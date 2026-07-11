package com.example.watermanagement.service;

import com.example.watermanagement.entity.Reading;
import com.example.watermanagement.entity.WaterBill;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface AccountingWriteGuard {

    void requireUnlocked(LocalDate date);

    Optional<Reading> findReadingInMonth(String waterMeterId, LocalDate date);

    void requireNoLaterReading(String waterMeterId, LocalDate date);

    void requireBillMutable(WaterBill bill);

    void requireNonNegativeChargeableUsage(BigDecimal usage);
}
