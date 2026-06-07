package com.example.watermanagement.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 村民信息 Excel 导出/导入 行
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HouseholdExportRow {

    @ExcelProperty("户主姓名")
    private String householdName;

    @ExcelProperty("联系电话")
    private String phone;

    @ExcelProperty("所属村名")
    private String villageName;

    @ExcelProperty("水表编号")
    private String waterMeterId;

    @ExcelProperty("材料费总额")
    private BigDecimal materialFeeTotal;
}
