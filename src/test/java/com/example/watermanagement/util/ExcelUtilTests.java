package com.example.watermanagement.util;

import com.example.watermanagement.dto.HistoricalReadingImportRow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelUtilTests {

    @Test
    void exportAddsHeaderTipsAndColumnFormats() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ExcelUtil.export(response, "历史抄表模板", HistoricalReadingImportRow.class, List.of(),
                Map.of(4, "抄表日期：必填，格式为 2026-07-04。"),
                Map.of(4, "yyyy-mm-dd"));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet = workbook.getSheetAt(0);
            var dateHeader = sheet.getRow(0).getCell(4);

            assertThat(dateHeader.getCellComment().getString().getString())
                    .contains("2026-07-04");
            assertThat(sheet.getColumnStyle(4).getDataFormatString())
                    .isEqualTo("yyyy-mm-dd");
        }
    }

    @Test
    void readPropagatesRowParseExceptions() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("sheet1");
            var header = sheet.createRow(0);
            String[] headers = {"序号", "户名", "水表编号", "村名", "抄表日期", "上次表底", "本次表底", "水价", "水费"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue(1);
            row.createCell(1).setCellValue("测试户");
            row.createCell(2).setCellValue("WM-BAD-XLSX");
            row.createCell(3).setCellValue("测试村");
            row.createCell(4).setCellValue("2026-07-05");
            row.createCell(5).setCellValue("not-a-number");
            workbook.write(out);
        }

        assertThatThrownBy(() -> ExcelUtil.read(
                new ByteArrayInputStream(out.toByteArray()), HistoricalReadingImportRow.class))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Excel 解析失败");
    }
}
