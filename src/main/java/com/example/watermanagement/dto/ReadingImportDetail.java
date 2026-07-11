package com.example.watermanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReadingImportDetail {
    private String type;
    private Integer index;
    private String householdName;
    private String waterMeterId;
    private String villageName;
    private LocalDate readingDate;
    private BigDecimal previousReading;
    private BigDecimal currentReading;
    private BigDecimal usageAmount;
    private BigDecimal waterPrice;
    private BigDecimal waterCharge;
    private String status;
    private String message;
}
