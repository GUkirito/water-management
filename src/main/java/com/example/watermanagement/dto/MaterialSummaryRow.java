package com.example.watermanagement.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 材料费统计表 — Excel 导出的一行数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialSummaryRow {

    @ExcelProperty("水表编号")
    private String waterMeterId;

    @ExcelProperty("户名")
    private String householdName;

    @ExcelProperty("村名")
    private String villageName;

    @ExcelProperty("应收材料费")
    private BigDecimal totalFee;

    @ExcelProperty("已缴材料费")
    private BigDecimal actualPaid;

    @ExcelProperty("欠费金额")
    private BigDecimal unpaid;

    @ExcelProperty("状态")
    private String status;
}
