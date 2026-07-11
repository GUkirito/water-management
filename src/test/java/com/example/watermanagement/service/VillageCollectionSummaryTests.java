package com.example.watermanagement.service;

import com.example.watermanagement.dto.VillageCollectionSummaryRow;
import com.example.watermanagement.dto.WaterBillReportRow;
import com.example.watermanagement.entity.Household;
import com.example.watermanagement.entity.Payment;
import com.example.watermanagement.entity.Reading;
import com.example.watermanagement.entity.WaterBill;
import com.example.watermanagement.repository.HouseholdRepository;
import com.example.watermanagement.repository.PaymentRepository;
import com.example.watermanagement.repository.ReadingRepository;
import com.example.watermanagement.repository.WaterBillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class VillageCollectionSummaryTests {

    @Autowired
    private ReportService reportService;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private WaterBillRepository waterBillRepository;

    @Autowired
    private ReadingRepository readingRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @BeforeEach
    void cleanDatabase() {
        paymentRepository.deleteAll();
        readingRepository.deleteAll();
        waterBillRepository.deleteAll();
        householdRepository.deleteAll();
    }

    @Test
    void summarizesCollectionByVillage() {
        householdRepository.save(household("WM-A1", "Alpha"));
        householdRepository.save(household("WM-A2", "Alpha"));
        householdRepository.save(household("WM-B1", "Beta"));

        waterBillRepository.save(waterBill("WM-A1", "20.00", "20.00", "已收"));
        waterBillRepository.save(waterBill("WM-A2", "30.00", "10.00", "部分收"));
        waterBillRepository.save(waterBill("WM-B1", "50.00", "0.00", "未收"));

        readingRepository.save(Reading.builder()
                .waterMeterId("WM-A2")
                .readingDate(LocalDate.of(2026, 6, 5))
                .currentReading(new BigDecimal("200.00"))
                .previousReading(new BigDecimal("20.00"))
                .usageAmount(new BigDecimal("180.00"))
                .isAbnormal(true)
                .abnormalReason("usage spike")
                .build());

        List<VillageCollectionSummaryRow> rows = reportService.getVillageCollectionSummary(2026, 6);

        assertThat(rows).hasSize(2);
        VillageCollectionSummaryRow alpha = rows.stream()
                .filter(row -> row.getVillageName().equals("Alpha"))
                .findFirst()
                .orElseThrow();
        assertThat(alpha.getHouseholdCount()).isEqualTo(2);
        assertThat(alpha.getWaterCharge()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(alpha.getActualWaterPaid()).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(alpha.getUnpaidHouseholdCount()).isEqualTo(1);
        assertThat(alpha.getAbnormalReadingCount()).isEqualTo(1);
        assertThat(alpha.getCollectionRate()).isEqualByComparingTo(new BigDecimal("60.00"));
    }

    @Test
    void waterBillReportIncludesPaymentMethods() {
        householdRepository.save(household("WM-A1", "Alpha"));
        WaterBill bill = waterBillRepository.save(waterBill("WM-A1", "20.00", "20.00", "已收"));
        paymentRepository.save(payment(bill.getId(), "现金", 9));
        paymentRepository.save(payment(bill.getId(), "微信", 10));
        paymentRepository.save(payment(bill.getId(), "微信", 11));

        List<WaterBillReportRow> rows = reportService.getWaterBillReportData(2026, 6, List.of("Alpha"));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getPaymentMethod()).isEqualTo("微信, 现金");
    }

    private Household household(String meterId, String village) {
        return Household.builder()
                .householdName(meterId + " owner")
                .waterMeterId(meterId)
                .villageName(village)
                .phone("")
                .isActive(true)
                .build();
    }

    private WaterBill waterBill(String meterId, String charge, String paid, String status) {
        return WaterBill.builder()
                .waterMeterId(meterId)
                .billYear(2026)
                .billMonth(6)
                .waterAmount(new BigDecimal("10.00"))
                .waterCharge(new BigDecimal(charge))
                .actualWaterPaid(new BigDecimal(paid))
                .waterStatus(status)
                .build();
    }

    private Payment payment(Long billId, String method, int day) {
        return Payment.builder()
                .billType("water")
                .billId(billId)
                .amount(new BigDecimal("10.00"))
                .paidDate(LocalDate.of(2026, 6, day))
                .paymentMethod(method)
                .operator("tester")
                .build();
    }
}
