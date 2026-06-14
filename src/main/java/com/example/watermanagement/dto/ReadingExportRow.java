package com.example.watermanagement.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 抄表 Excel 模板 / 导入时的一行数据
 * <p>
 * 导出为空白模板时，本次表底为空；抄表员填写后导入。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReadingExportRow {

    /** 水表编号 */
    @ExcelProperty("水表编号")
    private String waterMeterId;

    /** 户名 */
    @ExcelProperty("户名")
    private String householdName;

    /** 所属村名 */
    @ExcelProperty("村名")
    private String villageName;

    /** 上次表底（上月读数） */
    @ExcelProperty("上次表底")
    private BigDecimal previousReading;

    /** 本次表底（抄表员填写） */
    @ExcelProperty("本次表底")
    private BigDecimal currentReading;

    /** 水价（元/吨） */
    @ExcelProperty("水价")
    private BigDecimal waterPrice;

    /** 水费（用水量 x 水价，模板中为空） */
    @ExcelProperty("水费")
    private BigDecimal waterCharge;
}
