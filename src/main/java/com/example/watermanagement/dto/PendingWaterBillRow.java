package com.example.watermanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 缴费管理页的未缴水费账单行。
 */
@Data
@AllArgsConstructor
public class PendingWaterBillRow {

    private Long id;
    private String waterMeterId;
    private String householdName;
    private String villageName;
    private Integer billYear;
    private Integer billMonth;
    private BigDecimal waterAmount;
    private BigDecimal waterCharge;
    private BigDecimal actualWaterPaid;
    private BigDecimal dueAmount;
    private String waterStatus;
    private String note;
    private Boolean householdActive;
}
