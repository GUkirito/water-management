package com.example.watermanagement.util;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
        // 设置响应头
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition",
                "attachment;filename=" + encodedName + ".xlsx");

        try (OutputStream out = response.getOutputStream()) {
            EasyExcel.write(out, head)
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
}
