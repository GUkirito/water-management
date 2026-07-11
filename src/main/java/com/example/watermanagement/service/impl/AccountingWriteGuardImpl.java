package com.example.watermanagement.service.impl;

import com.example.watermanagement.entity.Reading;
import com.example.watermanagement.entity.WaterBill;
import com.example.watermanagement.exception.BusinessException;
import com.example.watermanagement.repository.AccountingAdjustmentRepository;
import com.example.watermanagement.repository.MonthLockRepository;
import com.example.watermanagement.repository.PaymentRepository;
import com.example.watermanagement.repository.PrepaymentLogRepository;
import com.example.watermanagement.repository.ReadingRepository;
import com.example.watermanagement.service.AccountingWriteGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AccountingWriteGuardImpl implements AccountingWriteGuard {

    private final MonthLockRepository monthLockRepository;
    private final ReadingRepository readingRepository;
    private final PaymentRepository paymentRepository;
    private final PrepaymentLogRepository prepaymentLogRepository;
    private final AccountingAdjustmentRepository adjustmentRepository;

    @Override
    public void requireUnlocked(LocalDate date) {
        if (monthLockRepository.existsByBillYearAndBillMonth(date.getYear(), date.getMonthValue())) {
            throw new BusinessException("该月份已月结锁定，不能修改抄表记录，请先解锁或走调账");
        }
    }

    @Override
    public Optional<Reading> findReadingInMonth(String waterMeterId, LocalDate date) {
        LocalDate start = date.withDayOfMonth(1);
        LocalDate end = start.plusMonths(1).minusDays(1);
        return readingRepository.findByWaterMeterIdAndReadingDateBetween(waterMeterId, start, end)
                .stream()
                .min(Comparator.comparing(Reading::getReadingDate));
    }

    @Override
    public void requireNoLaterReading(String waterMeterId, LocalDate date) {
        if (readingRepository.existsByWaterMeterIdAndReadingDateAfter(waterMeterId, date)) {
            throw new BusinessException("该日期之后已有抄表记录，不能修改历史表底，请使用数据修正流程");
        }
    }

    @Override
    public void requireBillMutable(WaterBill bill) {
        BigDecimal paid = bill.getActualWaterPaid() == null ? BigDecimal.ZERO : bill.getActualWaterPaid();
        boolean hasFinancialHistory = paid.compareTo(BigDecimal.ZERO) > 0
                || paymentRepository.existsByBillTypeAndBillId("water", bill.getId())
                || prepaymentLogRepository.existsByBillId(bill.getId())
                || adjustmentRepository.existsByTargetTypeAndTargetId("WATER_BILL", bill.getId());
        if (hasFinancialHistory) {
            throw new BusinessException("该月账单已有收款、预存抵扣或调账记录，不能通过重录表底修改，请使用调账");
        }
    }

    @Override
    public void requireNonNegativeChargeableUsage(BigDecimal usage) {
        if (usage != null && usage.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("计费用水量不能为负数");
        }
    }
}
