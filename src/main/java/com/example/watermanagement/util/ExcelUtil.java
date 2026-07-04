package com.example.watermanagement.util;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.metadata.Head;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.read.listener.ReadListener;
import com.alibaba.excel.write.handler.CellWriteHandler;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.metadata.holder.WriteTableHolder;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Sheet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel 导入导出工具类（基于 EasyExcel）
 */
@Slf4j
public class ExcelUtil {

    /**
     * 导出 Excel 到 HTTP 响应（浏览器下载）
     *
     * @param response HTTP 响应
     * @param fileName 文件名（不含扩展名）
     * @param head     表头对应的数据类（用 @ExcelProperty 标注）
     * @param data     数据列表
     */
    public static <T> void export(HttpServletResponse response,
                                   String fileName,
                                   Class<T> head,
                                   List<T> data) throws IOException {
        export(response, fileName, head, data, Map.of(), Map.of());
    }

    public static <T> void export(HttpServletResponse response,
                                   String fileName,
                                   Class<T> head,
                                   List<T> data,
                                   Map<Integer, String> columnTips,
                                   Map<Integer, String> columnFormats) throws IOException {
        // 设置响应头
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition",
                "attachment;filename=" + encodedName + ".xlsx");

        try (OutputStream out = response.getOutputStream()) {
            EasyExcel.write(out, head)
                    .registerWriteHandler(new TemplateHintWriteHandler(columnTips, columnFormats))
                    .sheet("sheet1")
                    .doWrite(data);
        }
    }

    /**
     * 从 InputStream 读取 Excel 并逐行解析
     *
     * @param inputStream Excel 文件输入流
     * @param head        表头对应的数据类
     * @return 解析后的数据列表
     */
    public static <T> List<T> read(InputStream inputStream, Class<T> head) {
        List<T> result = new ArrayList<>();
        EasyExcel.read(inputStream, head, new ReadListener<T>() {
            @Override
            public void invoke(T data, AnalysisContext context) {
                result.add(data);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                log.info("Excel 读取完成，共 {} 条数据", result.size());
            }

            @Override
            public void onException(Exception e, AnalysisContext context) {
                log.error("Excel 解析异常: {}", e.getMessage(), e);
            }
        }).sheet().doRead();
        return result;
    }

    private static class TemplateHintWriteHandler implements CellWriteHandler {
        private final Map<Integer, String> columnTips;
        private final Map<Integer, String> columnFormats;
        private final Map<String, CellStyle> styles = new HashMap<>();

        TemplateHintWriteHandler(Map<Integer, String> columnTips, Map<Integer, String> columnFormats) {
            this.columnTips = columnTips == null ? Map.of() : columnTips;
            this.columnFormats = columnFormats == null ? Map.of() : columnFormats;
        }

        @Override
        public void afterCellDispose(WriteSheetHolder writeSheetHolder,
                                     WriteTableHolder writeTableHolder,
                                     List<WriteCellData<?>> cellDataList,
                                     Cell cell,
                                     Head head,
                                     Integer relativeRowIndex,
                                     Boolean isHead) {
            int columnIndex = cell.getColumnIndex();
            if (Boolean.TRUE.equals(isHead)) {
                addHeaderTip(cell, columnTips.get(columnIndex));
                applyDefaultColumnFormat(cell, columnFormats.get(columnIndex));
                return;
            }
            applyCellFormat(cell, columnFormats.get(columnIndex));
        }

        private void addHeaderTip(Cell cell, String tip) {
            if (tip == null || tip.isBlank()) {
                return;
            }
            Sheet sheet = cell.getSheet();
            CreationHelper helper = sheet.getWorkbook().getCreationHelper();
            Drawing<?> drawing = sheet.createDrawingPatriarch();
            ClientAnchor anchor = helper.createClientAnchor();
            anchor.setCol1(cell.getColumnIndex());
            anchor.setCol2(cell.getColumnIndex() + 4);
            anchor.setRow1(cell.getRowIndex());
            anchor.setRow2(cell.getRowIndex() + 4);
            Comment comment = drawing.createCellComment(anchor);
            comment.setString(helper.createRichTextString(tip));
            cell.setCellComment(comment);
        }

        private void applyDefaultColumnFormat(Cell cell, String format) {
            if (format == null || format.isBlank()) {
                return;
            }
            cell.getSheet().setDefaultColumnStyle(cell.getColumnIndex(), style(cell, format));
        }

        private void applyCellFormat(Cell cell, String format) {
            if (format == null || format.isBlank()) {
                return;
            }
            cell.setCellStyle(style(cell, format));
        }

        private CellStyle style(Cell cell, String format) {
            return styles.computeIfAbsent(format, key -> {
                CellStyle style = cell.getSheet().getWorkbook().createCellStyle();
                style.setDataFormat(cell.getSheet().getWorkbook().getCreationHelper()
                        .createDataFormat().getFormat(key));
                return style;
            });
        }
    }
}
