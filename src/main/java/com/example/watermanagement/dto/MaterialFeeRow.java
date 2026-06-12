package com.example.watermanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 材料费列表行 DTO — 合并 Household + MaterialBill 信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialFeeRow {

    /** 水表编号 */
    private String waterMeterId;

    /** 户主姓名 */
    private String householdName;

    /** 所属村名 */
    private String villageName;

    /** 电话号码 */
    private String phone;

    /** 应收材料费（默认 1500） */
    private BigDecimal totalFee;

    /** 已缴金额 */
    private BigDecimal actualPaid;

    /** 欠费金额 = totalFee - actualPaid */
    private BigDecimal unpaid;

    /** 缴费状态：未收 / 部分收 / 已收 */
    private String status;

    /** 最近缴费日期 */
    private LocalDate paidAt;

    /** 收款人 */
    private String collector;
}
