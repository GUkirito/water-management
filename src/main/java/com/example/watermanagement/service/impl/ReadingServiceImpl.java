package com.example.watermanagement.service.impl;

import com.example.watermanagement.dto.ReadingBatchItem;
import com.example.watermanagement.dto.ReadingExportRow;
import com.example.watermanagement.entity.Household;
import com.example.watermanagement.entity.Reading;
import com.example.watermanagement.entity.WaterBill;
import com.example.watermanagement.exception.BusinessException;
import com.example.watermanagement.repository.HouseholdRepository;
import com.example.watermanagement.repository.ReadingRepository;
import com.example.watermanagement.repository.WaterBillRepository;
import com.example.watermanagement.service.ReadingService;
import com.example.watermanagement.util.ExcelUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 抄表管理 Service 实现
 * <p>
 * 核心业务流程：
 * 1. 导出模板 → 查询活跃水表 + 上次表底
 * 2. 导入/保存 → 计算用量 → 异常检测 → 写 readings + 生成 water_bills
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReadingServiceImpl implements ReadingService {

    private final HouseholdRepository householdRepository;
    private final ReadingRepository readingRepository;
    private final WaterBillRepository waterBillRepository;
    private final EntityManager entityManager;

    /** 水价：元/吨，默认 1.8 */
    @Value("${water.price:1.8}")
    private BigDecimal waterPrice;

    /** 异常检测阈值：吨，默认 100 */
    @Value("${water.abnormal.threshold:100}")
    private BigDecimal abnormalThreshold;

    // ==================== 模板导出 ====================

    @Override
    public void exportTemplate(List<String> villageNames, HttpServletResponse response) throws IOException {
        // 1. 查询目标村名下所有活跃水表
        List<Household> households;
        if (villageNames != null && !villageNames.isEmpty()) {
            households = householdRepository.findByVillageNameInAndIsActiveTrue(villageNames);
        } else {
            households = householdRepository.findByIsActiveTrue();
        }

        // 2. 获取每个水表的最新抄表记录（上次表底）
        List<String> meterIds = households.stream()
                .map(Household::getWaterMeterId).collect(Collectors.toList());
        Map<String, BigDecimal> lastReadings = getLastReadings(meterIds);

        // 3. 构建 Excel 数据行
        List<ReadingExportRow> rows = households.stream()
                .map(h -> ReadingExportRow.builder()
                        .waterMeterId(h.getWaterMeterId())
                        .householdName(h.getHouseholdName())
                        .villageName(h.getVillageName())
                        .previousReading(lastReadings.getOrDefault(h.getWaterMeterId(), BigDecimal.ZERO))
                        .currentReading(null)  // 空白，由抄表员填写
                        .build())
                .collect(Collectors.toList());

        // 4. 写出 Excel
        String filename = "抄表模板_" + LocalDate.now();
        ExcelUtil.export(response, filename, ReadingExportRow.class, rows);
        log.info("导出抄表模板: {} 户", rows.size());
    }

    // ==================== 模板导入 ====================

    @Override
    @Transactional
    public Map<String, Object> importReadings(InputStream inputStream, int year, int month) {
        List<ReadingExportRow> rows = ExcelUtil.read(inputStream, ReadingExportRow.class);

        int total = 0, abnormal = 0;
        List<String> errors = new ArrayList<>();

        for (ReadingExportRow row : rows) {
            try {
                if (row.getWaterMeterId() == null || row.getCurrentReading() == null) {
                    errors.add("数据不完整: 水表编号或本次表底为空，跳过");
                    continue;
                }
                Reading reading = processSingleReading(
                        row.getWaterMeterId(), row.getCurrentReading(), year, month);
                total++;
                if (Boolean.TRUE.equals(reading.getIsAbnormal())) {
                    abnormal++;
                }
            } catch (Exception e) {
                errors.add(row.getWaterMeterId() + ": " + e.getMessage());
                log.warn("导入抄表数据失败: waterMeterId={}, {}", row.getWaterMeterId(), e.getMessage());
            }
        }

        log.info("导入抄表完成: 成功{}条, 异常{}条, 错误{}条", total, abnormal, errors.size());
        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("abnormal", abnormal);
        result.put("errors", errors);
        return result;
    }

    // ==================== 批量保存 ====================

    @Override
    @Transactional
    public Map<String, Object> batchSave(List<ReadingBatchItem> items, int year, int month) {
        int total = 0, abnormal = 0;
        List<String> errors = new ArrayList<>();

        for (ReadingBatchItem item : items) {
            try {
                Reading reading = processSingleReading(
                        item.getWaterMeterId(), item.getCurrentReading(), year, month);
                total++;
                if (Boolean.TRUE.equals(reading.getIsAbnormal())) {
                    abnormal++;
                }
            } catch (Exception e) {
                errors.add(item.getWaterMeterId() + ": " + e.getMessage());
            }
        }

        log.info("批量保存抄表完成: 成功{}条, 异常{}条", total, abnormal);
        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("abnormal", abnormal);
        result.put("errors", errors);
        return result;
    }

    // ==================== 单个录入 ====================

    @Override
    @Transactional
    public Reading singleSave(String waterMeterId, BigDecimal currentReading, LocalDate readingDate) {
        int year = readingDate.getYear();
        int month = readingDate.getMonthValue();
        return processSingleReading(waterMeterId, currentReading, year, month);
    }

    // ==================== 查询 ====================

    @Override
    public List<Reading> getByMonth(int year, int month, List<String> villageNames) {
        // 计算当月的起止日期
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1).minusDays(1);

        // 如果指定了村名，先查出该村所有水表编号，再按日期范围查
        if (villageNames != null && !villageNames.isEmpty()) {
            List<String> meterIds = householdRepository
                    .findByVillageNameInAndIsActiveTrue(villageNames)
                    .stream().map(Household::getWaterMeterId).collect(Collectors.toList());
            if (meterIds.isEmpty()) {
                return Collections.emptyList();
            }
            // 逐水表查当月记录
            return meterIds.stream()
                    .flatMap(mid -> readingRepository
                            .findByWaterMeterIdAndReadingDateBetween(mid, start, end).stream())
                    .collect(Collectors.toList());
        }
        return readingRepository.findByReadingDateBetween(start, end);
    }

    // ==================== 私有方法 ====================

    /**
     * 处理单条抄表数据：计算用量 → 异常检测 → 保存 → 生成水费账单
     */
    private Reading processSingleReading(String waterMeterId, BigDecimal currentReading,
                                          int year, int month) {
        // 校验水表存在
        Household household = householdRepository.findByWaterMeterId(waterMeterId)
                .orElseThrow(() -> new BusinessException("水表不存在: " + waterMeterId));

        // 获取上次表底
        BigDecimal previousReading = getLastReading(waterMeterId);
        BigDecimal usageAmount = currentReading.subtract(previousReading);

        // 异常检测
        boolean isAbnormal = false;
        String abnormalReason = null;
        String anomaly = checkAbnormal(currentReading, previousReading);
        if (anomaly != null) {
            isAbnormal = true;
            abnormalReason = anomaly;
        }

        // 保存抄表记录
        Reading reading = Reading.builder()
                .waterMeterId(waterMeterId)
                .readingDate(LocalDate.of(year, month, 1)) // 统一存为每月1号
                .currentReading(currentReading)
                .previousReading(previousReading)
                .usageAmount(usageAmount)
                .isAbnormal(isAbnormal)
                .abnormalReason(abnormalReason)
                .build();

        // 检查当月是否已有记录（唯一约束 water_meter_id + reading_date）
        var existingOpt = readingRepository
                .findByWaterMeterIdAndReadingDate(waterMeterId, reading.getReadingDate());
        if (existingOpt.isPresent()) {
            reading.setId(existingOpt.get().getId()); // 覆盖已有记录
        }

        reading = readingRepository.save(reading);

        // 生成当月水费账单（应收水费 = 用量 × 水价）
        BigDecimal waterCharge = usageAmount.compareTo(BigDecimal.ZERO) > 0
                ? usageAmount.multiply(waterPrice).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        waterBillRepository
                .findByWaterMeterIdAndBillYearAndBillMonth(waterMeterId, year, month)
                .ifPresentOrElse(
                        existingBill -> {
                            // 更新已有账单
                            existingBill.setWaterAmount(usageAmount);
                            existingBill.setWaterCharge(waterCharge);
                            waterBillRepository.save(existingBill);
                        },
                        () -> {
                            // 新建账单
                            WaterBill bill = WaterBill.builder()
                                    .waterMeterId(waterMeterId)
                                    .billYear(year)
                                    .billMonth(month)
                                    .waterAmount(usageAmount)
                                    .waterCharge(waterCharge)
                                    .actualWaterPaid(BigDecimal.ZERO)
                                    .waterStatus("未收")
                                    .build();
                            waterBillRepository.save(bill);
                        });

        return reading;
    }

    /**
     * 获取某个水表的最新表底（上一次读数）
     */
    private BigDecimal getLastReading(String waterMeterId) {
        List<Reading> readings = readingRepository
                .findByWaterMeterIdInOrderByReadingDateDesc(List.of(waterMeterId));
        if (readings.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return readings.get(0).getCurrentReading();
    }

    /**
     * 批量获取水表的最新读数 → {waterMeterId: lastReading}
     */
    private Map<String, BigDecimal> getLastReadings(List<String> meterIds) {
        if (meterIds.isEmpty()) return Collections.emptyMap();
        Map<String, BigDecimal> result = new HashMap<>();
        List<Reading> allReadings = readingRepository
                .findByWaterMeterIdInOrderByReadingDateDesc(meterIds);
        // 已按日期降序排列，首次出现的即为最新记录
        Set<String> seen = new HashSet<>();
        for (Reading r : allReadings) {
            if (!seen.contains(r.getWaterMeterId())) {
                seen.add(r.getWaterMeterId());
                result.put(r.getWaterMeterId(), r.getCurrentReading());
            }
        }
        // 没找到记录的默认表底为 0
        for (String id : meterIds) {
            result.putIfAbsent(id, BigDecimal.ZERO);
        }
        return result;
    }

    /**
     * 异常检测：倒转（current < previous）或 突增（用量 > 阈值）
     *
     * @return 异常原因（null 表示正常）
     */
    private String checkAbnormal(BigDecimal current, BigDecimal previous) {
        if (current.compareTo(previous) < 0) {
            return "表底倒转（本次=" + current + " < 上次=" + previous + "）";
        }
        BigDecimal usage = current.subtract(previous);
        if (usage.compareTo(abnormalThreshold) > 0) {
            return "用量突增（" + usage + " 吨 > 阈值 " + abnormalThreshold + " 吨）";
        }
        return null;
    }

    // ==================== 异常查询 ====================

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getAbnormalReadings(int limit) {
        String jpql = """
            SELECT new map(
                r.readingDate as readingDate,
                r.abnormalReason as abnormalReason,
                h.householdName as householdName,
                h.villageName as villageName
            )
            FROM Reading r
            JOIN Household h ON r.waterMeterId = h.waterMeterId
            WHERE r.isAbnormal = true
            ORDER BY r.readingDate DESC
            """;
        return (List<Map<String, Object>>) entityManager.createQuery(jpql)
                .setMaxResults(limit)
                .getResultList();
    }
}
