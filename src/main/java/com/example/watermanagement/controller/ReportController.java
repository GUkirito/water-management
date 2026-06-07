package com.example.watermanagement.controller;

import com.example.watermanagement.dto.ApiResponse;
import com.example.watermanagement.dto.MaterialSummaryRow;
import com.example.watermanagement.dto.WaterBillReportRow;
import com.example.watermanagement.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * 报表中心 Controller
 * <p>
 * 提供水费月报表和材料费统计表的查询与导出。
 */
@Tag(name = "报表中心", description = "水费月报表、材料费统计表")
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // ==================== 水费月报表 ====================

    @Operation(summary = "查询水费月报表数据", description = "返回指定年月、指定村的水费报表数据")
    @GetMapping("/water-bill")
    public ApiResponse<List<WaterBillReportRow>> getWaterBillReport(
            @Parameter(description = "年份") @RequestParam int year,
            @Parameter(description = "月份") @RequestParam int month,
            @Parameter(description = "村名列表（多选）") @RequestParam(required = false) List<String> villageNames) {
        return ApiResponse.ok(reportService.getWaterBillReportData(year, month, villageNames));
    }

    @Operation(summary = "导出水费月报表 Excel", description = "包含村名、水表编号、户名、用水量、应收水费、实收水费、状态")
    @GetMapping("/water-bill/export")
    public void exportWaterBillReport(
            @Parameter(description = "年份") @RequestParam int year,
            @Parameter(description = "月份") @RequestParam int month,
            @Parameter(description = "村名列表（多选）") @RequestParam(required = false) List<String> villageNames,
            HttpServletResponse response) throws IOException {
        reportService.exportWaterBillReport(year, month, villageNames, response);
    }

    // ==================== 材料费统计表 ====================

    @Operation(summary = "查询材料费统计数据", description = "返回各户材料费应收、已缴、欠费、状态")
    @GetMapping("/material-summary")
    public ApiResponse<List<MaterialSummaryRow>> getMaterialSummary(
            @Parameter(description = "村名列表（多选）") @RequestParam(required = false) List<String> villageNames) {
        return ApiResponse.ok(reportService.getMaterialSummaryData(villageNames));
    }

    @Operation(summary = "导出材料费统计表 Excel", description = "包含水表编号、户名、应收材料费、已缴材料费、欠费金额、状态")
    @GetMapping("/material-summary/export")
    public void exportMaterialSummary(
            @Parameter(description = "村名列表（多选）") @RequestParam(required = false) List<String> villageNames,
            HttpServletResponse response) throws IOException {
        reportService.exportMaterialSummary(villageNames, response);
    }
}
