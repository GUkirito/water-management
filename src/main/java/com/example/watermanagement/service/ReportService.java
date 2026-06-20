package com.example.watermanagement.service;

import com.example.watermanagement.dto.VillageCollectionSummaryRow;
import com.example.watermanagement.dto.WaterBillReportRow;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/**
 * 报表管理 Service
 */
public interface ReportService {

    /**
     * 导出水费月报表
     *
     * @param year         年份
     * @param month        月份
     * @param villageNames 村名列表
     * @param response     HTTP 响应（直接写出 Excel）
     */
    void exportWaterBillReport(int year, int month,
                               List<String> villageNames,
                               HttpServletResponse response) throws IOException;

    /**
     * 查询水费月报表数据（供前端展示）
     */
    List<WaterBillReportRow> getWaterBillReportData(int year, int month,
                                                     List<String> villageNames);

    /**
     * 按村统计指定月份收缴进度。
     */
    List<VillageCollectionSummaryRow> getVillageCollectionSummary(int year, int month);
}
