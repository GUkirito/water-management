package com.example.watermanagement.service;

import com.alibaba.excel.EasyExcel;
import com.example.watermanagement.dto.HistoricalReadingImportRow;
import com.example.watermanagement.dto.ReadingExportRow;
import com.example.watermanagement.entity.Household;
import com.example.watermanagement.entity.Reading;
import com.example.watermanagement.repository.HouseholdRepository;
import com.example.watermanagement.repository.MonthLockRepository;
import com.example.watermanagement.repository.ReadingRepository;
import com.example.watermanagement.repository.WaterBillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ReadingImportPreviewTests {

    @Autowired
    private ReadingService readingService;

    @Autowired
    private AccountingControlService accountingControlService;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private ReadingRepository readingRepository;

    @Autowired
    private WaterBillRepository waterBillRepository;

    @Autowired
    private MonthLockRepository monthLockRepository;

    @BeforeEach
    void cleanDatabase() {
        monthLockRepository.deleteAll();
        waterBillRepository.deleteAll();
        readingRepository.deleteAll();
        householdRepository.deleteAll();
    }

    @Test
    void readingImportPreviewDoesNotWriteRowsOrBills() {
        householdRepository.save(Household.builder()
                .householdName("owner")
                .waterMeterId("WM-PREVIEW")
                .villageName("village")
                .phone("")
                .isActive(true)
                .build());

        Map<String, Object> result = readingService.previewImportReadings(excel(List.of(ReadingExportRow.builder()
                .index(1)
                .householdName("owner")
                .waterMeterId("WM-PREVIEW")
                .villageName("village")
                .previousReading(BigDecimal.ZERO)
                .currentReading(new BigDecimal("10.00"))
                .waterPrice(new BigDecimal("1.80"))
                .build()), ReadingExportRow.class), LocalDate.of(2026, 6, 30));

        assertThat(result.get("success")).isEqualTo(1);
        assertThat(readingRepository.count()).isZero();
        assertThat(waterBillRepository.count()).isZero();
        assertThat((List<?>) result.get("details")).hasSize(1);
    }

    @Test
    void abnormalRowsAreNotCountedAsSuccess() {
        householdRepository.save(Household.builder()
                .householdName("owner")
                .waterMeterId("WM-ABNORMAL-COUNT")
                .villageName("village")
                .phone("")
                .isActive(true)
                .build());

        Map<String, Object> result = readingService.previewImportReadings(excel(List.of(ReadingExportRow.builder()
                .index(1)
                .householdName("owner")
                .waterMeterId("WM-ABNORMAL-COUNT")
                .villageName("village")
                .previousReading(BigDecimal.ZERO)
                .currentReading(new BigDecimal("200.00"))
                .waterPrice(new BigDecimal("1.80"))
                .build()), ReadingExportRow.class), LocalDate.of(2026, 6, 30));

        assertThat(result.get("success")).isEqualTo(0);
        assertThat(result.get("abnormal")).isEqualTo(1);
    }

    @Test
    void standardImportRejectsLockedMonthWithoutWritingRows() {
        householdRepository.save(Household.builder()
                .householdName("owner")
                .waterMeterId("WM-LOCKED-IMPORT")
                .villageName("village")
                .phone("")
                .isActive(true)
                .build());
        accountingControlService.lockMonth(2026, 6, "tester", "月结完成");

        Map<String, Object> result = readingService.importReadings(excel(List.of(
                ReadingExportRow.builder()
                        .index(1)
                        .householdName("owner")
                        .waterMeterId("WM-LOCKED-IMPORT")
                        .villageName("village")
                        .currentReading(new BigDecimal("10.00"))
                        .build()
        ), ReadingExportRow.class), LocalDate.of(2026, 6, 30));

        assertThat(result.get("fail")).isEqualTo(1);
        assertThat(readingRepository.count()).isZero();
        assertThat(waterBillRepository.count()).isZero();
    }

    @Test
    void historicalImportRejectsLockedMonthWithoutWritingRows() {
        householdRepository.save(Household.builder()
                .householdName("owner")
                .waterMeterId("WM-LOCKED-HISTORY")
                .villageName("village")
                .phone("")
                .isActive(true)
                .build());
        accountingControlService.lockMonth(2025, 1, "tester", "月结完成");

        Map<String, Object> result = readingService.importHistoricalReadings(excel(List.of(
                HistoricalReadingImportRow.builder()
                        .index(1)
                        .householdName("owner")
                        .waterMeterId("WM-LOCKED-HISTORY")
                        .villageName("village")
                        .readingDate("2025-01-31")
                        .currentReading(new BigDecimal("12.00"))
                        .build()
        ), HistoricalReadingImportRow.class));

        assertThat(result.get("fail")).isEqualTo(1);
        assertThat(readingRepository.count()).isZero();
        assertThat(waterBillRepository.count()).isZero();
    }

    @Test
    void historyImportWritesReadingsButNoWaterBills() {
        householdRepository.save(Household.builder()
                .householdName("owner")
                .waterMeterId("WM-HISTORY")
                .villageName("village")
                .phone("")
                .isActive(true)
                .build());

        Map<String, Object> result = readingService.importHistoricalReadings(excel(List.of(
                HistoricalReadingImportRow.builder()
                        .index(1)
                        .householdName("owner")
                        .waterMeterId("WM-HISTORY")
                        .villageName("village")
                        .readingDate("2025-01-31")
                        .currentReading(new BigDecimal("12.00"))
                        .build(),
                HistoricalReadingImportRow.builder()
                        .index(2)
                        .householdName("owner")
                        .waterMeterId("WM-HISTORY")
                        .villageName("village")
                        .readingDate("2025-02-28")
                        .currentReading(new BigDecimal("20.00"))
                        .build()
        ), HistoricalReadingImportRow.class));

        assertThat(result.get("success")).isEqualTo(2);
        assertThat(readingRepository.count()).isEqualTo(2);
        assertThat(waterBillRepository.count()).isZero();
        Reading second = readingRepository.findByWaterMeterIdAndReadingDate("WM-HISTORY", LocalDate.of(2025, 2, 28))
                .orElseThrow();
        assertThat(second.getPreviousReading()).isEqualByComparingTo(new BigDecimal("12.00"));
        assertThat(second.getUsageAmount()).isEqualByComparingTo(new BigDecimal("8.00"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void historyPreviewUsesEarlierRowsInSameFileAsPreviousReading() {
        householdRepository.save(Household.builder()
                .householdName("owner")
                .waterMeterId("WM-HISTORY-PREVIEW")
                .villageName("village")
                .phone("")
                .isActive(true)
                .build());

        Map<String, Object> result = readingService.previewHistoricalReadings(excel(List.of(
                HistoricalReadingImportRow.builder()
                        .index(1)
                        .householdName("owner")
                        .waterMeterId("WM-HISTORY-PREVIEW")
                        .villageName("village")
                        .readingDate("2025-01-31")
                        .currentReading(new BigDecimal("12.00"))
                        .build(),
                HistoricalReadingImportRow.builder()
                        .index(2)
                        .householdName("owner")
                        .waterMeterId("WM-HISTORY-PREVIEW")
                        .villageName("village")
                        .readingDate("2025-02-28")
                        .currentReading(new BigDecimal("20.00"))
                        .build()
        ), HistoricalReadingImportRow.class));

        List<Object> details = (List<Object>) result.get("details");
        var second = (com.example.watermanagement.dto.ReadingImportDetail) details.get(1);
        assertThat(second.getPreviousReading()).isEqualByComparingTo(new BigDecimal("12.00"));
        assertThat(second.getUsageAmount()).isEqualByComparingTo(new BigDecimal("8.00"));
        assertThat(readingRepository.count()).isZero();
    }

    @Test
    void historyImportSkipsExistingSameDayReading() {
        householdRepository.save(Household.builder()
                .householdName("owner")
                .waterMeterId("WM-HISTORY-SKIP")
                .villageName("village")
                .phone("")
                .isActive(true)
                .build());
        readingRepository.save(Reading.builder()
                .waterMeterId("WM-HISTORY-SKIP")
                .readingDate(LocalDate.of(2025, 1, 31))
                .previousReading(BigDecimal.ZERO)
                .currentReading(new BigDecimal("9.00"))
                .usageAmount(new BigDecimal("9.00"))
                .isAbnormal(false)
                .build());

        Map<String, Object> result = readingService.importHistoricalReadings(excel(List.of(
                HistoricalReadingImportRow.builder()
                        .index(1)
                        .householdName("owner")
                        .waterMeterId("WM-HISTORY-SKIP")
                        .villageName("village")
                        .readingDate("2025-01-31")
                        .currentReading(new BigDecimal("12.00"))
                        .build()
        ), HistoricalReadingImportRow.class));

        assertThat(result.get("skip")).isEqualTo(1);
        assertThat(readingRepository.count()).isEqualTo(1);
        Reading existing = readingRepository.findByWaterMeterIdAndReadingDate("WM-HISTORY-SKIP", LocalDate.of(2025, 1, 31))
                .orElseThrow();
        assertThat(existing.getCurrentReading()).isEqualByComparingTo(new BigDecimal("9.00"));
    }

    private <T> ByteArrayInputStream excel(List<T> rows, Class<T> rowClass) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, rowClass).sheet("sheet1").doWrite(rows);
        return new ByteArrayInputStream(out.toByteArray());
    }
}
