package com.example.watermanagement.service.impl;

import com.example.watermanagement.entity.AccountingAdjustment;
import com.example.watermanagement.entity.MaterialRecord;
import com.example.watermanagement.entity.MonthLock;
import com.example.watermanagement.entity.WaterBill;
import com.example.watermanagement.exception.BusinessException;
import com.example.watermanagement.repository.AccountingAdjustmentRepository;
import com.example.watermanagement.repository.MaterialRecordRepository;
import com.example.watermanagement.repository.MonthLockRepository;
import com.example.watermanagement.repository.WaterBillRepository;
import com.example.watermanagement.service.AccountingControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountingControlServiceImpl implements AccountingControlService {

    private final MonthLockRepository monthLockRepository;
    private final AccountingAdjustmentRepository adjustmentRepository;
    private final WaterBillRepository waterBillRepository;
    private final MaterialRecordRepository materialRecordRepository;

    @Override
    @Transactional
    public MonthLock lockMonth(Integer billYear, Integer billMonth, String operator, String note) {
        validateMonth(billYear, billMonth);
        MonthLock lock = monthLockRepository.findByBillYearAndBillMonth(billYear, billMonth)
                .orElseGet(() -> MonthLock.builder()
                        .billYear(billYear)
                        .billMonth(billMonth)
                        .build());
        lock.setLocked(true);
        lock.setOperator(operator);
        lock.setNote(note);
        lock.setLockedAt(LocalDateTime.now());
        return monthLockRepository.save(lock);
    }

    @Override
    @Transactional
    public MonthLock unlockMonth(Integer billYear, Integer billMonth, String operator, String reason) {
        validateMonth(billYear, billMonth);
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("解除月结锁定必须填写原因");
        }
        MonthLock lock = monthLockRepository.findByBillYearAndBillMonth(billYear, billMonth)
                .orElseThrow(() -> new BusinessException("该月份未锁定"));
        lock.setLocked(false);
        lock.setUnlockedAt(LocalDateTime.now());
        lock.setUnlockedBy(operator);
        lock.setUnlockReason(reason.trim());
        return monthLockRepository.save(lock);
    }

    @Override
    public boolean isMonthLocked(Integer billYear, Integer billMonth) {
        return monthLockRepository.existsByBillYearAndBillMonth(billYear, billMonth);
    }

    @Override
    public List<MonthLock> listMonthLocks() {
        return monthLockRepository.findAllByOrderByBillYearDescBillMonthDesc();
    }

    @Override
    @Transactional
    public AccountingAdjustment adjustWaterBill(Long billId, BigDecimal afterAmount, String reason, String operator) {
        validateAdjustment(afterAmount, reason);
        WaterBill bill = waterBillRepository.findById(billId)
                .orElseThrow(() -> new BusinessException("水费账单不存在: " + billId));
        if (monthLockRepository.existsByBillYearAndBillMonth(bill.getBillYear(), bill.getBillMonth())) {
            throw new BusinessException("该月份已月结锁定，不能调账，请先按流程解锁");
        }
        if (afterAmount.compareTo(bill.getActualWaterPaid()) < 0) {
            throw new BusinessException("调账后金额不能小于已收金额");
        }
        BigDecimal before = bill.getWaterCharge();
        bill.setWaterCharge(afterAmount);
        bill.setWaterStatus(calcStatus(bill.getActualWaterPaid(), afterAmount));
        waterBillRepository.save(bill);
        return saveAdjustment("WATER_BILL", billId, before, afterAmount, reason, operator);
    }

    @Override
    @Transactional
    public AccountingAdjustment adjustMaterialRecord(Long recordId, BigDecimal afterAmount, String reason, String operator) {
        validateAdjustment(afterAmount, reason);
        MaterialRecord record = materialRecordRepository.findById(recordId)
                .orElseThrow(() -> new BusinessException("材料费记录不存在: " + recordId));
        if (afterAmount.compareTo(record.getActualPaid()) < 0) {
            throw new BusinessException("调账后金额不能小于已收金额");
        }
        BigDecimal before = record.getTotalFee();
        record.setTotalFee(afterAmount);
        record.setStatus(calcStatus(record.getActualPaid(), afterAmount));
        materialRecordRepository.save(record);
        return saveAdjustment("MATERIAL_RECORD", recordId, before, afterAmount, reason, operator);
    }

    @Override
    public List<AccountingAdjustment> listAdjustments() {
        return adjustmentRepository.findAllByOrderByCreatedAtDesc();
    }

    private AccountingAdjustment saveAdjustment(String targetType, Long targetId, BigDecimal before,
                                                BigDecimal after, String reason, String operator) {
        return adjustmentRepository.save(AccountingAdjustment.builder()
                .targetType(targetType)
                .targetId(targetId)
                .beforeAmount(before)
                .afterAmount(after)
                .reason(reason.trim())
                .operator(operator)
                .build());
    }

    private void validateMonth(Integer billYear, Integer billMonth) {
        if (billYear == null || billMonth == null || billMonth < 1 || billMonth > 12) {
            throw new BusinessException("请选择有效的月结年月");
        }
    }

    private void validateAdjustment(BigDecimal afterAmount, String reason) {
        if (afterAmount == null || afterAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("调账金额不能小于 0");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("调账必须填写原因");
        }
    }

    private String calcStatus(BigDecimal paid, BigDecimal total) {
        BigDecimal actualPaid = paid == null ? BigDecimal.ZERO : paid;
        if (actualPaid.compareTo(BigDecimal.ZERO) <= 0) return "未收";
        if (actualPaid.compareTo(total) >= 0) return "已收";
        return "部分收";
    }
}
