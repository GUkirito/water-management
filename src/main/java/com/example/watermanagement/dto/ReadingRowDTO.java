package com.example.watermanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 抄表行 DTO — 用于前端表格展示，合并了 Household + Reading + WaterBill 信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReadingRowDTO {

    /** 水表编号（用于前端与 household 数据匹配） */
    private String waterMeterId;

    /** 上次表底（最近一次抄表的 currentReading） */
    private BigDecimal previousReading;

    /** 本次表底（当日已保存的读数，null 表示未录入） */
    private BigDecimal currentReading;

    /** 计费用水量（从 WaterBill.waterAmount 读取，null 表示未录入） */
    private BigDecimal chargeableUsage;

    /** 实际用水量 = currentReading - previousReading */
    private BigDecimal usageAmount;

    /** 是否异常 */
    private Boolean isAbnormal;

    /** 异常原因 */
    private String abnormalReason;

    /** 备注 */
    private String note;
}
