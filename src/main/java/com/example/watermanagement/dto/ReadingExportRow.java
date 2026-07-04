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

    /** 序号 */
    @ExcelProperty(value = "序号", index = 0)
    private Integer index;

    /** 户名 */
    @ExcelProperty(value = "户名", index = 1)
    private String householdName;

    /** 水表编号 */
    @ExcelProperty(value = "水表编号", index = 2)
    private String waterMeterId;

    /** 所属村名 */
    @ExcelProperty(value = "村名", index = 3)
    private String villageName;

    /** 上次表底（上月读数） */
    @ExcelProperty(value = "上次表底", index = 4)
    private BigDecimal previousReading;

    /** 本次表底（抄表员填写） */
    @ExcelProperty(value = "本次表底", index = 5)
    private BigDecimal currentReading;

    /** 水价（元/吨） */
    @ExcelProperty(value = "水价", index = 6)
    private BigDecimal waterPrice;

    /** 水费（用水量 x 水价，模板中为空） */
    @ExcelProperty(value = "水费", index = 7)
    private BigDecimal waterCharge;
}
