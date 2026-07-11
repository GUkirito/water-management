package com.example.watermanagement.service;

import com.example.watermanagement.dto.VillageCollectionSummaryRow;
import com.example.watermanagement.entity.Household;
import com.example.watermanagement.entity.MaterialPayment;
import com.example.watermanagement.entity.MaterialRecord;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ReportingAndDeletionTests {

    @Autowired private ReportService reportService;
    @Autowired private HouseholdService householdService;
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
    void householdWithFinancialHistoryIsArchivedInsteadOfDeleted() {
        Household household = household("M-HISTORY", true);
        readingRepository.save(Reading.builder()
                .waterMeterId("M-HISTORY")
                .readingDate(LocalDate.of(2026, 6, 1))
                .previousReading(BigDecimal.ZERO)
                .currentReading(BigDecimal.TEN)
                .usageAmount(BigDecimal.TEN)
                .isAbnormal(false)
                .build());

        householdService.delete(household.getId());

        Household archived = householdRepository.findById(household.getId()).orElseThrow();
        assertThat(archived.getIsActive()).isFalse();
        assertThat(readingRepository.findAll()).hasSize(1);
    }

    @Test
    void householdWithoutHistoryIsPhysicallyDeleted() {
        Household household = household("M-EMPTY", true);

        householdService.delete(household.getId());

        assertThat(householdRepository.findById(household.getId())).isEmpty();
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
