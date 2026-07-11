package com.example.watermanagement.service;

import com.example.watermanagement.entity.AccountingAdjustment;
import com.example.watermanagement.entity.MonthLock;

import java.math.BigDecimal;
import java.util.List;

public interface AccountingControlService {

    MonthLock lockMonth(Integer billYear, Integer billMonth, String operator, String note);

    MonthLock unlockMonth(Integer billYear, Integer billMonth, String operator, String reason);

    boolean isMonthLocked(Integer billYear, Integer billMonth);

    List<MonthLock> listMonthLocks();

    AccountingAdjustment adjustWaterBill(Long billId, BigDecimal afterAmount, String reason, String operator);

    AccountingAdjustment adjustMaterialRecord(Long recordId, BigDecimal afterAmount, String reason, String operator);

    List<AccountingAdjustment> listAdjustments();
}
