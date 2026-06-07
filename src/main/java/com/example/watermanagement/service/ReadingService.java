package com.example.watermanagement.service;

import com.example.watermanagement.dto.ReadingBatchItem;
import com.example.watermanagement.dto.ReadingExportRow;
import com.example.watermanagement.entity.Reading;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 抄表管理 Service
 */
public interface ReadingService {

    /**
     * 导出抄表空白模板（基于选定村名）
     *
     * @param villageNames 村名列表
     * @param response     HTTP 响应
     */
    void exportTemplate(List<String> villageNames, HttpServletResponse response) throws IOException;

    /**
     * 导入已填写的抄表模板，自动计算用量、检测异常、生成水费账单
     *
     * @param inputStream Excel 文件流
     * @param year        账单年份
     * @param month       账单月份
     * @return 导入结果 {"total": 成功条数, "abnormal": 异常条数, "errors": 错误列表}
     */
    Map<String, Object> importReadings(InputStream inputStream, int year, int month);

    /**
     * 批量保存表底数据
     *
     * @param items 抄表数据列表 [{waterMeterId, currentReading}]
     * @param year  账单年份
     * @param month 账单月份
     * @return 保存结果
     */
    Map<String, Object> batchSave(List<ReadingBatchItem> items, int year, int month);

    /**
     * 单个录入抄表数据
     *
     * @param waterMeterId   水表编号
     * @param currentReading 本次表底
     * @param readingDate    抄表日期
     * @return 保存后的抄表记录
     */
    Reading singleSave(String waterMeterId, java.math.BigDecimal currentReading, LocalDate readingDate);

    /**
     * 按年月查询某村的抄表记录
     *
     * @param year         年份
     * @param month        月份
     * @param villageNames 村名列表
     * @return 抄表记录列表
     */
    List<Reading> getByMonth(int year, int month, List<String> villageNames);

    /**
     * 查询最近异常抄表记录（用于仪表盘提醒）
     *
     * @param limit 返回条数上限
     * @return 异常抄表列表，含户名和村名
     */
    List<Map<String, Object>> getAbnormalReadings(int limit);
}
