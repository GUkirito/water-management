package com.example.watermanagement.service;

import com.example.watermanagement.dto.AccountingRepairExecuteRequest;
import com.example.watermanagement.dto.AccountingHealthIssue;
import com.example.watermanagement.dto.AccountingRepairPreview;
import com.example.watermanagement.dto.AccountingRepairPreviewRequest;
import com.example.watermanagement.dto.AccountingRepairResult;
import com.example.watermanagement.entity.AccountingRepairAudit;
import com.example.watermanagement.entity.Household;
import com.example.watermanagement.entity.Payment;
import com.example.watermanagement.entity.PrepaymentLog;
import com.example.watermanagement.entity.WaterBill;
import com.example.watermanagement.exception.BusinessException;
import com.example.watermanagement.repository.AccountingRepairAuditRepository;
import com.example.watermanagement.repository.HouseholdRepository;
import com.example.watermanagement.repository.PaymentRepository;
import com.example.watermanagement.repository.PrepaymentLogRepository;
import com.example.watermanagement.repository.WaterBillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class AccountingRepairServiceTests {

    @Autowired private AccountingRepairService accountingRepairService;
    @Autowired private HouseholdRepository householdRepository;
    @Autowired private WaterBillRepository waterBillRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private ObjectMapper objectMapper;
    @MockitoSpyBean private PrepaymentLogRepository prepaymentLogRepository;
    @MockitoSpyBean private AccountingHealthService accountingHealthService;
    @MockitoSpyBean private AccountingRepairAuditRepository auditRepository;
    @MockitoBean private DatabaseSnapshotService databaseSnapshotService;

    @BeforeEach
    void cleanDatabase() {
        reset(databaseSnapshotService, auditRepository, prepaymentLogRepository, accountingHealthService);
        auditRepository.deleteAll();
        prepaymentLogRepository.deleteAll();
        paymentRepository.deleteAll();
        waterBillRepository.deleteAll();
        householdRepository.deleteAll();
        when(databaseSnapshotService.createVerifiedSnapshot("accounting-repair"))
                .thenReturn(Path.of("target", "backup-temp", "accounting-repair-test.db"));
    }

    @Test
    void previewIsReadOnly() {
        CaseData data = createCrossMeterCase();

        AccountingRepairPreview preview = accountingRepairService.preview(request(data.wrongBill().getId()));

        assertThat(preview.isRepairable()).isTrue();
        assertThat(preview.isSnapshotRequired()).isTrue();
        assertThat(prepaymentLogRepository.findById(data.log().getId()).orElseThrow().getBillId())
                .isEqualTo(data.wrongBill().getId());
        assertThat(waterBillRepository.findById(data.correctBill().getId()).orElseThrow().getActualWaterPaid())
                .isEqualByComparingTo("0.00");
        assertThat(auditRepository.count()).isZero();
    }

    @Test
    void previewsExactCrossMeterRepairWithoutAcceptingAmountsOrTargetBillId() {
        CaseData data = createCrossMeterCase();

        AccountingRepairPreview preview = accountingRepairService.preview(request(data.wrongBill().getId()));

        assertThat(preview.isRepairable()).isTrue();
        assertThat(preview.getCause()).contains("预存抵扣流水", "其他住户");
        assertThat(preview.getAffectedRecords()).extracting("recordType", "recordId")
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("prepayment_log", data.log().getId()),
                        org.assertj.core.groups.Tuple.tuple("water_bill", data.wrongBill().getId()),
                        org.assertj.core.groups.Tuple.tuple("water_bill", data.correctBill().getId()));
        assertBillState(preview.getAfter(), data.wrongBill().getId(), "14.40", "已收", "0.00");
        assertBillState(preview.getAfter(), data.correctBill().getId(), "1.40", "部分收", "4.00");
    }

    @ParameterizedTest(name = "非法付款金额 {0} 不可自动修复")
    @MethodSource("invalidPaymentAmounts")
    void invalidPaymentAmountIsNotRepairable(String label, BigDecimal amount) {
        CaseData data = createCrossMeterCase();
        Payment payment = paymentRepository.findById(data.payment().getId()).orElseThrow();
        payment.setAmount(amount);
        paymentRepository.saveAndFlush(payment);

        AccountingRepairPreview preview = accountingRepairService.preview(request(data.wrongBill().getId()));

        assertThat(preview.isRepairable()).isFalse();
        assertThat(preview.getCause()).contains("付款流水", "人工核对");
        assertThat(auditRepository.count()).isZero();
    }

    @ParameterizedTest(name = "非法预存抵扣 {0} 不可自动修复")
    @MethodSource("invalidPrepaymentLogs")
    void invalidLinkedPrepaymentIsNotRepairable(String label, String type, BigDecimal amount) {
        CaseData data = createCrossMeterCase();
        prepaymentLogRepository.saveAndFlush(PrepaymentLog.builder()
                .waterMeterId(data.wrongBill().getWaterMeterId())
                .billId(data.wrongBill().getId())
                .type(type)
                .amount(amount)
                .build());

        AccountingRepairPreview preview = accountingRepairService.preview(request(data.wrongBill().getId()));

        assertThat(preview.isRepairable()).isFalse();
        assertThat(preview.getCause()).contains("预存流水", "人工核对");
        assertThat(auditRepository.count()).isZero();
    }

    @Test
    void nullLinkedPrepaymentAmountIsNotRepairable() {
        CaseData data = createCrossMeterCase();
        List<PrepaymentLog> logs = new ArrayList<>(prepaymentLogRepository.findByBillId(data.wrongBill().getId()));
        logs.add(PrepaymentLog.builder()
                .id(99999L)
                .waterMeterId(data.wrongBill().getWaterMeterId())
                .billId(data.wrongBill().getId())
                .type("AUTO_DEDUCT")
                .amount(null)
                .build());
        doReturn(logs).when(prepaymentLogRepository).findByBillId(data.wrongBill().getId());

        AccountingRepairPreview preview = accountingRepairService.preview(request(data.wrongBill().getId()));

        assertThat(preview.isRepairable()).isFalse();
        assertThat(preview.getCause()).contains("预存流水", "人工核对");
    }

    @ParameterizedTest(name = "非法应收金额 {0} 不可自动修复")
    @MethodSource("invalidCharges")
    void nullOrNegativeChargeIsNotRepairable(String label, BigDecimal charge) {
        CaseData data = createCrossMeterCase();
        WaterBill correct = waterBillRepository.findById(data.correctBill().getId()).orElseThrow();
        correct.setWaterCharge(charge);
        waterBillRepository.saveAndFlush(correct);

        AccountingRepairPreview preview = accountingRepairService.preview(request(data.wrongBill().getId()));

        assertThat(preview.isRepairable()).isFalse();
        assertThat(preview.getCause()).contains("应收金额", "人工核对");
    }

    @Test
    void effectivePaidGreaterThanChargeIsNotRepairable() {
        CaseData data = createCrossMeterCase();
        WaterBill correct = waterBillRepository.findById(data.correctBill().getId()).orElseThrow();
        correct.setWaterCharge(new BigDecimal("1.00"));
        waterBillRepository.saveAndFlush(correct);

        AccountingRepairPreview preview = accountingRepairService.preview(request(data.wrongBill().getId()));

        assertThat(preview.isRepairable()).isFalse();
        assertThat(preview.getCause()).contains("超过应收金额", "人工核对");
    }

    @Test
    void missingUniqueDestinationIsNotRepairable() {
        CaseData data = createCrossMeterCase();
        waterBillRepository.delete(data.correctBill());

        AccountingRepairPreview preview = accountingRepairService.preview(request(data.wrongBill().getId()));

        assertThat(preview.isRepairable()).isFalse();
        assertThat(preview.getCause()).contains("无法唯一确定");
        assertThat(auditRepository.count()).isZero();
    }

    @Test
    void multipleDestinationBillsAreNotRepairable() {
        CaseData data = createCrossMeterCase();
        waterBillRepository.save(bill("210500692", "5.40", "0.00", "未收"));

        AccountingRepairPreview preview = accountingRepairService.preview(request(data.wrongBill().getId()));

        assertThat(preview.isRepairable()).isFalse();
        assertThat(preview.getCause()).contains("无法唯一确定");
        assertThat(auditRepository.count()).isZero();
    }

    @Test
    void inconsistentStatusCanBeRepairedFromValidPaymentRecords() {
        saveHousehold("210503109");
        WaterBill bill = waterBillRepository.save(bill("210503109", "14.40", "14.40", "未收"));
        paymentRepository.save(Payment.builder()
                .billType("water")
                .billId(bill.getId())
                .amount(new BigDecimal("14.40"))
                .paidDate(LocalDate.of(2026, 7, 4))
                .build());
        AccountingRepairPreviewRequest previewRequest = new AccountingRepairPreviewRequest(
                "INCONSISTENT_WATER_BILL_STATUS", "water_bill", bill.getId());

        AccountingRepairPreview preview = accountingRepairService.preview(previewRequest);

        assertThat(preview.isRepairable()).isTrue();
        assertBillState(preview.getAfter(), bill.getId(), "14.40", "已收", "0.00");

        AccountingRepairResult result = accountingRepairService.execute(new AccountingRepairExecuteRequest(
                "INCONSISTENT_WATER_BILL_STATUS", "water_bill", bill.getId(),
                "测试员", "修复错误收款状态"));

        WaterBill repaired = waterBillRepository.findById(bill.getId()).orElseThrow();
        assertThat(repaired.getActualWaterPaid()).isEqualByComparingTo("14.40");
        assertThat(repaired.getWaterStatus()).isEqualTo("已收");
        assertThat(result.getAuditId()).isNotNull();
    }

    @Test
    void snapshotFailureCausesNoWrites() {
        CaseData data = createCrossMeterCase();
        when(databaseSnapshotService.createVerifiedSnapshot("accounting-repair"))
                .thenThrow(new BusinessException("创建数据库一致性备份失败"));

        assertThatThrownBy(() -> accountingRepairService.execute(executeRequest(data.wrongBill().getId())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("备份失败");

        assertUnchanged(data);
        assertThat(auditRepository.count()).isZero();
    }

    @Test
    void executeRebindsLogRecalculatesBillsAuditsAndRechecks() {
        CaseData data = createCrossMeterCase();
        long paymentCount = paymentRepository.count();
        long prepaymentCount = prepaymentLogRepository.count();

        AccountingRepairResult result = accountingRepairService.execute(executeRequest(data.wrongBill().getId()));

        PrepaymentLog moved = prepaymentLogRepository.findById(data.log().getId()).orElseThrow();
        assertThat(moved.getBillId()).isEqualTo(data.correctBill().getId());
        assertThat(moved.getWaterMeterId()).isEqualTo("210500692");
        assertThat(moved.getType()).isEqualTo("AUTO_DEDUCT");
        assertThat(moved.getAmount()).isEqualByComparingTo("-1.40");
        WaterBill wrong = waterBillRepository.findById(data.wrongBill().getId()).orElseThrow();
        WaterBill correct = waterBillRepository.findById(data.correctBill().getId()).orElseThrow();
        assertThat(wrong.getActualWaterPaid()).isEqualByComparingTo("14.40");
        assertThat(wrong.getWaterStatus()).isEqualTo("已收");
        assertThat(correct.getActualWaterPaid()).isEqualByComparingTo("1.40");
        assertThat(correct.getWaterStatus()).isEqualTo("部分收");
        assertBillState(result.getAfter(), wrong.getId(), "14.40", "已收", "0.00");
        assertBillState(result.getAfter(), correct.getId(), "1.40", "部分收", "4.00");
        assertThat(result.getSnapshotName()).isEqualTo("accounting-repair-test.db");
        assertThat(result.getAuditId()).isNotNull();
        AccountingRepairAudit audit = auditRepository.findById(result.getAuditId()).orElseThrow();
        assertAuditStructure(audit, data);
        assertThat(result.getRemainingIssues()).noneMatch(issue ->
                "PAYMENT_TOTAL_MISMATCH".equals(issue.getType())
                        && data.wrongBill().getId().equals(issue.getRefId()));
        assertThat(paymentRepository.count()).isEqualTo(paymentCount);
        assertThat(prepaymentLogRepository.count()).isEqualTo(prepaymentCount);
    }

    @Test
    void multipleLegalWrongBindingsToSameBillAreCombined() {
        CaseData data = createCrossMeterCase();
        PrepaymentLog second = prepaymentLogRepository.save(PrepaymentLog.builder()
                .waterMeterId("210500692")
                .amount(new BigDecimal("-0.60"))
                .type("AUTO_DEDUCT")
                .billId(data.wrongBill().getId())
                .build());

        AccountingRepairResult result = accountingRepairService.execute(executeRequest(data.wrongBill().getId()));

        assertThat(prepaymentLogRepository.findById(data.log().getId()).orElseThrow().getBillId())
                .isEqualTo(data.correctBill().getId());
        assertThat(prepaymentLogRepository.findById(second.getId()).orElseThrow().getBillId())
                .isEqualTo(data.correctBill().getId());
        WaterBill correct = waterBillRepository.findById(data.correctBill().getId()).orElseThrow();
        assertThat(correct.getActualWaterPaid()).isEqualByComparingTo("2.00");
        assertThat(correct.getWaterStatus()).isEqualTo("部分收");
        assertBillState(result.getAfter(), correct.getId(), "2.00", "部分收", "3.40");
    }

    @Test
    void executeRejectsWhenPreviewedProblemHasChanged() {
        CaseData data = createCrossMeterCase();
        assertThat(accountingRepairService.preview(request(data.wrongBill().getId())).isRepairable()).isTrue();
        prepaymentLogRepository.delete(data.log());

        assertThatThrownBy(() -> accountingRepairService.execute(executeRequest(data.wrongBill().getId())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数据已变化");

        assertThat(auditRepository.count()).isZero();
    }

    @Test
    void auditFailureRollsBackAccountingWrites() {
        CaseData data = createCrossMeterCase();
        doThrow(new RuntimeException("audit failed")).when(auditRepository)
                .saveAndFlush(any(AccountingRepairAudit.class));

        assertThatThrownBy(() -> accountingRepairService.execute(executeRequest(data.wrongBill().getId())))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("audit failed");

        assertUnchanged(data);
    }

    @Test
    void healthCheckFailureRollsBackAccountingAndAudit() {
        CaseData data = createCrossMeterCase();
        doThrow(new RuntimeException("health check failed")).when(accountingHealthService).check();

        assertThatThrownBy(() -> accountingRepairService.execute(executeRequest(data.wrongBill().getId())))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("health check failed");

        assertUnchanged(data);
    }

    @Test
    void targetIssueRemainingAfterRepairRollsBackEverything() {
        CaseData data = createCrossMeterCase();
        doReturn(List.of(new AccountingHealthIssue(
                "PAYMENT_TOTAL_MISMATCH", "ERROR", "water_bill", data.wrongBill().getId(),
                data.wrongBill().getWaterMeterId(), "still mismatched")))
                .when(accountingHealthService).check();

        assertThatThrownBy(() -> accountingRepairService.execute(executeRequest(data.wrongBill().getId())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("复检未通过");

        assertUnchanged(data);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PAYMENT_TOTAL_MISMATCH", "INCONSISTENT_WATER_BILL_STATUS"})
    void affectedDestinationBillIssueRemainingAfterRepairRollsBackEverything(String issueType) {
        CaseData data = createCrossMeterCase();
        doReturn(List.of(new AccountingHealthIssue(
                issueType, "ERROR", "water_bill", data.correctBill().getId(),
                data.correctBill().getWaterMeterId(), "still mismatched")))
                .when(accountingHealthService).check();

        assertThatThrownBy(() -> accountingRepairService.execute(executeRequest(data.wrongBill().getId())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("复检未通过");

        assertUnchanged(data);
    }

    @Test
    void unrelatedHealthIssuesRemainWithoutRollingBackRepair() {
        CaseData data = createCrossMeterCase();
        List<AccountingHealthIssue> unrelatedIssues = List.of(
                new AccountingHealthIssue(
                        "ORPHAN_WATER_BILL", "ERROR", "water_bill", data.correctBill().getId(),
                        data.correctBill().getWaterMeterId(), "unrelated type"),
                new AccountingHealthIssue(
                        "PAYMENT_TOTAL_MISMATCH", "ERROR", "water_bill", 99999L,
                        "OTHER-METER", "unrelated bill"));
        doReturn(unrelatedIssues).when(accountingHealthService).check();

        AccountingRepairResult result = accountingRepairService.execute(
                executeRequest(data.wrongBill().getId()));

        assertThat(result.getRemainingIssues()).containsExactlyElementsOf(unrelatedIssues);
        assertThat(prepaymentLogRepository.findById(data.log().getId()).orElseThrow().getBillId())
                .isEqualTo(data.correctBill().getId());
        assertThat(auditRepository.count()).isEqualTo(1);
    }

    @Test
    void logAmountChangedAfterPlanIsRejectedBeforeRepairWrites() {
        CaseData data = createCrossMeterCase();
        when(databaseSnapshotService.createVerifiedSnapshot("accounting-repair")).thenAnswer(ignored -> {
            PrepaymentLog changed = prepaymentLogRepository.findById(data.log().getId()).orElseThrow();
            changed.setAmount(new BigDecimal("-2.00"));
            prepaymentLogRepository.saveAndFlush(changed);
            return Path.of("target", "backup-temp", "accounting-repair-test.db");
        });

        assertThatThrownBy(() -> accountingRepairService.execute(executeRequest(data.wrongBill().getId())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数据已变化");

        assertBillUnchanged(data);
        assertThat(auditRepository.count()).isZero();
        assertThat(prepaymentLogRepository.findById(data.log().getId()).orElseThrow().getAmount())
                .isEqualByComparingTo("-2.00");
    }

    private CaseData createCrossMeterCase() {
        saveHousehold("210503109");
        saveHousehold("210500692");
        WaterBill wrong = waterBillRepository.save(bill("210503109", "14.40", "14.40", "已收"));
        WaterBill correct = waterBillRepository.save(bill("210500692", "5.40", "0.00", "未收"));
        Payment payment = paymentRepository.save(Payment.builder()
                .billType("water")
                .billId(wrong.getId())
                .amount(new BigDecimal("14.40"))
                .paidDate(LocalDate.of(2026, 7, 4))
                .build());
        PrepaymentLog log = prepaymentLogRepository.save(PrepaymentLog.builder()
                .waterMeterId("210500692")
                .amount(new BigDecimal("-1.40"))
                .type("AUTO_DEDUCT")
                .billId(wrong.getId())
                .build());
        return new CaseData(wrong, correct, log, payment);
    }

    private void saveHousehold(String meterId) {
        householdRepository.save(Household.builder()
                .householdName(meterId)
                .villageName("测试村")
                .waterMeterId(meterId)
                .isActive(true)
                .build());
    }

    private WaterBill bill(String meterId, String charge, String paid, String status) {
        return WaterBill.builder()
                .waterMeterId(meterId)
                .billYear(2026)
                .billMonth(7)
                .waterAmount(BigDecimal.ONE)
                .waterCharge(new BigDecimal(charge))
                .actualWaterPaid(new BigDecimal(paid))
                .waterStatus(status)
                .build();
    }

    private AccountingRepairPreviewRequest request(Long billId) {
        return new AccountingRepairPreviewRequest("PAYMENT_TOTAL_MISMATCH", "water_bill", billId);
    }

    private AccountingRepairExecuteRequest executeRequest(Long billId) {
        return new AccountingRepairExecuteRequest(
                "PAYMENT_TOTAL_MISMATCH", "water_bill", billId, "测试员", "修复错绑预存流水");
    }

    private void assertUnchanged(CaseData data) {
        assertBillUnchanged(data);
        PrepaymentLog log = prepaymentLogRepository.findById(data.log().getId()).orElseThrow();
        assertThat(log.getBillId()).isEqualTo(data.wrongBill().getId());
        assertThat(log.getWaterMeterId()).isEqualTo(data.log().getWaterMeterId());
        assertThat(log.getType()).isEqualTo(data.log().getType());
        assertThat(log.getAmount()).isEqualByComparingTo(data.log().getAmount());
        Payment payment = paymentRepository.findById(data.payment().getId()).orElseThrow();
        assertThat(payment.getBillType()).isEqualTo("water");
        assertThat(payment.getBillId()).isEqualTo(data.wrongBill().getId());
        assertThat(payment.getAmount()).isEqualByComparingTo("14.40");
        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(prepaymentLogRepository.count()).isEqualTo(1);
        assertThat(auditRepository.count()).isZero();
    }

    private void assertBillUnchanged(CaseData data) {
        WaterBill wrong = waterBillRepository.findById(data.wrongBill().getId()).orElseThrow();
        WaterBill correct = waterBillRepository.findById(data.correctBill().getId()).orElseThrow();
        assertThat(wrong.getActualWaterPaid()).isEqualByComparingTo("14.40");
        assertThat(wrong.getWaterStatus()).isEqualTo("已收");
        assertThat(correct.getActualWaterPaid()).isEqualByComparingTo("0.00");
        assertThat(correct.getWaterStatus()).isEqualTo("未收");
    }

    @SuppressWarnings("unchecked")
    private void assertBillState(Map<String, Object> state, Long billId,
                                 String actual, String status, String remaining) {
        Map<String, Object> bill = ((List<Map<String, Object>>) state.get("bills")).stream()
                .filter(item -> new BigDecimal(item.get("id").toString()).longValue() == billId)
                .findFirst().orElseThrow();
        assertThat(new BigDecimal(bill.get("actual").toString())).isEqualByComparingTo(actual);
        assertThat(bill.get("status")).isEqualTo(status);
        assertThat(new BigDecimal(bill.get("remainingDue").toString())).isEqualByComparingTo(remaining);
    }

    @SuppressWarnings("unchecked")
    private void assertAuditStructure(AccountingRepairAudit audit, CaseData data) {
        Map<String, Object> before = objectMapper.readValue(audit.getBeforeJson(), Map.class);
        Map<String, Object> after = objectMapper.readValue(audit.getAfterJson(), Map.class);
        Map<String, Object> wrongBefore = findRecord((List<Map<String, Object>>) before.get("bills"),
                data.wrongBill().getId());
        assertThat(wrongBefore).containsKeys(
                "id", "meter", "year", "month", "charge", "actual", "status", "version");
        Map<String, Object> payment = findRecord((List<Map<String, Object>>) before.get("payments"),
                data.payment().getId());
        assertThat(payment).containsEntry("billId", data.wrongBill().getId().intValue())
                .containsKeys("id", "amount");
        Map<String, Object> deduction = findRecord(
                (List<Map<String, Object>>) before.get("prepaymentLogs"), data.log().getId());
        assertThat(deduction).containsKeys("id", "billId", "meter", "type", "amount");
        Map<String, Object> moved = findRecord((List<Map<String, Object>>) after.get("movedLogs"),
                data.log().getId());
        assertThat(moved).containsKeys("id", "fromBillId", "toBillId", "meter", "type", "amount");
        assertThat(new BigDecimal(moved.get("amount").toString())).isEqualByComparingTo("-1.40");
    }

    private Map<String, Object> findRecord(List<Map<String, Object>> records, Long id) {
        return records.stream()
                .filter(item -> new BigDecimal(item.get("id").toString()).longValue() == id)
                .findFirst().orElseThrow();
    }

    private static Stream<Arguments> invalidPaymentAmounts() {
        return Stream.of(
                Arguments.of("null", null),
                Arguments.of("zero", BigDecimal.ZERO),
                Arguments.of("negative", new BigDecimal("-1.00")));
    }

    private static Stream<Arguments> invalidPrepaymentLogs() {
        return Stream.of(
                Arguments.of("unknown type", "MANUAL_DEDUCT", new BigDecimal("-0.50")),
                Arguments.of("zero", "AUTO_DEDUCT", BigDecimal.ZERO),
                Arguments.of("positive", "AUTO_DEDUCT", new BigDecimal("0.50")));
    }

    private static Stream<Arguments> invalidCharges() {
        return Stream.of(
                Arguments.of("null", null),
                Arguments.of("negative", new BigDecimal("-1.00")));
    }

    private record CaseData(WaterBill wrongBill, WaterBill correctBill, PrepaymentLog log, Payment payment) {
    }
}
