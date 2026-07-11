package com.example.watermanagement.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AccountingAdjustmentRequest {
    private BigDecimal afterAmount;
    private String reason;
    private String operator;
}
