package com.example.watermanagement.service;

import com.example.watermanagement.dto.ReadingBatchItem;
import com.example.watermanagement.entity.Household;
import com.example.watermanagement.entity.Reading;
import com.example.watermanagement.entity.WaterBill;
import com.example.watermanagement.exception.BusinessException;
import com.example.watermanagement.repository.AccountingAdjustmentRepository;
import com.example.watermanagement.repository.HouseholdRepository;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ReadingConsistencyTests {

    @Autowired
    private ReadingService readingService;
    @Autowired
    private HouseholdRepository householdRepository;
    @Autowired
    private ReadingRepository readingRepository;
    @Autowired
    private WaterBillRepository waterBillRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private PrepaymentLogRepository prepaymentLogRepository;
    @Autowired
    private AccountingAdjustmentRepository adjustmentRepository;
    @Autowired
    private MonthLockRepository monthLockRepository;

    @BeforeEach
    void clearData() {
        paymentRepository.deleteAll();
        prepaymentLogRepository.deleteAll();
        adjustmentRepository.deleteAll();
        waterBillRepository.deleteAll();
        readingRepository.deleteAll();
        monthLockRepository.deleteAll();
        householdRepository.deleteAll();
    }

    @Test
    void earlierReadingIsRejectedWhenLaterReadingExists() {
        household("M-LATER");
        readingRepository.save(reading("M-LATER", "2026-07-20", "20"));

        assertThatThrownBy(() -> readingService.singleSave(
                "M-LATER", bd("10"), LocalDate.of(2026, 7, 10)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("之后已有抄表");
        assertThat(readingRepository.findAll()).hasSize(1);
    }

    @Test
    void secondReadingOnDifferentDayInSameMonthIsRejected() {
        household("M-MONTH");
        readingService.singleSave("M-MONTH", bd("10"), LocalDate.of(2026, 7, 5));

        assertThatThrownBy(() -> readingService.singleSave(
                "M-MONTH", bd("12"), LocalDate.of(2026, 7, 18)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("同月已有抄表")
                .hasMessageContaining("2026-07-05");
        assertThat(readingRepository.findAll()).hasSize(1);
    }

    @Test
    void paidBillCannotBeRepricedBySameDayReadingUpdate() {
        household("M-PAID");
        readingService.singleSave("M-PAID", bd("10"), LocalDate.of(2026, 7, 5));
        WaterBill bill = waterBillRepository.findByWaterMeterIdAndBillYearAndBillMonth(
                "M-PAID", 2026, 7).orElseThrow();
        bill.setActualWaterPaid(bd("18"));
        bill.setWaterStatus("已收");
        waterBillRepository.saveAndFlush(bill);

        assertThatThrownBy(() -> readingService.singleSave(
                "M-PAID", bd("12"), LocalDate.of(2026, 7, 5)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有收款");

        WaterBill unchanged = waterBillRepository.findById(bill.getId()).orElseThrow();
        assertThat(unchanged.getWaterCharge()).isEqualByComparingTo("18.00");
        assertThat(readingRepository.findByWaterMeterIdAndReadingDate(
                "M-PAID", LocalDate.of(2026, 7, 5)).orElseThrow().getCurrentReading())
                .isEqualByComparingTo("10.00");
    }

    @Test
    void negativeChargeableUsageFailsWithoutWritingAnyRow() {
        household("M-NEGATIVE");
        ReadingBatchItem item = new ReadingBatchItem();
        item.setWaterMeterId("M-NEGATIVE");
        item.setCurrentReading(bd("10"));
        item.setChargeableUsage(bd("-1"));

        Map<String, Object> result = readingService.batchSave(
                List.of(item), LocalDate.of(2026, 7, 5));

        assertThat(result.get("total")).isEqualTo(0);
        assertThat(result.get("fail")).isEqualTo(1);
        assertThat(readingRepository.findAll()).isEmpty();
        assertThat(waterBillRepository.findAll()).isEmpty();
    }

    @Test
    void changingNormalReadingToAbnormalRemovesUnpaidBill() {
        household("M-ABNORMAL");
        LocalDate date = LocalDate.of(2026, 7, 5);
        readingService.singleSave("M-ABNORMAL", bd("10"), date);
        assertThat(waterBillRepository.findByWaterMeterId("M-ABNORMAL")).hasSize(1);

        Reading changed = readingService.singleSave("M-ABNORMAL", bd("200"), date);

        assertThat(changed.getIsAbnormal()).isTrue();
        assertThat(waterBillRepository.findByWaterMeterId("M-ABNORMAL")).isEmpty();
    }

    @Test
    void sameDayUnpaidReadingUpdateKeepsOriginalBillPriceSnapshot() {
        household("M-PRICE");
        LocalDate date = LocalDate.of(2026, 7, 5);
        readingService.singleSave("M-PRICE", bd("10"), date);
        readingService.singleSave("M-PRICE", bd("12"), date);

        WaterBill bill = waterBillRepository.findByWaterMeterIdAndBillYearAndBillMonth(
                "M-PRICE", 2026, 7).orElseThrow();
        assertThat(bill.getWaterPrice()).isEqualByComparingTo("1.80");
        assertThat(bill.getWaterCharge()).isEqualByComparingTo("21.60");
    }

    private void household(String meterId) {
        householdRepository.save(Household.builder()
                .householdName("测试户")
                .waterMeterId(meterId)
                .villageName("测试村")
                .phone("")
                .isActive(true)
                .build());
    }

    private Reading reading(String meterId, String date, String current) {
        return Reading.builder()
                .waterMeterId(meterId)
                .readingDate(LocalDate.parse(date))
                .previousReading(BigDecimal.ZERO)
                .currentReading(bd(current))
                .usageAmount(bd(current))
                .isAbnormal(false)
                .build();
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
