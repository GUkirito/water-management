package com.example.watermanagement.controller;

import com.example.watermanagement.dto.ApiResponse;
import com.example.watermanagement.dto.VillageCollectionSummaryRow;
import com.example.watermanagement.dto.WaterBillReportRow;
import com.example.watermanagement.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@Tag(name = "报表中心", description = "水费月报表、村组收缴进度")
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "查询水费月报表数据", description = "返回指定年月、指定村的水费报表数据")
    @GetMapping("/water-bill")
    public ApiResponse<List<WaterBillReportRow>> getWaterBillReport(
            @Parameter(description = "年份") @RequestParam int year,
            @Parameter(description = "月份") @RequestParam int month,
            @Parameter(description = "村名列表（多选）") @RequestParam(required = false) List<String> villageNames) {
        return ApiResponse.ok(reportService.getWaterBillReportData(year, month, villageNames));
    }

    @Operation(summary = "导出水费月报表 Excel")
    @GetMapping("/water-bill/export")
    public void exportWaterBillReport(
            @Parameter(description = "年份") @RequestParam int year,
            @Parameter(description = "月份") @RequestParam int month,
            @Parameter(description = "村名列表（多选）") @RequestParam(required = false) List<String> villageNames,
            HttpServletResponse response) throws IOException {
        reportService.exportWaterBillReport(year, month, villageNames, response);
    }

    @Operation(summary = "按村统计收缴进度", description = "返回指定月份各村应收、实收、欠费户数、完成率和异常抄表数")
    @GetMapping("/village-collection-summary")
    public ApiResponse<List<VillageCollectionSummaryRow>> getVillageCollectionSummary(
            @Parameter(description = "年份") @RequestParam int year,
            @Parameter(description = "月份") @RequestParam int month) {
        return ApiResponse.ok(reportService.getVillageCollectionSummary(year, month));
    }
}
