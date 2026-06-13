package com.example.watermanagement.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialRecordExportRow {

    @ExcelProperty("序号")
    private Integer index;

    @ExcelProperty("户主姓名")
    private String householdName;

    @ExcelProperty("表号")
    private String waterMeterId;

    @ExcelProperty("电话号码")
    private String phone;

    @ExcelProperty("村组")
    private String villageName;

    @ExcelProperty("材料费(元)")
    private BigDecimal totalFee;

    @ExcelProperty("已缴")
    private BigDecimal actualPaid;

    @ExcelProperty("欠费")
    private BigDecimal unpaid;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("最近收费日期")
    private String paidAt;

    @ExcelProperty("收款人")
    private String collector;

    @ExcelProperty("备注")
    private String note;
}
