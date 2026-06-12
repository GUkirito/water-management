package com.example.watermanagement.service;

import com.example.watermanagement.dto.ReadingBatchItem;
import com.example.watermanagement.dto.ReadingExportRow;
import com.example.watermanagement.dto.ReadingRowDTO;
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
     */
    void exportTemplate(List<String> villageNames, HttpServletResponse response) throws IOException;

    /**
     * 导入已填写的抄表模板，自动计算用量、检测异常、生成水费账单
     *
     * @param inputStream Excel 文件流
     * @param readingDate 抄表日期
     * @return 导入结果 {"total": 成功条数, "abnormal": 异常条数, "errors": 错误列表}
     */
    Map<String, Object> importReadings(InputStream inputStream, LocalDate readingDate);

    /**
     * 批量保存表底数据
     *
     * @param items       抄表数据列表 [{waterMeterId, currentReading, chargeableUsage, note}]
     * @param readingDate 抄表日期
     * @return 保存结果
     */
    Map<String, Object> batchSave(List<ReadingBatchItem> items, LocalDate readingDate);

    /**
     * 单个录入抄表数据
     */
    Reading singleSave(String waterMeterId, java.math.BigDecimal currentReading, LocalDate readingDate);

    /**
     * 按年月查询某村的抄表记录（保持向后兼容）
     */
    List<Reading> getByMonth(int year, int month, List<String> villageNames);

    /**
     * 按日期和村名查询抄表数据，返回前端表格所需的合并数据
     *
     * @param readingDate 抄表日期
     * @param villageName 村名
     * @return 该村所有户的抄表行数据（含未录入的户）
     */
    List<ReadingRowDTO> getByDate(LocalDate readingDate, String villageName);

    /**
     * 查询最近异常抄表记录（用于仪表盘提醒）
     */
    List<Map<String, Object>> getAbnormalReadings(int limit);

    /**
     * 获取系统配置（水价、异常阈值等）
     */
    Map<String, Object> getConfig();
}
