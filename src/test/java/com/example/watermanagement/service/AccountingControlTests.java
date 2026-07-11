package com.example.watermanagement.service;

import com.example.watermanagement.dto.ReadingBatchItem;
import com.example.watermanagement.entity.AccountingAdjustment;
import com.example.watermanagement.entity.Household;
import com.example.watermanagement.entity.MaterialRecord;
import com.example.watermanagement.entity.MonthLock;
import com.example.watermanagement.entity.WaterBill;
import com.example.watermanagement.exception.BusinessException;
import com.example.watermanagement.repository.AccountingAdjustmentRepository;
import com.example.watermanagement.repository.HouseholdRepository;
import com.example.watermanagement.repository.MaterialRecordRepository;
import com.example.watermanagement.repository.MonthLockRepository;
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
class AccountingControlTests {

    @Autowired
    private AccountingControlService accountingControlService;

    @Autowired
    private ReadingService readingService;

    @Autowired
    private AccountingAdjustmentRepository adjustmentRepository;

    @Autowired
    private MonthLockRepository monthLockRepository;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private ReadingRepository readingRepository;

    @Autowired
    private WaterBillRepository waterBillRepository;

    @Autowired
    private MaterialRecordRepository materialRecordRepository;

    @BeforeEach
    void cleanDatabase() {
        adjustmentRepository.deleteAll();
        monthLockRepository.deleteAll();
        readingRepository.deleteAll();
        waterBillRepository.deleteAll();
        materialRecordRepository.deleteAll();
        householdRepository.deleteAll();
    }

    @Test
    void lockedMonthRejectsReadingSave() {
        householdRepository.save(Household.builder()
                .householdName("owner")
                .waterMeterId("WM-LOCKED")
                .villageName("village")
                .phone("")
                .isActive(true)
                .build());
        accountingControlService.lockMonth(2026, 6, "tester", "月结完成");

        assertThatThrownBy(() -> readingService.singleSave(
                "WM-LOCKED", new BigDecimal("10.00"), LocalDate.of(2026, 6, 30)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("月结锁定");
        assertThat(readingRepository.count()).isZero();
        assertThat(waterBillRepository.count()).isZero();
    }

    @Test
    void lockedMonthRejectsBatchReadingSave() {
        householdRepository.save(Household.builder()
                .householdName("owner")
                .waterMeterId("WM-LOCKED-BATCH")
                .villageName("village")
                .phone("")
                .isActive(true)
                .build());
        accountingControlService.lockMonth(2026, 6, "tester", "月结完成");
        ReadingBatchItem item = new ReadingBatchItem();
        item.setWaterMeterId("WM-LOCKED-BATCH");
        item.setCurrentReading(new BigDecimal("10.00"));

        Map<String, Object> result = readingService.batchSave(
                List.of(item), LocalDate.of(2026, 6, 30));

        assertThat(result.get("total")).isEqualTo(0);
        assertThat(result.get("fail")).isEqualTo(1);
        assertThat(readingRepository.count()).isZero();
        assertThat(waterBillRepository.count()).isZero();
    }

    @Test
    void lockedMonthDoesNotAffectDifferentMonth() {
        householdRepository.save(Household.builder()
                .householdName("owner")
                .waterMeterId("WM-OTHER-MONTH")
                .villageName("village")
                .phone("")
                .isActive(true)
                .build());
        accountingControlService.lockMonth(2026, 6, "tester", "月结完成");

        readingService.singleSave(
                "WM-OTHER-MONTH", new BigDecimal("10.00"), LocalDate.of(2026, 7, 31));

        assertThat(readingRepository.count()).isEqualTo(1);
        assertThat(waterBillRepository.count()).isEqualTo(1);
    }

    @Test
    void unlockRequiresReasonAndKeepsRecord() {
        accountingControlService.lockMonth(2026, 6, "tester", "月结完成");

        assertThatThrownBy(() -> accountingControlService.unlockMonth(
                2026, 6, "tester", ""))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("原因");

        MonthLock unlocked = accountingControlService.unlockMonth(
                2026, 6, "tester", "批准补录");

        assertThat(unlocked.getLocked()).isFalse();
        assertThat(unlocked.getUnlockReason()).isEqualTo("批准补录");
        assertThat(unlocked.getUnlockedBy()).isEqualTo("tester");
        assertThat(unlocked.getUnlockedAt()).isNotNull();
        assertThat(monthLockRepository.findByBillYearAndBillMonth(2026, 6)).isPresent();
        assertThat(accountingControlService.isMonthLocked(2026, 6)).isFalse();
    }

    @Test
    void unlockedMonthAllowsReadingSave() {
        householdRepository.save(Household.builder()
                .householdName("owner")
                .waterMeterId("WM-UNLOCKED")
                .villageName("village")
                .phone("")
                .isActive(true)
                .build());
        accountingControlService.lockMonth(2026, 6, "tester", "月结完成");
        accountingControlService.unlockMonth(2026, 6, "tester", "批准补录");

        readingService.singleSave(
                "WM-UNLOCKED", new BigDecimal("10.00"), LocalDate.of(2026, 6, 30));

        assertThat(readingRepository.count()).isEqualTo(1);
        assertThat(waterBillRepository.count()).isEqualTo(1);
    }

    @Test
    void lockedMonthRejectsWaterBillAdjustment() {
        WaterBill bill = waterBillRepository.save(WaterBill.builder()
                .waterMeterId("WM-LOCKED-ADJUST")
                .billYear(2026)
                .billMonth(6)
                .waterAmount(new BigDecimal("10.00"))
                .waterCharge(new BigDecimal("18.00"))
                .actualWaterPaid(BigDecimal.ZERO)
                .waterStatus("未收")
                .build());
        accountingControlService.lockMonth(2026, 6, "tester", "月结完成");

        assertThatThrownBy(() -> accountingControlService.adjustWaterBill(
                bill.getId(), new BigDecimal("20.00"), "补录", "tester"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("月结锁定");
        assertThat(adjustmentRepository.count()).isZero();
    }

    @Test
    void waterBillAdjustmentChangesAmountAndWritesRecord() {
        WaterBill bill = waterBillRepository.save(WaterBill.builder()
                .waterMeterId("WM-ADJ-WATER")
                .billYear(2026)
                .billMonth(6)
                .waterAmount(new BigDecimal("10.00"))
                .waterCharge(new BigDecimal("18.00"))
                .actualWaterPaid(BigDecimal.ZERO)
                .waterStatus("未收")
                .build());

        AccountingAdjustment adjustment = accountingControlService.adjustWaterBill(
                bill.getId(), new BigDecimal("20.00"), "补录漏计", "tester");

        WaterBill reloaded = waterBillRepository.findById(bill.getId()).orElseThrow();
        assertThat(reloaded.getWaterCharge()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(adjustment.getTargetType()).isEqualTo("WATER_BILL");
        assertThat(adjustment.getBeforeAmount()).isEqualByComparingTo(new BigDecimal("18.00"));
        assertThat(adjustment.getAfterAmount()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(adjustmentRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc("WATER_BILL", bill.getId()))
                .hasSize(1);
    }

    @Test
    void waterBillAdjustmentCannotReduceBelowActualPaid() {
        WaterBill bill = waterBillRepository.save(WaterBill.builder()
                .waterMeterId("WM-ADJ-WATER-PAID")
                .billYear(2026)
                .billMonth(6)
                .waterAmount(new BigDecimal("10.00"))
                .waterCharge(new BigDecimal("18.00"))
                .actualWaterPaid(new BigDecimal("10.00"))
                .waterStatus("部分收")
                .build());

        assertThatThrownBy(() -> accountingControlService.adjustWaterBill(
                bill.getId(), new BigDecimal("9.00"), "减免", "tester"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能小于已收金额");
        assertThat(adjustmentRepository.count()).isZero();
    }

    @Test
    void materialRecordAdjustmentChangesAmountAndWritesRecord() {
        MaterialRecord record = materialRecordRepository.save(MaterialRecord.builder()
                .householdName("owner")
                .waterMeterId("WM-ADJ-MAT")
                .villageName("village")
                .totalFee(new BigDecimal("1500.00"))
                .actualPaid(BigDecimal.ZERO)
                .status("未收")
                .build());

        AccountingAdjustment adjustment = accountingControlService.adjustMaterialRecord(
                record.getId(), new BigDecimal("1600.00"), "材料补差", "tester");

        MaterialRecord reloaded = materialRecordRepository.findById(record.getId()).orElseThrow();
        assertThat(reloaded.getTotalFee()).isEqualByComparingTo(new BigDecimal("1600.00"));
        assertThat(adjustment.getTargetType()).isEqualTo("MATERIAL_RECORD");
        assertThat(adjustment.getBeforeAmount()).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(adjustment.getAfterAmount()).isEqualByComparingTo(new BigDecimal("1600.00"));
        assertThat(adjustmentRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc("MATERIAL_RECORD", record.getId()))
                .hasSize(1);
    }
}
