package com.example.watermanagement.service;

import com.example.watermanagement.dto.PaymentRequest;
import com.example.watermanagement.dto.PendingWaterBillRow;
import com.example.watermanagement.dto.MaterialRecordRequest;
import com.example.watermanagement.dto.HouseholdRequest;
import com.example.watermanagement.dto.ConfigUpdateDTO;
import com.example.watermanagement.controller.HouseholdController;
import com.example.watermanagement.controller.MaterialRecordController;
import com.example.watermanagement.entity.Household;
import com.example.watermanagement.entity.MaterialPayment;
import com.example.watermanagement.entity.MaterialRecord;
import com.example.watermanagement.entity.PrepaymentLog;
import com.example.watermanagement.entity.Reading;
import com.example.watermanagement.entity.WaterBill;
import com.example.watermanagement.entity.Payment;
import com.example.watermanagement.exception.BusinessException;
import com.example.watermanagement.repository.HouseholdRepository;
import com.example.watermanagement.repository.MaterialPaymentRepository;
import com.example.watermanagement.repository.MaterialRecordRepository;
import com.example.watermanagement.repository.MonthLockRepository;
import com.example.watermanagement.repository.PaymentRepository;
import com.example.watermanagement.repository.PrepaymentLogRepository;
import com.example.watermanagement.repository.ReadingRepository;
import com.example.watermanagement.repository.WaterBillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.validation.Validator;
import jakarta.persistence.Version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class BillingValidationTests {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private MaterialRecordService materialRecordService;

    @Autowired
    private HouseholdService householdService;

    @Autowired
    private ReadingService readingService;

    @Autowired
    private HouseholdController householdController;

    @Autowired
    private MaterialRecordController materialRecordController;

    @Autowired
    private WaterBillRepository waterBillRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private MaterialRecordRepository materialRecordRepository;

    @Autowired
    private MaterialPaymentRepository materialPaymentRepository;

    @Autowired
    private PrepaymentLogRepository prepaymentLogRepository;

    @Autowired
    private ReadingRepository readingRepository;

    @Autowired
    private MonthLockRepository monthLockRepository;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private Validator validator;

    @BeforeEach
    void cleanDatabase() {
        monthLockRepository.deleteAll();
        prepaymentLogRepository.deleteAll();
        paymentRepository.deleteAll();
        readingRepository.deleteAll();
        waterBillRepository.deleteAll();
        materialPaymentRepository.deleteAll();
        materialRecordRepository.deleteAll();
        householdRepository.deleteAll();
    }

    @Test
    void waterPaymentRejectsNonPositiveAmount() {
        WaterBill bill = waterBillRepository.save(WaterBill.builder()
                .waterMeterId("WM-001")
                .billYear(2026)
                .billMonth(6)
                .waterAmount(new BigDecimal("10.00"))
                .waterCharge(new BigDecimal("18.00"))
                .actualWaterPaid(BigDecimal.ZERO)
                .waterStatus("未收")
                .build());

        PaymentRequest request = waterPayment(List.of(bill.getId()), new BigDecimal("-1.00"));

        assertThatThrownBy(() -> paymentService.pay(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缴费金额必须大于 0");
        assertThat(paymentRepository.count()).isZero();
    }

    @Test
    void waterPaymentStoresOverpaymentAsPrepayment() {
        WaterBill bill = waterBillRepository.save(WaterBill.builder()
                .waterMeterId("WM-002")
                .billYear(2026)
                .billMonth(6)
                .waterAmount(new BigDecimal("10.00"))
                .waterCharge(new BigDecimal("18.00"))
                .actualWaterPaid(BigDecimal.ZERO)
                .waterStatus("未收")
                .build());

        PaymentRequest request = waterPayment(List.of(bill.getId()), new BigDecimal("20.00"));

        paymentService.pay(request);

        assertThat(paymentRepository.count()).isEqualTo(1);
        WaterBill reloaded = waterBillRepository.findById(bill.getId()).orElseThrow();
        assertThat(reloaded.getActualWaterPaid()).isEqualByComparingTo(new BigDecimal("18.00"));
        assertThat(reloaded.getWaterStatus()).isEqualTo("已收");
        assertThat(prepaymentLogRepository.getBalance("WM-002"))
                .isEqualByComparingTo(new BigDecimal("2.00"));
    }

    @Test
    void waterPaymentRejectsZeroDueBillsEvenIfStatusIsStale() {
        WaterBill bill = waterBillRepository.save(WaterBill.builder()
                .waterMeterId("WM-ZERO-DUE")
                .billYear(2026)
                .billMonth(6)
                .waterAmount(new BigDecimal("10.00"))
                .waterCharge(new BigDecimal("18.00"))
                .actualWaterPaid(new BigDecimal("18.00"))
                .waterStatus("未收")
                .build());

        PaymentRequest request = waterPayment(List.of(bill.getId()), new BigDecimal("1.00"));

        assertThatThrownBy(() -> paymentService.pay(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("均已缴清");
        assertThat(paymentRepository.count()).isZero();
        assertThat(prepaymentLogRepository.getBalance("WM-ZERO-DUE")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void waterPaymentRejectsNoPaymentRequiredBill() {
        WaterBill bill = waterBillRepository.save(WaterBill.builder()
                .waterMeterId("WM-NO-PAYMENT")
                .billYear(2026)
                .billMonth(7)
                .waterAmount(BigDecimal.ZERO)
                .waterCharge(new BigDecimal("0.00"))
                .actualWaterPaid(new BigDecimal("0.00"))
                .waterStatus("无需缴费")
                .build());
        prepaymentLogRepository.save(PrepaymentLog.builder()
                .waterMeterId("WM-NO-PAYMENT")
                .amount(new BigDecimal("8.00"))
                .type("OVERPAYMENT")
                .build());

        PaymentRequest request = waterPayment(List.of(bill.getId()), new BigDecimal("1.00"));

        assertThatThrownBy(() -> paymentService.pay(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无需缴费");
        assertThat(paymentRepository.count()).isZero();
        WaterBill unchanged = waterBillRepository.findById(bill.getId()).orElseThrow();
        assertThat(unchanged.getActualWaterPaid()).isEqualByComparingTo("0.00");
        assertThat(unchanged.getWaterStatus()).isEqualTo("无需缴费");
        assertThat(prepaymentLogRepository.findByWaterMeterIdOrderByCreatedAtDesc("WM-NO-PAYMENT"))
                .extracting(PrepaymentLog::getType)
                .containsExactly("OVERPAYMENT");
        assertThat(prepaymentLogRepository.getBalance("WM-NO-PAYMENT"))
                .isEqualByComparingTo("8.00");
    }

    @Test
    void meterPendingBillsExcludeNoPaymentRequiredAndKeepPositiveDueBills() {
        saveWaterBill("WM-PENDING", 5, "0.00", "0.00", "无需缴费");
        WaterBill unpaid = saveWaterBill("WM-PENDING", 6, "18.00", "0.00", "未收");
        WaterBill partial = saveWaterBill("WM-PENDING", 7, "18.00", "8.00", "部分收");

        List<WaterBill> bills = paymentService.getPendingWaterBills("WM-PENDING");

        assertThat(bills).extracting(WaterBill::getId)
                .containsExactlyInAnyOrder(unpaid.getId(), partial.getId());
    }

    @Test
    void pendingBillListExcludeNoPaymentRequiredAndKeepPositiveDueBills() {
        householdRepository.save(Household.builder()
                .householdName("待缴测试户")
                .waterMeterId("WM-PENDING-LIST")
                .villageName("测试村")
                .phone("")
                .isActive(true)
                .build());
        saveWaterBill("WM-PENDING-LIST", 5, "0.00", "0.00", "无需缴费");
        WaterBill unpaid = saveWaterBill("WM-PENDING-LIST", 6, "18.00", "0.00", "未收");
        WaterBill partial = saveWaterBill("WM-PENDING-LIST", 7, "18.00", "8.00", "部分收");

        List<PendingWaterBillRow> rows = paymentService.listPendingWaterBills(
                "测试村", null, 2026, null);

        assertThat(rows).extracting(PendingWaterBillRow::getId)
                .containsExactlyInAnyOrder(unpaid.getId(), partial.getId());
    }

    @Test
    void materialCollectRejectsNonPositiveAmount() {
        MaterialRecord record = materialRecordRepository.save(MaterialRecord.builder()
                .householdName("Test User")
                .waterMeterId("WM-MAT-001")
                .villageName("Test Village")
                .totalFee(new BigDecimal("1500.00"))
                .actualPaid(BigDecimal.ZERO)
                .status("未收")
                .build());

        assertThatThrownBy(() -> materialRecordService.collect(
                record.getId(),
                new BigDecimal("-100.00"),
                LocalDate.of(2026, 6, 20),
                "tester",
                null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("收费金额必须大于 0");
        assertThat(materialPaymentRepository.count()).isZero();
    }

    @Test
    void batchUpdateVillageRejectsMissingBodyFields() {
        var response = householdController.batchUpdateVillage(Map.of());

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getMessage()).contains("ids", "villageName");
    }

    @Test
    void materialCollectRejectsMissingBodyFields() {
        MaterialRecord record = materialRecordRepository.save(MaterialRecord.builder()
                .householdName("Test User")
                .waterMeterId("WM-MAT-MISSING")
                .villageName("Test Village")
                .totalFee(new BigDecimal("1500.00"))
                .actualPaid(BigDecimal.ZERO)
                .status("未收")
                .build());

        var response = materialRecordController.collect(record.getId(), Map.of());

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getMessage()).contains("amount", "paidDate");
    }

    @Test
    void pendingWaterBillListCanFilterByVillageKeywordAndMonth() {
        householdRepository.save(Household.builder()
                .householdName("张三")
                .waterMeterId("WM-LIST-001")
                .villageName("一组")
                .phone("")
                .isActive(true)
                .build());
        householdRepository.save(Household.builder()
                .householdName("李四")
                .waterMeterId("WM-LIST-002")
                .villageName("二组")
                .phone("")
                .isActive(true)
                .build());
        waterBillRepository.save(WaterBill.builder()
                .waterMeterId("WM-LIST-001")
                .billYear(2026)
                .billMonth(6)
                .waterAmount(new BigDecimal("8.00"))
                .waterCharge(new BigDecimal("14.40"))
                .actualWaterPaid(new BigDecimal("4.40"))
                .waterStatus("部分收")
                .build());
        waterBillRepository.save(WaterBill.builder()
                .waterMeterId("WM-LIST-002")
                .billYear(2026)
                .billMonth(6)
                .waterAmount(new BigDecimal("9.00"))
                .waterCharge(new BigDecimal("16.20"))
                .actualWaterPaid(BigDecimal.ZERO)
                .waterStatus("未收")
                .build());
        waterBillRepository.save(WaterBill.builder()
                .waterMeterId("WM-LIST-001")
                .billYear(2026)
                .billMonth(5)
                .waterAmount(new BigDecimal("7.00"))
                .waterCharge(new BigDecimal("12.60"))
                .actualWaterPaid(new BigDecimal("12.60"))
                .waterStatus("已收")
                .build());

        List<PendingWaterBillRow> rows = paymentService.listPendingWaterBills("一组", "张", 2026, 6);

        assertThat(rows).hasSize(1);
        PendingWaterBillRow row = rows.get(0);
        assertThat(row.getWaterMeterId()).isEqualTo("WM-LIST-001");
        assertThat(row.getHouseholdName()).isEqualTo("张三");
        assertThat(row.getVillageName()).isEqualTo("一组");
        assertThat(row.getDueAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void householdListKeywordMatchesNameMeterAndVillage() {
        householdRepository.save(Household.builder()
                .householdName("张三")
                .waterMeterId("WM-SEARCH-001")
                .villageName("东村")
                .phone("")
                .isActive(true)
                .build());
        householdRepository.save(Household.builder()
                .householdName("李四")
                .waterMeterId("WM-SEARCH-002")
                .villageName("西村")
                .phone("")
                .isActive(true)
                .build());

        var byName = householdService.list(null, "张", org.springframework.data.domain.PageRequest.of(0, 8));
        var byVillage = householdService.list(null, "西村", org.springframework.data.domain.PageRequest.of(0, 8));

        assertThat(byName.getContent()).extracting(Household::getWaterMeterId).containsExactly("WM-SEARCH-001");
        assertThat(byVillage.getContent()).extracting(Household::getWaterMeterId).containsExactly("WM-SEARCH-002");
    }

    @Test
    void waterBillHasOptimisticLockVersionField() throws NoSuchFieldException {
        Field version = WaterBill.class.getDeclaredField("version");

        assertThat(version.getAnnotation(Version.class)).isNotNull();
    }

    @Test
    void configUpdateRequiresBothFields() {
        ConfigUpdateDTO dto = new ConfigUpdateDTO();

        assertThat(validator.validate(dto))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("waterPrice", "abnormalThreshold");
    }

    @Test
    void materialRecordUpdateCanChangeWaterMeterId() {
        MaterialRecord record = materialRecordRepository.save(MaterialRecord.builder()
                .householdName("材料户")
                .waterMeterId("WM-MAT-OLD")
                .villageName("测试村")
                .totalFee(new BigDecimal("1500.00"))
                .actualPaid(BigDecimal.ZERO)
                .status("未收")
                .build());
        MaterialRecordRequest request = new MaterialRecordRequest();
        request.setHouseholdName("材料户");
        request.setWaterMeterId("WM-MAT-NEW");
        request.setVillageName("测试村");

        materialRecordService.update(record.getId(), request);

        MaterialRecord reloaded = materialRecordRepository.findById(record.getId()).orElseThrow();
        assertThat(reloaded.getWaterMeterId()).isEqualTo("WM-MAT-NEW");
    }

    @Test
    void materialRecordUpdateRejectsDuplicateWaterMeterId() {
        MaterialRecord first = materialRecordRepository.save(MaterialRecord.builder()
                .householdName("材料户一")
                .waterMeterId("WM-MAT-A")
                .villageName("测试村")
                .totalFee(new BigDecimal("1500.00"))
                .actualPaid(BigDecimal.ZERO)
                .status("未收")
                .build());
        materialRecordRepository.save(MaterialRecord.builder()
                .householdName("材料户二")
                .waterMeterId("WM-MAT-B")
                .villageName("测试村")
                .totalFee(new BigDecimal("1500.00"))
                .actualPaid(BigDecimal.ZERO)
                .status("未收")
                .build());
        MaterialRecordRequest request = new MaterialRecordRequest();
        request.setWaterMeterId("WM-MAT-B");

        assertThatThrownBy(() -> materialRecordService.update(first.getId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("水表编号已存在");
    }

    @Test
    void householdMaterialFeeTotalRequestIsIgnored() {
        HouseholdRequest addRequest = householdRequest("owner", "WM-MAT-BOUNDARY", "village");
        addRequest.setMaterialFeeTotal(new BigDecimal("9999.00"));

        Household added = householdService.add(addRequest);
        assertThat(added.getMaterialFeeTotal()).isEqualByComparingTo(new BigDecimal("1500.00"));

        HouseholdRequest updateRequest = householdRequest("owner", "WM-MAT-BOUNDARY", "village");
        updateRequest.setMaterialFeeTotal(new BigDecimal("8888.00"));
        householdService.update(added.getId(), updateRequest);

        Household reloaded = householdRepository.findById(added.getId()).orElseThrow();
        assertThat(reloaded.getMaterialFeeTotal()).isEqualByComparingTo(new BigDecimal("1500.00"));
    }

    @Test
    void deleteByVillageRequiresConfirmParameter() {
        householdRepository.save(Household.builder()
                .householdName("整村删除测试户")
                .waterMeterId("WM-DEL-VILLAGE")
                .villageName("危险村")
                .phone("")
                .isActive(true)
                .build());

        assertThatThrownBy(() -> householdController.deleteByVillage("危险村", false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("confirm=true");
        assertThat(householdRepository.findByIsActiveTrue()).hasSize(1);
    }

    @Test
    void deletingOneHouseholdRequiresConfirmParameter() {
        Household household = householdRepository.save(Household.builder()
                .householdName("owner")
                .waterMeterId("WM-DEL-CONFIRM")
                .villageName("village")
                .phone("")
                .isActive(true)
                .build());

        assertThatThrownBy(() -> householdController.delete(household.getId(), false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("confirm=true");
        assertThat(householdRepository.findById(household.getId())).isPresent();
    }

    @Test
    void batchDeleteRequestBodyAllowsBooleanConfirmation() throws Exception {
        var method = HouseholdController.class.getMethod("batchDelete", Map.class);
        var bodyType = (ParameterizedType) method.getGenericParameterTypes()[0];

        assertThat(bodyType.getActualTypeArguments()[1].getTypeName())
                .isEqualTo(Object.class.getTypeName());

        Household household = householdRepository.save(Household.builder()
                .householdName("owner")
                .waterMeterId("WM-BATCH-DELETE")
                .villageName("village")
                .phone("")
                .isActive(true)
                .build());
        householdController.batchDelete(Map.of(
                "ids", List.of(household.getId()),
                "confirm", true));

        assertThat(householdRepository.findById(household.getId())).isEmpty();
    }

    @Test
    void paymentHistoryAllowsNullPaidDate() {
        WaterBill bill = waterBillRepository.save(WaterBill.builder()
                .waterMeterId("WM-HISTORY-NULL-DATE")
                .billYear(2026)
                .billMonth(6)
                .waterAmount(new BigDecimal("10.00"))
                .waterCharge(new BigDecimal("18.00"))
                .actualWaterPaid(BigDecimal.ZERO)
                .waterStatus("open")
                .build());
        paymentRepository.save(Payment.builder()
                .billType("water")
                .billId(bill.getId())
                .amount(new BigDecimal("1.00"))
                .paidDate(null)
                .build());

        assertThat(paymentService.getHistory("WM-HISTORY-NULL-DATE")).hasSize(1);
    }

    @Test
    void batchSaveReportsMissingCurrentReadingWithoutWritingRows() {
        householdRepository.save(Household.builder()
                .householdName("owner")
                .waterMeterId("WM-BLANK-READING")
                .villageName("village")
                .phone("")
                .isActive(true)
                .build());

        var item = new com.example.watermanagement.dto.ReadingBatchItem();
        item.setWaterMeterId("WM-BLANK-READING");
        Map<String, Object> result = readingService.batchSave(List.of(item), LocalDate.of(2026, 7, 1));

        assertThat(result.get("total")).isEqualTo(0);
        assertThat((List<?>) result.get("errors")).hasSize(1);
        assertThat(readingRepository.count()).isZero();
    }

    @Test
    void householdMeterIdChangeMovesLinkedRows() {
        Household household = householdRepository.save(Household.builder()
                .householdName("owner")
                .waterMeterId("WM-OLD")
                .villageName("village")
                .phone("")
                .isActive(true)
                .build());
        readingRepository.save(Reading.builder()
                .waterMeterId("WM-OLD")
                .readingDate(LocalDate.of(2026, 6, 1))
                .currentReading(new BigDecimal("10.00"))
                .previousReading(BigDecimal.ZERO)
                .usageAmount(new BigDecimal("10.00"))
                .isAbnormal(false)
                .build());
        waterBillRepository.save(WaterBill.builder()
                .waterMeterId("WM-OLD")
                .billYear(2026)
                .billMonth(6)
                .waterAmount(new BigDecimal("10.00"))
                .waterCharge(new BigDecimal("18.00"))
                .actualWaterPaid(BigDecimal.ZERO)
                .waterStatus("open")
                .build());
        prepaymentLogRepository.save(PrepaymentLog.builder()
                .waterMeterId("WM-OLD")
                .amount(new BigDecimal("5.00"))
                .type("OVERPAYMENT")
                .build());
        materialRecordRepository.save(MaterialRecord.builder()
                .householdName("owner")
                .waterMeterId("WM-OLD")
                .villageName("village")
                .totalFee(new BigDecimal("1500.00"))
                .actualPaid(BigDecimal.ZERO)
                .status("open")
                .build());

        HouseholdRequest request = householdRequest("owner", "WM-NEW", "village");
        householdService.update(household.getId(), request);

        assertThat(readingRepository.findByWaterMeterIdInOrderByReadingDateDesc(List.of("WM-OLD"))).isEmpty();
        assertThat(readingRepository.findByWaterMeterIdInOrderByReadingDateDesc(List.of("WM-NEW"))).hasSize(1);
        assertThat(waterBillRepository.findByWaterMeterId("WM-OLD")).isEmpty();
        assertThat(waterBillRepository.findByWaterMeterId("WM-NEW")).hasSize(1);
        assertThat(prepaymentLogRepository.getBalance("WM-OLD")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(prepaymentLogRepository.getBalance("WM-NEW")).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(materialRecordRepository.findByWaterMeterId("WM-NEW")).isPresent();
    }

    @Test
    void deletingHouseholdWithPrepaymentArchivesAndKeepsLogs() {
        Household household = householdRepository.save(Household.builder()
                .householdName("owner")
                .waterMeterId("WM-DELETE-PREPAY")
                .villageName("village")
                .phone("")
                .isActive(true)
                .build());
        prepaymentLogRepository.save(PrepaymentLog.builder()
                .waterMeterId("WM-DELETE-PREPAY")
                .amount(new BigDecimal("8.00"))
                .type("OVERPAYMENT")
                .build());

        householdService.delete(household.getId());

        assertThat(prepaymentLogRepository.findByWaterMeterIdOrderByCreatedAtDesc("WM-DELETE-PREPAY")).hasSize(1);
        assertThat(householdRepository.findById(household.getId()).orElseThrow().getIsActive()).isFalse();
    }

    @Test
    void deletingHouseholdWithMaterialPaymentArchivesAndKeepsHistory() {
        Household household = householdRepository.save(Household.builder()
                .householdName("owner")
                .waterMeterId("WM-DELETE-MATERIAL")
                .villageName("village")
                .phone("")
                .isActive(true)
                .build());
        MaterialRecord record = materialRecordRepository.save(MaterialRecord.builder()
                .householdName("owner")
                .waterMeterId("WM-DELETE-MATERIAL")
                .villageName("village")
                .totalFee(new BigDecimal("1500.00"))
                .actualPaid(new BigDecimal("100.00"))
                .status("部分收")
                .build());
        materialPaymentRepository.save(MaterialPayment.builder()
                .recordId(record.getId())
                .amount(new BigDecimal("100.00"))
                .paidDate(LocalDate.of(2026, 7, 5))
                .collector("tester")
                .build());

        householdService.delete(household.getId());

        assertThat(materialRecordRepository.findByWaterMeterId("WM-DELETE-MATERIAL")).isPresent();
        assertThat(materialPaymentRepository.findByRecordIdOrderByPaidDateDesc(record.getId())).hasSize(1);
        assertThat(householdRepository.findById(household.getId()).orElseThrow().getIsActive()).isFalse();
    }

    @Test
    void recalculatingPaidWaterBillIsRejectedAndKeepsHistory() {
        householdRepository.save(Household.builder()
                .householdName("owner")
                .waterMeterId("WM-RECALC")
                .villageName("village")
                .phone("")
                .isActive(true)
                .build());
        waterBillRepository.save(WaterBill.builder()
                .waterMeterId("WM-RECALC")
                .billYear(2026)
                .billMonth(6)
                .waterAmount(new BigDecimal("10.00"))
                .waterCharge(new BigDecimal("18.00"))
                .actualWaterPaid(new BigDecimal("18.00"))
                .waterStatus("paid")
                .build());

        assertThatThrownBy(() -> readingService.singleSave(
                "WM-RECALC", new BigDecimal("5.00"), LocalDate.of(2026, 6, 2)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有收款");

        WaterBill bill = waterBillRepository
                .findByWaterMeterIdAndBillYearAndBillMonth("WM-RECALC", 2026, 6)
                .orElseThrow();
        assertThat(bill.getWaterCharge()).isEqualByComparingTo(new BigDecimal("18.00"));
        assertThat(bill.getActualWaterPaid()).isEqualByComparingTo(new BigDecimal("18.00"));
        assertThat(prepaymentLogRepository.getBalance("WM-RECALC")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void reversedReadingIsRejectedBeforeSavingBill() {
        householdRepository.save(Household.builder()
                .householdName("owner")
                .waterMeterId("WM-REVERSE")
                .villageName("village")
                .phone("")
                .isActive(true)
                .build());
        readingRepository.save(Reading.builder()
                .waterMeterId("WM-REVERSE")
                .readingDate(LocalDate.of(2026, 6, 1))
                .currentReading(new BigDecimal("10.00"))
                .previousReading(BigDecimal.ZERO)
                .usageAmount(new BigDecimal("10.00"))
                .isAbnormal(false)
                .build());

        assertThatThrownBy(() -> readingService.singleSave(
                "WM-REVERSE", new BigDecimal("8.00"), LocalDate.of(2026, 6, 2)))
                .isInstanceOf(BusinessException.class);
        assertThat(waterBillRepository.findByWaterMeterId("WM-REVERSE")).isEmpty();
    }

    @Test
    void materialTotalFeeCannotBeReducedBelowActualPaid() {
        MaterialRecord record = materialRecordRepository.save(MaterialRecord.builder()
                .householdName("owner")
                .waterMeterId("WM-MAT-PAID")
                .villageName("village")
                .totalFee(new BigDecimal("1500.00"))
                .actualPaid(new BigDecimal("100.00"))
                .status("partial")
                .build());
        MaterialRecordRequest request = new MaterialRecordRequest();
        request.setTotalFee(new BigDecimal("50.00"));

        assertThatThrownBy(() -> materialRecordService.update(record.getId(), request))
                .isInstanceOf(BusinessException.class);
    }

    private PaymentRequest waterPayment(List<Long> billIds, BigDecimal amount) {
        PaymentRequest request = new PaymentRequest();
        request.setBillType("water");
        request.setBillIds(billIds);
        request.setAmount(amount);
        request.setPaidDate(LocalDate.of(2026, 6, 20));
        request.setPaymentMethod("现金");
        request.setOperator("tester");
        return request;
    }

    private WaterBill saveWaterBill(String meterId, int month, String charge, String paid, String status) {
        return waterBillRepository.save(WaterBill.builder()
                .waterMeterId(meterId)
                .billYear(2026)
                .billMonth(month)
                .waterAmount(new BigDecimal(charge))
                .waterCharge(new BigDecimal(charge))
                .actualWaterPaid(new BigDecimal(paid))
                .waterStatus(status)
                .build());
    }

    private HouseholdRequest householdRequest(String name, String meterId, String village) {
        HouseholdRequest request = new HouseholdRequest();
        request.setHouseholdName(name);
        request.setWaterMeterId(meterId);
        request.setVillageName(village);
        request.setPhone("");
        return request;
    }
}
