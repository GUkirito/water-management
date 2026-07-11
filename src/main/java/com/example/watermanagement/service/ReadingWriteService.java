package com.example.watermanagement.service;

import com.example.watermanagement.entity.Reading;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface ReadingWriteService {

    Reading saveRow(String waterMeterId, BigDecimal currentReading, LocalDate readingDate,
                    BigDecimal chargeableUsage, String note, BigDecimal waterPrice,
                    BigDecimal abnormalThreshold);

    Reading saveImportedRow(String waterMeterId, BigDecimal currentReading, LocalDate readingDate,
                            BigDecimal chargeableUsage, String note, BigDecimal waterPrice,
                            BigDecimal abnormalThreshold);
}
