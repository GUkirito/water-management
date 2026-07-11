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
public class HistoricalReadingImportRow {

    @ExcelProperty(value = "序号", index = 0)
    private Integer index;

    @ExcelProperty(value = "户名", index = 1)
    private String householdName;

    @ExcelProperty(value = "水表编号", index = 2)
    private String waterMeterId;

    @ExcelProperty(value = "村名", index = 3)
    private String villageName;

    @ExcelProperty(value = "抄表日期", index = 4)
    private String readingDate;

    @ExcelProperty(value = "上次表底", index = 5)
    private BigDecimal previousReading;

    @ExcelProperty(value = "本次表底", index = 6)
    private BigDecimal currentReading;

    @ExcelProperty(value = "水价", index = 7)
    private BigDecimal waterPrice;

    @ExcelProperty(value = "水费", index = 8)
    private BigDecimal waterCharge;
}
