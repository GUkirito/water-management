package com.example.watermanagement.controller;

import com.example.watermanagement.dto.ApiResponse;
import com.example.watermanagement.dto.ReadingBatchItem;
import com.example.watermanagement.dto.ReadingRowDTO;
import com.example.watermanagement.entity.Reading;
import com.example.watermanagement.service.ReadingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 抄表管理 Controller
 * <p>
 * 提供抄表模板导出、Excel 导入、批量保存、按户录入、按月/按日查询等功能。
 */
@Tag(name = "抄表管理", description = "抄表模板、批量导入、按户录入")
@RestController
@RequestMapping("/api/readings")
@RequiredArgsConstructor
public class ReadingController {

    private final ReadingService readingService;

    @Operation(summary = "导出抄表空白模板", description = "基于选定村名导出 Excel 模板，供抄表员线下填写后导入")
    @GetMapping("/export-template")
    public void exportTemplate(
            @Parameter(description = "村名列表（多选）") @RequestParam(required = false) List<String> villageNames,
            HttpServletResponse response) throws IOException {
        readingService.exportTemplate(villageNames, response);
    }

    @Operation(summary = "导出历史抄表模板", description = "用于导入历史表底或初始化读数，默认不生成历史账单")
    @GetMapping("/history-template")
    public void exportHistoricalTemplate(
            @Parameter(description = "村名列表（多选）") @RequestParam(required = false) List<String> villageNames,
            HttpServletResponse response) throws IOException {
        readingService.exportHistoricalTemplate(villageNames, response);
    }

    @Operation(summary = "预览抄表导入", description = "只解析和校验 Excel，不写入数据库")
    @PostMapping("/import/preview")
    public ApiResponse<Map<String, Object>> previewImportReadings(
            @Parameter(description = "抄表日期（yyyy-MM-dd）")
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate readingDate,
            @Parameter(description = "Excel 文件") @RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.ok("预览完成", readingService.previewImportReadings(file.getInputStream(), readingDate));
    }

    @Operation(summary = "导入已填写的抄表模板",
            description = "上传已填写的 Excel，自动计算用量、检测异常、生成水费账单")
    @PostMapping("/import")
    public ApiResponse<Map<String, Object>> importReadings(
            @Parameter(description = "抄表日期（yyyy-MM-dd）")
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate readingDate,
            @Parameter(description = "Excel 文件") @RequestParam("file") MultipartFile file) throws IOException {
        Map<String, Object> result = readingService.importReadings(file.getInputStream(), readingDate);
        return ApiResponse.ok("导入完成", result);
    }

    @Operation(summary = "预览历史抄表导入", description = "只解析历史表底 Excel，不写入数据库")
    @PostMapping("/history-import/preview")
    public ApiResponse<Map<String, Object>> previewHistoricalReadings(
            @Parameter(description = "Excel 文件") @RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.ok("预览完成", readingService.previewHistoricalReadings(file.getInputStream()));
    }

    @Operation(summary = "导入历史抄表", description = "只写历史 readings，不生成 water_bills")
    @PostMapping("/history-import")
    public ApiResponse<Map<String, Object>> importHistoricalReadings(
            @Parameter(description = "Excel 文件") @RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.ok("导入完成", readingService.importHistoricalReadings(file.getInputStream()));
    }

    @Operation(summary = "按日期和村名查询抄表数据",
            description = "返回指定日期下某村所有户的抄表行数据，用于前端表格展示")
    @GetMapping("/by-date")
    public ApiResponse<List<ReadingRowDTO>> getByDate(
            @Parameter(description = "抄表日期（yyyy-MM-dd）")
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate readingDate,
            @Parameter(description = "村名") @RequestParam(required = false) String villageName) {
        return ApiResponse.ok(readingService.getByDate(readingDate, villageName));
    }

    @Operation(summary = "获取系统配置", description = "返回水价、异常阈值等前端所需配置")
    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> getConfig() {
        return ApiResponse.ok(readingService.getConfig());
    }

    @Operation(summary = "更新系统配置")
    @PostMapping("/config")
    public ApiResponse<Void> updateConfig(@RequestBody Map<String, Object> body) {
        readingService.updateConfig(body);
        return ApiResponse.ok("配置已更新", null);
    }

    @Operation(summary = "批量保存表底数据",
            description = "接收 JSON 数组 [{waterMeterId, currentReading, chargeableUsage, note}]，批量计算用量并生成水费账单")
    @PostMapping("/batch")
    public ApiResponse<Map<String, Object>> batchSave(
            @Parameter(description = "抄表日期（yyyy-MM-dd）")
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate readingDate,
            @Valid @RequestBody List<ReadingBatchItem> items) {
        Map<String, Object> result = readingService.batchSave(items, readingDate);
        return ApiResponse.ok("批量保存完成", result);
    }

    @Operation(summary = "按户单个录入抄表", description = "选择某户录入本次表底，自动计算用量和生成账单")
    @PostMapping("/single")
    public ApiResponse<Reading> singleSave(
            @Parameter(description = "水表编号") @RequestParam String waterMeterId,
            @Parameter(description = "本次表底数") @RequestParam BigDecimal currentReading,
            @Parameter(description = "抄表日期（yyyy-MM-dd）")
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate readingDate) {
        return ApiResponse.ok("录入成功", readingService.singleSave(waterMeterId, currentReading, readingDate));
    }

    @Operation(summary = "按年月查询抄表记录", description = "查询指定村在指定月份的抄表记录")
    @GetMapping("/by-month")
    public ApiResponse<List<Reading>> getByMonth(
            @Parameter(description = "年份") @RequestParam int year,
            @Parameter(description = "月份") @RequestParam int month,
            @Parameter(description = "村名列表（多选）") @RequestParam(required = false) List<String> villageNames) {
        return ApiResponse.ok(readingService.getByMonth(year, month, villageNames));
    }

    @Operation(summary = "查询异常抄表记录", description = "返回最近N条异常抄表，含户名村名，用于仪表盘提醒")
    @GetMapping("/abnormal")
    public ApiResponse<List<Map<String, Object>>> getAbnormal(
            @Parameter(description = "返回条数") @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(readingService.getAbnormalReadings(limit));
    }
}
