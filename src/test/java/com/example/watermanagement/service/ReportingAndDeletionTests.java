package com.example.watermanagement.service;

import com.example.watermanagement.dto.VillageCollectionSummaryRow;
import com.example.watermanagement.controller.HouseholdController;
import com.example.watermanagement.entity.Household;
import com.example.watermanagement.entity.MaterialPayment;
import com.example.watermanagement.entity.MaterialRecord;
import com.example.watermanagement.entity.Payment;
import com.example.watermanagement.entity.PrepaymentLog;
import com.example.watermanagement.entity.Reading;
import com.example.watermanagement.entity.WaterBill;
import com.example.watermanagement.exception.BusinessException;
import com.example.watermanagement.repository.AccountingAdjustmentRepository;
import com.example.watermanagement.repository.HouseholdRepository;
import com.example.watermanagement.repository.MaterialPaymentRepository;
import com.example.watermanagement.repository.MaterialRecordRepository;
import com.example.watermanagement.repository.PaymentRepository;
import com.example.watermanagement.repository.PrepaymentLogRepository;
import com.example.watermanagement.repository.ReadingRepository;
import com.example.watermanagement.repository.WaterBillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ReportingAndDeletionTests {

    @Autowired private ReportService reportService;
    @Autowired private HouseholdService householdService;
    @Autowired private HouseholdController householdController;
    @Autowired private MaterialRecordService materialRecordService;
    @Autowired private HouseholdRepository householdRepository;
    @Autowired private ReadingRepository readingRepository;
    @Autowired private WaterBillRepository waterBillRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PrepaymentLogRepository prepaymentLogRepository;
    @Autowired private AccountingAdjustmentRepository adjustmentRepository;
    @Autowired private MaterialRecordRepository materialRecordRepository;
    @Autowired private MaterialPaymentRepository materialPaymentRepository;

    @BeforeEach
    void cleanDatabase() {
        materialPaymentRepository.deleteAll();
        paymentRepository.deleteAll();
        prepaymentLogRepository.deleteAll();
        adjustmentRepository.deleteAll();
        materialRecordRepository.deleteAll();
        waterBillRepository.deleteAll();
        readingRepository.deleteAll();
        householdRepository.deleteAll();
    }

    @Test
    void collectionRateIsNullWhenThereAreNoReceivables() {
        household("M-NO-BILL", true);

        VillageCollectionSummaryRow row = reportService.getVillageCollectionSummary(2026, 7).getFirst();

        assertThat(row.getCollectionRate()).isNull();
    }

    @Test
    void inactiveHouseholdRemainsInHistoricalReport() {
        household("M-INACTIVE", false);
        waterBillRepository.save(WaterBill.builder()
                .waterMeterId("M-INACTIVE")
                .billYear(2026)
                .billMonth(6)
                .waterAmount(new BigDecimal("10.00"))
                .waterCharge(new BigDecimal("18.00"))
                .actualWaterPaid(BigDecimal.ZERO)
                .waterStatus("未收")
                .build());

        assertThat(reportService.getWaterBillReportData(2026, 6, null))
                .extracting(row -> row.getWaterMeterId())
                .containsExactly("M-INACTIVE");
    }

    @Test
    void householdWithBusinessHistoryIsDeactivatedAndReturnsPreservedHistoryCounts() {
        Household household = household("M-HISTORY", true);
        readingRepository.save(Reading.builder()
                .waterMeterId("M-HISTORY")
                .readingDate(LocalDate.of(2026, 6, 1))
                .previousReading(BigDecimal.ZERO)
                .currentReading(BigDecimal.TEN)
                .usageAmount(BigDecimal.TEN)
                .isAbnormal(false)
                .build());
        WaterBill bill = waterBillRepository.save(WaterBill.builder()
                .waterMeterId("M-HISTORY")
                .billYear(2026)
                .billMonth(7)
                .waterAmount(new BigDecimal("3.00"))
                .waterCharge(new BigDecimal("5.40"))
                .actualWaterPaid(new BigDecimal("1.40"))
                .waterStatus("部分收")
                .build());
        paymentRepository.save(Payment.builder()
                .billType("water")
                .billId(bill.getId())
                .amount(new BigDecimal("1.40"))
                .paidDate(LocalDate.of(2026, 7, 2))
                .build());
        paymentRepository.save(Payment.builder()
                .billType("material")
                .billId(bill.getId())
                .amount(new BigDecimal("100.00"))
                .paidDate(LocalDate.of(2026, 7, 2))
                .build());
        prepaymentLogRepository.save(PrepaymentLog.builder()
                .waterMeterId("M-HISTORY")
                .amount(new BigDecimal("2.00"))
                .type("OVERPAYMENT")
                .build());
        MaterialRecord materialRecord = materialRecordRepository.save(MaterialRecord.builder()
                .householdName("测试户")
                .waterMeterId("M-HISTORY")
                .villageName("测试村")
                .totalFee(new BigDecimal("1500.00"))
                .actualPaid(new BigDecimal("100.00"))
                .status("部分收")
                .build());
        materialPaymentRepository.save(MaterialPayment.builder()
                .recordId(materialRecord.getId())
                .amount(new BigDecimal("100.00"))
                .paidDate(LocalDate.of(2026, 7, 2))
                .collector("tester")
                .build());

        var result = householdService.delete(household.getId());

        Household archived = householdRepository.findById(household.getId()).orElseThrow();
        assertThat(archived.getIsActive()).isFalse();
        assertThat(result.getAction()).isEqualTo("DEACTIVATED");
        assertThat(result.getHouseholdId()).isEqualTo(household.getId());
        assertThat(result.getWaterMeterId()).isEqualTo("M-HISTORY");
        assertThat(result.getReadingCount()).isEqualTo(1);
        assertThat(result.getBillCount()).isEqualTo(1);
        assertThat(result.getPaymentCount()).isEqualTo(1);
        assertThat(result.getPrepaymentCount()).isEqualTo(1);
        assertThat(result.getMaterialRecordCount()).isEqualTo(1);
        assertThat(result.getMaterialPaymentCount()).isEqualTo(1);
        assertThat(result.getOutstandingAmount()).isEqualByComparingTo("4.00");
        assertThat(readingRepository.findAll()).hasSize(1);
        assertThat(waterBillRepository.findAll()).hasSize(1);
        assertThat(paymentRepository.findAll()).hasSize(2);
        assertThat(prepaymentLogRepository.findAll()).hasSize(1);
        assertThat(materialRecordRepository.findAll()).hasSize(1);
        assertThat(materialPaymentRepository.findAll()).hasSize(1);
    }

    @Test
    void householdWithoutHistoryIsPhysicallyDeleted() {
        Household household = household("M-EMPTY", true);

        var result = householdService.delete(household.getId());

        assertThat(householdRepository.findById(household.getId())).isEmpty();
        assertThat(result.getAction()).isEqualTo("DELETED");
        assertThat(result.getHouseholdId()).isEqualTo(household.getId());
        assertThat(result.getWaterMeterId()).isEqualTo("M-EMPTY");
        assertThat(result.getReadingCount()).isZero();
        assertThat(result.getBillCount()).isZero();
        assertThat(result.getPaymentCount()).isZero();
        assertThat(result.getPrepaymentCount()).isZero();
        assertThat(result.getMaterialRecordCount()).isZero();
        assertThat(result.getMaterialPaymentCount()).isZero();
        assertThat(result.getOutstandingAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void householdControllerReturnsSingleAndBatchRemovalResults() {
        Household single = household("M-CONTROLLER-SINGLE", true);

        var singleResponse = householdController.delete(single.getId(), true);

        assertThat(singleResponse.getData().getAction()).isEqualTo("DELETED");
        assertThat(singleResponse.getData().getHouseholdId()).isEqualTo(single.getId());

        Household first = household("M-CONTROLLER-BATCH-1", true);
        Household second = household("M-CONTROLLER-BATCH-2", true);

        var batchResponse = householdController.batchDelete(Map.of(
                "ids", List.of(first.getId(), second.getId()),
                "confirm", true));

        assertThat(batchResponse.getData())
                .extracting(result -> result.getHouseholdId())
                .containsExactly(first.getId(), second.getId());
        assertThat(batchResponse.getData())
                .extracting(result -> result.getAction())
                .containsOnly("DELETED");
    }

    @Test
    void batchDeleteDeduplicatesIdsInFirstSeenOrderAndPreservesHistory() {
        Household empty = household("M-BATCH-EMPTY", true);
        Household withHistory = household("M-BATCH-HISTORY", true);
        readingRepository.save(Reading.builder()
                .waterMeterId("M-BATCH-HISTORY")
                .readingDate(LocalDate.of(2026, 7, 1))
                .previousReading(BigDecimal.ZERO)
                .currentReading(BigDecimal.TEN)
                .usageAmount(BigDecimal.TEN)
                .isAbnormal(false)
                .build());

        var results = householdService.batchDelete(List.of(
                empty.getId(), withHistory.getId(), empty.getId(), withHistory.getId()));

        assertThat(results)
                .extracting(result -> result.getHouseholdId())
                .containsExactly(empty.getId(), withHistory.getId());
        assertThat(results)
                .extracting(result -> result.getAction())
                .containsExactly("DELETED", "DEACTIVATED");
        assertThat(householdRepository.findById(empty.getId())).isEmpty();
        assertThat(householdRepository.findById(withHistory.getId()).orElseThrow().getIsActive()).isFalse();
        assertThat(readingRepository.findByWaterMeterIdInOrderByReadingDateDesc(
                List.of("M-BATCH-HISTORY"))).hasSize(1);
    }

    @Test
    void batchDeleteRollsBackAllChangesWhenAnyHouseholdDoesNotExist() {
        Household empty = household("M-BATCH-ROLLBACK-EMPTY", true);
        Household withHistory = household("M-BATCH-ROLLBACK-HISTORY", true);
        readingRepository.save(Reading.builder()
                .waterMeterId("M-BATCH-ROLLBACK-HISTORY")
                .readingDate(LocalDate.of(2026, 7, 1))
                .previousReading(BigDecimal.ZERO)
                .currentReading(BigDecimal.ONE)
                .usageAmount(BigDecimal.ONE)
                .isAbnormal(false)
                .build());

        assertThatThrownBy(() -> householdService.batchDelete(List.of(
                empty.getId(), withHistory.getId(), Long.MAX_VALUE)))
                .isInstanceOf(BusinessException.class);

        assertThat(householdRepository.findById(empty.getId()).orElseThrow().getIsActive()).isTrue();
        assertThat(householdRepository.findById(withHistory.getId()).orElseThrow().getIsActive()).isTrue();
        assertThat(readingRepository.findByWaterMeterIdInOrderByReadingDateDesc(
                List.of("M-BATCH-ROLLBACK-HISTORY"))).hasSize(1);
    }

    @Test
    void deletingInactiveHouseholdWithHistoryAgainIsIdempotent() {
        Household household = household("M-INACTIVE-DELETE", false);
        Reading reading = readingRepository.save(Reading.builder()
                .waterMeterId("M-INACTIVE-DELETE")
                .readingDate(LocalDate.of(2026, 7, 1))
                .previousReading(BigDecimal.ZERO)
                .currentReading(BigDecimal.ONE)
                .usageAmount(BigDecimal.ONE)
                .isAbnormal(false)
                .build());

        var result = householdService.delete(household.getId());

        assertThat(result.getAction()).isEqualTo("DEACTIVATED");
        assertThat(result.getReadingCount()).isEqualTo(1);
        assertThat(householdRepository.findById(household.getId()).orElseThrow().getIsActive()).isFalse();
        assertThat(readingRepository.findById(reading.getId())).isPresent();
    }

    @Test
    void deleteByVillageReturnsEmptyListWhenNoActiveHouseholdsMatch() {
        assertThat(householdService.deleteByVillage("不存在村组")).isEmpty();
    }

    @Test
    void materialRecordWithPaymentCannotBeDeleted() {
        MaterialRecord record = materialRecordRepository.save(MaterialRecord.builder()
                .householdName("材料户")
                .waterMeterId("M-MATERIAL")
                .villageName("测试村")
                .totalFee(new BigDecimal("1500.00"))
                .actualPaid(new BigDecimal("100.00"))
                .status("部分收")
                .build());
        materialPaymentRepository.save(MaterialPayment.builder()
                .recordId(record.getId())
                .amount(new BigDecimal("100.00"))
                .paidDate(LocalDate.of(2026, 7, 1))
                .collector("tester")
                .build());

        assertThatThrownBy(() -> materialRecordService.delete(record.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缴费");
        assertThat(materialRecordRepository.findById(record.getId())).isPresent();
    }

    @Test
    void materialImportPreviewUsesDefaultOnlyForBlankAmount() throws Exception {
        byte[] workbook;
        try (XSSFWorkbook excel = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = excel.createSheet("测试村");
            sheet.createRow(0).createCell(0).setCellValue("材料费登记表");
            var header = sheet.createRow(1);
            header.createCell(0).setCellValue("序号");
            header.createCell(1).setCellValue("户主姓名");
            header.createCell(2).setCellValue("表号");
            header.createCell(3).setCellValue("电话");
            header.createCell(4).setCellValue("材料费");
            var blank = sheet.createRow(2);
            blank.createCell(0).setCellValue(1);
            blank.createCell(1).setCellValue("空金额户");
            blank.createCell(2).setCellValue("M-BLANK-FEE");
            var invalid = sheet.createRow(3);
            invalid.createCell(0).setCellValue(2);
            invalid.createCell(1).setCellValue("非法金额户");
            invalid.createCell(2).setCellValue("M-INVALID-FEE");
            invalid.createCell(4).setCellValue("not-a-number");
            excel.write(output);
            workbook = output.toByteArray();
        }

        var result = materialRecordService.previewImportFromExcel(new ByteArrayInputStream(workbook));

        assertThat(result.get("success")).isEqualTo(1);
        assertThat(result.get("fail")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        var details = (java.util.List<java.util.Map<String, Object>>) result.get("details");
        assertThat(details).anySatisfy(detail -> {
            assertThat(detail.get("waterMeterId")).isEqualTo("M-BLANK-FEE");
            assertThat(detail.get("totalFee")).isEqualTo(new BigDecimal("1500.00"));
            assertThat(detail.get("usedDefault")).isEqualTo(true);
        });
        assertThat(details).anySatisfy(detail -> {
            assertThat(detail.get("waterMeterId")).isEqualTo("M-INVALID-FEE");
            assertThat(detail.get("status")).isEqualTo("fail");
        });
        assertThat(materialRecordRepository.count()).isZero();
    }

    private Household household(String meterId, boolean active) {
        return householdRepository.save(Household.builder()
                .householdName("测试户")
                .waterMeterId(meterId)
                .villageName("测试村")
                .phone("")
                .isActive(active)
                .build());
    }
}
