package com.example.watermanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 住户删除或停用结果及其保留的业务历史摘要。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HouseholdRemovalResult {

    private String action;
    private Long householdId;
    private String waterMeterId;
    private long readingCount;
    private long billCount;
    private long paymentCount;
    private long prepaymentCount;
    private long materialRecordCount;
    private long materialPaymentCount;

    /** 仅统计水费账单的未收金额，不包含材料费。 */
    private BigDecimal outstandingAmount;
}
