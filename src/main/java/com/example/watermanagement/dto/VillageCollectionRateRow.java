package com.example.watermanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 各村收缴率排行 — 仪表盘用
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VillageCollectionRateRow {

    /** 村名 */
    private String villageName;

    /** 应收总额 */
    private BigDecimal totalCharge;

    /** 实收总额 */
    private BigDecimal totalPaid;

    /** 收缴率 (0.0 ~ 100.0) */
    private double collectionRate;
}
