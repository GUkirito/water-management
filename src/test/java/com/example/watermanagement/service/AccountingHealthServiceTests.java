package com.example.watermanagement.service;

import com.example.watermanagement.entity.Household;
import com.example.watermanagement.entity.MaterialPayment;
import com.example.watermanagement.entity.Payment;
import com.example.watermanagement.entity.PrepaymentLog;
import com.example.watermanagement.entity.Reading;
import com.example.watermanagement.entity.WaterBill;
import com.example.watermanagement.entity.AccountingAdjustment;
import com.example.watermanagement.dto.AccountingHealthIssue;
import com.example.watermanagement.repository.HouseholdRepository;
import com.example.watermanagement.repository.MaterialPaymentRepository;
import com.example.watermanagement.repository.MaterialRecordRepository;
import com.example.watermanagement.repository.PaymentRepository;
import com.example.watermanagement.repository.PrepaymentLogRepository;
import com.example.watermanagement.repository.ReadingRepository;
import com.example.watermanagement.repository.WaterBillRepository;
import com.example.watermanagement.repository.AccountingAdjustmentRepository;
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
class AccountingHealthServiceTests {

    @Autowired
    private AccountingHealthService accountingHealthService;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private WaterBillRepository waterBillRepository;

    @Autowired
    private ReadingRepository readingRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PrepaymentLogRepository prepaymentLogRepository;

    @Autowired
    private MaterialPaymentRepository materialPaymentRepository;

    @Autowired
    private MaterialRecordRepository materialRecordRepository;

    @Autowired
    private AccountingAdjustmentRepository adjustmentRepository;

    @BeforeEach
    void cleanDatabase() {
        adjustmentRepository.deleteAll();
        prepaymentLogRepository.deleteAll();
        paymentRepository.deleteAll();
        materialPaymentRepository.deleteAll();
        readingRepository.deleteAll();
        waterBillRepository.deleteAll();
        materialRecordRepository.deleteAll();
        householdRepository.deleteAll();
    }

    @Test
    void findsCoreAccountingHealthProblems() {
        householdRepository.save(Household.builder()
                .householdName("owner")
                .waterMeterId("WM-HEALTH")
                .villageName("village")
                .phone("")
                .isActive(true)
                .build());
        waterBillRepository.save(waterBill("WM-DUP", 2026, 6, "10.00", "5.00"));
        waterBillRepository.save(waterBill("WM-DUP", 2026, 6, "10.00", "0.00"));
        waterBillRepository.save(waterBill("WM-OVERPAID", 2026, 6, "10.00", "12.00"));
        readingRepository.save(Reading.builder()
                .waterMeterId("WM-HEALTH")
                .readingDate(LocalDate.of(2026, 6, 1))
                .currentReading(new BigDecimal("8.00"))
                .previousReading(new BigDecimal("10.00"))
                .usageAmount(new BigDecimal("-2.00"))
                .isAbnormal(true)
                .build());
        prepaymentLogRepository.save(PrepaymentLog.builder()
                .waterMeterId("WM-NO-HOUSEHOLD")
                .amount(new BigDecimal("3.00"))
                .type("OVERPAYMENT")
                .build());
        paymentRepository.save(Payment.builder()
                .billType("water")
                .billId(99999L)
                .amount(new BigDecimal("1.00"))
                .paidDate(LocalDate.of(2026, 6, 2))
                .build());
        materialPaymentRepository.save(MaterialPayment.builder()
                .recordId(99999L)
                .amount(new BigDecimal("2.00"))
                .paidDate(LocalDate.of(2026, 6, 2))
                .build());
        readingRepository.save(Reading.builder()
                .waterMeterId("WM-HEALTH")
                .readingDate(LocalDate.of(2026, 7, 1))
                .currentReading(new BigDecimal("20.00"))
                .previousReading(new BigDecimal("7.00"))
                .usageAmount(new BigDecimal("13.00"))
                .isAbnormal(false)
                .build());
        readingRepository.save(Reading.builder()
                .waterMeterId("WM-HEALTH")
                .readingDate(LocalDate.of(2026, 7, 15))
                .currentReading(new BigDecimal("21.00"))
                .previousReading(new BigDecimal("20.00"))
                .usageAmount(BigDecimal.ONE)
                .isAbnormal(false)
                .build());
        readingRepository.save(Reading.builder()
                .waterMeterId("WM-ORPHAN-READING")
                .readingDate(LocalDate.of(2026, 6, 1))
                .currentReading(BigDecimal.ONE)
                .previousReading(BigDecimal.ZERO)
                .usageAmount(BigDecimal.ONE)
                .isAbnormal(false)
                .build());
        waterBillRepository.save(waterBill("WM-ORPHAN-BILL", 2026, 5, "-1.00", "0.00"));
        WaterBill mismatch = waterBillRepository.save(waterBill("WM-HEALTH", 2026, 7, "10.00", "5.00"));
        paymentRepository.save(Payment.builder()
                .billType("water")
                .billId(mismatch.getId())
                .amount(BigDecimal.ONE)
                .paidDate(LocalDate.of(2026, 7, 2))
                .build());
        adjustmentRepository.save(AccountingAdjustment.builder()
                .targetType("WATER_BILL")
                .targetId(88888L)
                .beforeAmount(BigDecimal.ONE)
                .afterAmount(BigDecimal.TEN)
                .reason("测试")
                .build());
        prepaymentLogRepository.save(PrepaymentLog.builder()
                .waterMeterId("WM-HEALTH")
                .amount(new BigDecimal("-3.00"))
                .type("AUTO_DEDUCT")
                .build());

        List<AccountingHealthIssue> issues = accountingHealthService.check();

        assertThat(issues).extracting(AccountingHealthIssue::getType)
                .contains(
                        "DUPLICATE_WATER_BILL",
                        "WATER_BILL_OVERPAID",
                        "NEGATIVE_READING_USAGE",
                        "ORPHAN_PREPAYMENT_LOG",
                        "ORPHAN_WATER_PAYMENT",
                        "ORPHAN_MATERIAL_PAYMENT",
                        "BROKEN_READING_CHAIN",
                        "NEGATIVE_WATER_BILL_AMOUNT",
                        "INCONSISTENT_WATER_BILL_STATUS",
                        "ORPHAN_READING",
                        "ORPHAN_WATER_BILL",
                        "DUPLICATE_MONTHLY_READING",
                        "PAYMENT_TOTAL_MISMATCH",
                        "ORPHAN_ADJUSTMENT_TARGET",
                        "NEGATIVE_PREPAYMENT_BALANCE");
    }

    @Test
    void acceptsZeroAmountBillOnlyWhenStatusIsNoPaymentRequired() {
        householdRepository.save(Household.builder()
                .householdName("zero valid")
                .waterMeterId("WM-ZERO-VALID")
                .villageName("village")
                .phone("")
                .isActive(true)
                .build());
        householdRepository.save(Household.builder()
                .householdName("zero invalid")
                .waterMeterId("WM-ZERO-INVALID")
                .villageName("village")
                .phone("")
                .isActive(true)
                .build());

        WaterBill valid = waterBill("WM-ZERO-VALID", 2026, 7, "0.00", "0.00");
        valid.setWaterAmount(BigDecimal.ZERO);
        valid.setWaterStatus("无需缴费");
        valid = waterBillRepository.save(valid);

        WaterBill invalid = waterBill("WM-ZERO-INVALID", 2026, 7, "0.00", "0.00");
        invalid.setWaterAmount(BigDecimal.ZERO);
        invalid.setWaterStatus("未收");
        invalid = waterBillRepository.save(invalid);

        List<AccountingHealthIssue> issues = accountingHealthService.check();

        Long validId = valid.getId();
        Long invalidId = invalid.getId();
        assertThat(issues)
                .filteredOn(issue -> "INCONSISTENT_WATER_BILL_STATUS".equals(issue.getType()))
                .extracting(AccountingHealthIssue::getRefId)
                .doesNotContain(validId)
                .contains(invalidId);
    }

    private WaterBill waterBill(String meterId, int year, int month, String charge, String paid) {
        return WaterBill.builder()
                .waterMeterId(meterId)
                .billYear(year)
                .billMonth(month)
                .waterAmount(new BigDecimal("1.00"))
                .waterCharge(new BigDecimal(charge))
                .actualWaterPaid(new BigDecimal(paid))
                .waterStatus("test")
                .build();
    }
}
