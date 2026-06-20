package com.example.watermanagement.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 水费月报表 — Excel 导出的一行数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaterBillReportRow {

    @ExcelProperty("村名")
    private String villageName;

    @ExcelProperty("水表编号")
    private String waterMeterId;

    @ExcelProperty("户名")
    private String householdName;

    @ExcelProperty("用水量(吨)")
    private BigDecimal waterAmount;

    @ExcelProperty("应收水费")
    private BigDecimal waterCharge;

    @ExcelProperty("实收水费")
    private BigDecimal actualWaterPaid;

    @ExcelProperty("状态")
    private String waterStatus;

    @ExcelProperty("备注")
    private String note;
}
