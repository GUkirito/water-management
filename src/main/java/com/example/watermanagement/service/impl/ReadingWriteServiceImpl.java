package com.example.watermanagement.service.impl;

import com.example.watermanagement.entity.PrepaymentLog;
import com.example.watermanagement.entity.Reading;
import com.example.watermanagement.entity.WaterBill;
import com.example.watermanagement.exception.BusinessException;
import com.example.watermanagement.repository.HouseholdRepository;
import com.example.watermanagement.repository.PrepaymentLogRepository;
import com.example.watermanagement.repository.ReadingRepository;
import com.example.watermanagement.repository.WaterBillRepository;
import com.example.watermanagement.service.AccountingWriteGuard;
import com.example.watermanagement.service.ReadingWriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReadingWriteServiceImpl implements ReadingWriteService {

    private final HouseholdRepository householdRepository;
    private final ReadingRepository readingRepository;
    private final WaterBillRepository waterBillRepository;
    private final PrepaymentLogRepository prepaymentLogRepository;
    private final AccountingWriteGuard accountingWriteGuard;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reading saveRow(String waterMeterId, BigDecimal currentReading, LocalDate readingDate,
                           BigDecimal chargeableUsage, String note, BigDecimal waterPrice,
                           BigDecimal abnormalThreshold) {
        return save(waterMeterId, currentReading, readingDate, chargeableUsage, note,
                waterPrice, abnormalThreshold);
    }

    @Override
    @Transactional
    public Reading saveImportedRow(String waterMeterId, BigDecimal currentReading, LocalDate readingDate,
                                   BigDecimal chargeableUsage, String note, BigDecimal waterPrice,
                                   BigDecimal abnormalThreshold) {
        return save(waterMeterId, currentReading, readingDate, chargeableUsage, note,
                waterPrice, abnormalThreshold);
    }

    private Reading save(String waterMeterId, BigDecimal currentReading, LocalDate readingDate,
                         BigDecimal chargeableUsage, String note, BigDecimal waterPrice,
                         BigDecimal abnormalThreshold) {
        if (readingDate == null) {
            throw new BusinessException("抄表日期不能为空");
        }
        if (currentReading == null) {
            throw new BusinessException("本次表底不能为空: " + waterMeterId);
        }
        accountingWriteGuard.requireUnlocked(readingDate);
        accountingWriteGuard.requireNonNegativeChargeableUsage(chargeableUsage);
        householdRepository.findByWaterMeterId(waterMeterId)
                .orElseThrow(() -> new BusinessException("水表不存在: " + waterMeterId));

        Reading sameDay = readingRepository.findByWaterMeterIdAndReadingDate(waterMeterId, readingDate)
                .orElse(null);
        accountingWriteGuard.requireNoLaterReading(waterMeterId, readingDate);
        Reading sameMonth = accountingWriteGuard.findReadingInMonth(waterMeterId, readingDate)
                .orElse(null);
        if (sameMonth != null && !sameMonth.getReadingDate().equals(readingDate)) {
            throw new BusinessException("同月已有抄表记录: " + sameMonth.getReadingDate()
                    + "，每个自然月只能录入一条计费抄表");
        }
        BigDecimal previousReading = getLastReadingBefore(waterMeterId, readingDate);
        BigDecimal usageAmount = currentReading.subtract(previousReading);
        if (usageAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("本次表底小于上次表底: " + waterMeterId);
        }
        boolean manualChargeable = chargeableUsage != null;
        BigDecimal effectiveChargeable = manualChargeable ? chargeableUsage : usageAmount;
        accountingWriteGuard.requireNonNegativeChargeableUsage(effectiveChargeable);

        String anomaly = checkAbnormal(currentReading, previousReading, abnormalThreshold);
        boolean abnormal = anomaly != null;
        WaterBill existingBill = waterBillRepository.findByWaterMeterIdAndBillYearAndBillMonth(
                waterMeterId, readingDate.getYear(), readingDate.getMonthValue()).orElse(null);
        if (sameDay != null && existingBill != null) {
            accountingWriteGuard.requireBillMutable(existingBill);
        }

        Reading reading = Reading.builder()
                .id(sameDay == null ? null : sameDay.getId())
                .waterMeterId(waterMeterId)
                .readingDate(readingDate)
                .currentReading(currentReading)
                .previousReading(previousReading)
                .usageAmount(usageAmount)
                .isAbnormal(abnormal)
                .abnormalReason(anomaly)
                .note(note)
                .build();

        if (abnormal && !manualChargeable) {
            if (existingBill != null) {
                accountingWriteGuard.requireBillMutable(existingBill);
                waterBillRepository.delete(existingBill);
            }
            return readingRepository.save(reading);
        }

        Reading savedReading = readingRepository.save(reading);
        BigDecimal normalizedPrice = waterPrice.setScale(2, RoundingMode.HALF_UP);
        BigDecimal waterCharge = effectiveChargeable.multiply(normalizedPrice)
                .setScale(2, RoundingMode.HALF_UP);
        if (existingBill != null) {
            accountingWriteGuard.requireBillMutable(existingBill);
            BigDecimal deduction = availablePrepayment(waterMeterId, waterCharge);
            existingBill.setWaterAmount(effectiveChargeable);
            existingBill.setWaterPrice(normalizedPrice);
            existingBill.setWaterCharge(waterCharge);
            existingBill.setActualWaterPaid(deduction);
            existingBill.setWaterStatus(status(deduction, waterCharge));
            existingBill.setNote(deduction.signum() > 0 ? "已使用预存抵扣 " + deduction + " 元" : null);
            waterBillRepository.save(existingBill);
            if (deduction.signum() > 0) {
                savePrepaymentDeduction(waterMeterId, readingDate, existingBill, deduction);
            }
        } else {
            BigDecimal deduction = availablePrepayment(waterMeterId, waterCharge);
            WaterBill bill = waterBillRepository.save(WaterBill.builder()
                    .waterMeterId(waterMeterId)
                    .billYear(readingDate.getYear())
                    .billMonth(readingDate.getMonthValue())
                    .waterAmount(effectiveChargeable)
                    .waterPrice(normalizedPrice)
                    .waterCharge(waterCharge)
                    .actualWaterPaid(deduction)
                    .waterStatus(status(deduction, waterCharge))
                    .note(deduction.signum() > 0 ? "已使用预存抵扣 " + deduction + " 元" : null)
                    .build());
            if (deduction.signum() > 0) {
                savePrepaymentDeduction(waterMeterId, readingDate, bill, deduction);
            }
        }
        return savedReading;
    }

    private BigDecimal getLastReadingBefore(String waterMeterId, LocalDate date) {
        return readingRepository.findByWaterMeterIdInOrderByReadingDateDesc(List.of(waterMeterId))
                .stream()
                .filter(reading -> reading.getReadingDate().isBefore(date))
                .map(Reading::getCurrentReading)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private String checkAbnormal(BigDecimal current, BigDecimal previous, BigDecimal threshold) {
        BigDecimal usage = current.subtract(previous);
        if (usage.compareTo(threshold) > 0) {
            return "用量突增（" + usage + " 吨 > 阈值 " + threshold + " 吨）";
        }
        return null;
    }

    private BigDecimal availablePrepayment(String meterId, BigDecimal charge) {
        BigDecimal balance = prepaymentLogRepository.getBalance(meterId);
        if (balance == null || balance.signum() <= 0 || charge.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return balance.min(charge).setScale(2, RoundingMode.HALF_UP);
    }

    private String status(BigDecimal paid, BigDecimal total) {
        if (paid.signum() == 0) return "未收";
        if (paid.compareTo(total) >= 0) return "已收";
        return "部分收";
    }

    private void savePrepaymentDeduction(String waterMeterId, LocalDate readingDate,
                                         WaterBill bill, BigDecimal deduction) {
        prepaymentLogRepository.save(PrepaymentLog.builder()
                .waterMeterId(waterMeterId)
                .amount(deduction.negate())
                .type("AUTO_DEDUCT")
                .billId(bill.getId())
                .remark(readingDate.getYear() + "年" + readingDate.getMonthValue()
                        + "月水费自动抵扣预存 " + deduction + " 元")
                .build());
    }
}
