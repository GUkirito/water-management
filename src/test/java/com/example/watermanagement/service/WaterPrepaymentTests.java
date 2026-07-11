package com.example.watermanagement.service;

import com.example.watermanagement.dto.PaymentRequest;
import com.example.watermanagement.entity.Household;
import com.example.watermanagement.entity.PrepaymentLog;
import com.example.watermanagement.entity.WaterBill;
import com.example.watermanagement.repository.HouseholdRepository;
import com.example.watermanagement.repository.PaymentRepository;
import com.example.watermanagement.repository.PrepaymentLogRepository;
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
class WaterPrepaymentTests {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ReadingService readingService;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private WaterBillRepository waterBillRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PrepaymentLogRepository prepaymentLogRepository;

    @BeforeEach
    void cleanDatabase() {
        prepaymentLogRepository.deleteAll();
        paymentRepository.deleteAll();
        waterBillRepository.deleteAll();
        householdRepository.deleteAll();
    }

    @Test
    void waterOverpaymentIsStoredAsWaterOnlyPrepayment() {
        WaterBill savedBill = waterBillRepository.save(WaterBill.builder()
                .waterMeterId("WM-PRE-001")
                .billYear(2026)
                .billMonth(6)
                .waterAmount(new BigDecimal("1.89"))
                .waterCharge(new BigDecimal("3.40"))
                .actualWaterPaid(BigDecimal.ZERO)
                .waterStatus("未收")
                .build());

        PaymentRequest request = waterPayment(List.of(savedBill.getId()), new BigDecimal("5.00"));
        request.setNote("现金收款");

        paymentService.pay(request);

        WaterBill bill = waterBillRepository.findById(savedBill.getId()).orElseThrow();
        assertThat(bill.getActualWaterPaid()).isEqualByComparingTo(new BigDecimal("3.40"));
        assertThat(bill.getWaterStatus()).isEqualTo("已收");
        assertThat(prepaymentLogRepository.getBalance("WM-PRE-001"))
                .isEqualByComparingTo(new BigDecimal("1.60"));

        List<PrepaymentLog> logs = prepaymentLogRepository.findByWaterMeterIdOrderByCreatedAtDesc("WM-PRE-001");
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("1.60"));
        assertThat(logs.get(0).getType()).isEqualTo("OVERPAYMENT");
        assertThat(logs.get(0).getRemark()).contains("预存 1.60 元");
    }

    @Test
    void nextReadingAutomaticallyAppliesPrepaymentAndLeavesRemark() {
        householdRepository.save(Household.builder()
                .householdName("预存测试户")
                .waterMeterId("WM-PRE-002")
                .villageName("测试村")
                .phone("")
                .isActive(true)
                .build());
        prepaymentLogRepository.save(PrepaymentLog.builder()
                .waterMeterId("WM-PRE-002")
                .amount(new BigDecimal("1.60"))
                .type("OVERPAYMENT")
                .remark("历史现金多收转预存 1.60 元")
                .build());

        readingService.singleSave("WM-PRE-002", new BigDecimal("2.333"), LocalDate.of(2026, 7, 1));

        WaterBill bill = waterBillRepository
                .findByWaterMeterIdAndBillYearAndBillMonth("WM-PRE-002", 2026, 7)
                .orElseThrow();
        assertThat(bill.getWaterCharge()).isEqualByComparingTo(new BigDecimal("4.20"));
        assertThat(bill.getActualWaterPaid()).isEqualByComparingTo(new BigDecimal("1.60"));
        assertThat(bill.getWaterStatus()).isEqualTo("部分收");
        assertThat(bill.getNote()).contains("已使用预存抵扣 1.60 元");
        assertThat(prepaymentLogRepository.getBalance("WM-PRE-002")).isEqualByComparingTo(BigDecimal.ZERO);

        List<PrepaymentLog> logs = prepaymentLogRepository.findByWaterMeterIdOrderByCreatedAtDesc("WM-PRE-002");
        assertThat(logs.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("-1.60"));
        assertThat(logs.get(0).getType()).isEqualTo("AUTO_DEDUCT");
        assertThat(logs.get(0).getRemark()).contains("2026年7月水费");
    }

    @Test
    void updatedBillAppliesAvailablePrepaymentToNewDueAmount() {
        householdRepository.save(Household.builder()
                .householdName("owner")
                .waterMeterId("WM-PRE-UPDATE")
                .villageName("village")
                .phone("")
                .isActive(true)
                .build());
        waterBillRepository.save(WaterBill.builder()
                .waterMeterId("WM-PRE-UPDATE")
                .billYear(2026)
                .billMonth(7)
                .waterAmount(new BigDecimal("2.00"))
                .waterCharge(new BigDecimal("3.60"))
                .actualWaterPaid(BigDecimal.ZERO)
                .waterStatus("open")
                .build());
        prepaymentLogRepository.save(PrepaymentLog.builder()
                .waterMeterId("WM-PRE-UPDATE")
                .amount(new BigDecimal("5.00"))
                .type("OVERPAYMENT")
                .remark("prepayment")
                .build());

        readingService.singleSave("WM-PRE-UPDATE", new BigDecimal("4.00"), LocalDate.of(2026, 7, 1));

        WaterBill bill = waterBillRepository
                .findByWaterMeterIdAndBillYearAndBillMonth("WM-PRE-UPDATE", 2026, 7)
                .orElseThrow();
        assertThat(bill.getWaterCharge()).isEqualByComparingTo(new BigDecimal("7.20"));
        assertThat(bill.getActualWaterPaid()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(prepaymentLogRepository.getBalance("WM-PRE-UPDATE")).isEqualByComparingTo(BigDecimal.ZERO);
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
}
