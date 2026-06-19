package com.example.watermanagement.service.impl;

import com.example.watermanagement.dto.ReadingBatchItem;
import com.example.watermanagement.dto.ReadingExportRow;
import com.example.watermanagement.dto.ReadingRowDTO;
import com.example.watermanagement.entity.Household;
import com.example.watermanagement.entity.Reading;
import com.example.watermanagement.entity.WaterBill;
import com.example.watermanagement.exception.BusinessException;
import com.example.watermanagement.repository.HouseholdRepository;
import com.example.watermanagement.repository.ReadingRepository;
import com.example.watermanagement.repository.WaterBillRepository;
import com.example.watermanagement.service.ReadingService;
import com.example.watermanagement.util.ExcelUtil;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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

    private java.util.Properties configProps = new java.util.Properties();
    private static final File CONFIG_FILE =
        new File(System.getProperty("user.home"), ".water-management/config.properties");

    @PostConstruct
    void loadConfig() {
        if (CONFIG_FILE.exists()) {
            try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
                configProps.load(fis);
                String wp = configProps.getProperty("water.price");
                if (wp != null) waterPrice = new BigDecimal(wp);
                String at = configProps.getProperty("water.abnormal.threshold");
                if (at != null) abnormalThreshold = new BigDecimal(at);
            } catch (Exception e) { log.warn("Load config failed", e); }
        }
    }

    // ==================== 模板导出 ====================

    @Override
    public void exportTemplate(List<String> villageNames, HttpServletResponse response) throws IOException {
        List<Household> households;
        if (villageNames != null && !villageNames.isEmpty()) {
            households = householdRepository.findByVillageNameInAndIsActiveTrue(villageNames);
        } else {
            households = householdRepository.findByIsActiveTrue();
        }

        List<String> meterIds = households.stream()
                .map(Household::getWaterMeterId).collect(Collectors.toList());
        Map<String, BigDecimal> lastReadings = getLastReadings(meterIds);

        List<ReadingExportRow> rows = households.stream()
                .map(h -> ReadingExportRow.builder()
                        .waterMeterId(h.getWaterMeterId())
                        .householdName(h.getHouseholdName())
                        .villageName(h.getVillageName())
                        .previousReading(lastReadings.getOrDefault(h.getWaterMeterId(), BigDecimal.ZERO))
                        .currentReading(null)
                        .waterPrice(waterPrice)
                        .waterCharge(null)
                        .build())
                .collect(Collectors.toList());

        String filename = "抄表模板_" + LocalDate.now();
        ExcelUtil.export(response, filename, ReadingExportRow.class, rows);
        log.info("导出抄表模板: {} 户", rows.size());
    }

    // ==================== 模板导入 ====================

    @Override
    @Transactional
    public Map<String, Object> importReadings(InputStream inputStream, LocalDate readingDate) {
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
                        row.getWaterMeterId(), row.getCurrentReading(), readingDate, null, null);
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
    public Map<String, Object> batchSave(List<ReadingBatchItem> items, LocalDate readingDate) {
        int total = 0, abnormal = 0;
        List<String> errors = new ArrayList<>();

        for (ReadingBatchItem item : items) {
            try {
                Reading reading = processSingleReading(
                        item.getWaterMeterId(), item.getCurrentReading(),
                        readingDate, item.getChargeableUsage(), item.getNote());
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
        return processSingleReading(waterMeterId, currentReading, readingDate, null, null);
    }

    // ==================== 查询 ====================

    @Override
    public List<Reading> getByMonth(int year, int month, List<String> villageNames) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1).minusDays(1);

        if (villageNames != null && !villageNames.isEmpty()) {
            List<String> meterIds = householdRepository
                    .findByVillageNameInAndIsActiveTrue(villageNames)
                    .stream().map(Household::getWaterMeterId).collect(Collectors.toList());
            if (meterIds.isEmpty()) {
                return Collections.emptyList();
            }
            return meterIds.stream()
                    .flatMap(mid -> readingRepository
                            .findByWaterMeterIdAndReadingDateBetween(mid, start, end).stream())
                    .collect(Collectors.toList());
        }
        return readingRepository.findByReadingDateBetween(start, end);
    }

    @Override
    public List<ReadingRowDTO> getByDate(LocalDate readingDate, String villageName) {
        // 获取该村所有活跃户
        List<Household> households;
        if (villageName != null && !villageName.isEmpty()) {
            households = householdRepository.findByVillageNameInAndIsActiveTrue(List.of(villageName));
        } else {
            households = householdRepository.findByIsActiveTrue();
        }

        int billYear = readingDate.getYear();
        int billMonth = readingDate.getMonthValue();

        List<ReadingRowDTO> rows = new ArrayList<>();
        for (Household h : households) {
            String meterId = h.getWaterMeterId();

            // 1. 查找当日是否已有抄表记录
            Optional<Reading> existingReading = readingRepository
                    .findByWaterMeterIdAndReadingDate(meterId, readingDate);

            // 2. 查找对应的 WaterBill（计费用水量存在 water_bills.water_amount 中）
            BigDecimal chargeableUsage = null;
            Optional<WaterBill> existingBill = waterBillRepository
                    .findByWaterMeterIdAndBillYearAndBillMonth(meterId, billYear, billMonth);
            if (existingBill.isPresent()) {
                chargeableUsage = existingBill.get().getWaterAmount();
            }

            if (existingReading.isPresent()) {
                Reading r = existingReading.get();
                rows.add(ReadingRowDTO.builder()
                        .waterMeterId(meterId)
                        .previousReading(r.getPreviousReading())
                        .currentReading(r.getCurrentReading())
                        .chargeableUsage(chargeableUsage)
                        .usageAmount(r.getUsageAmount())
                        .isAbnormal(r.getIsAbnormal())
                        .abnormalReason(r.getAbnormalReason())
                        .note(r.getNote())
                        .build());
            } else {
                // 无当日记录：计算上次表底（最近一次抄表）
                BigDecimal previousReading = getLastReadingBefore(meterId, readingDate);
                rows.add(ReadingRowDTO.builder()
                        .waterMeterId(meterId)
                        .previousReading(previousReading)
                        .currentReading(null)
                        .chargeableUsage(null)
                        .usageAmount(null)
                        .isAbnormal(false)
                        .abnormalReason(null)
                        .note(null)
                        .build());
            }
        }

        return rows;
    }

    // ==================== 配置 ====================

    @Override
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("waterPrice", waterPrice);
        config.put("abnormalThreshold", abnormalThreshold);
        return config;
    }

    @Override
    public void updateConfig(Map<String, Object> config) {
        if (config.containsKey("waterPrice")) {
            waterPrice = new BigDecimal(config.get("waterPrice").toString());
            configProps.setProperty("water.price", waterPrice.toString());
        }
        if (config.containsKey("abnormalThreshold")) {
            abnormalThreshold = new BigDecimal(config.get("abnormalThreshold").toString());
            configProps.setProperty("water.abnormal.threshold", abnormalThreshold.toString());
        }
        CONFIG_FILE.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            configProps.store(fos, "Water Management Config");
        } catch (Exception e) { log.error("Save config failed", e); }
        log.info("Config updated: waterPrice={}, threshold={}", waterPrice, abnormalThreshold);
    }

    // ==================== 私有方法 ====================

    /**
     * 处理单条抄表数据：计算用量 → 异常检测 → 保存 → 生成水费账单
     *
     * @param waterMeterId    水表编号
     * @param currentReading  本次表底
     * @param readingDate     抄表日期
     * @param chargeableUsage 计费用水量（可选，不传则使用实际用量）
     * @param note            备注
     */
    private Reading processSingleReading(String waterMeterId, BigDecimal currentReading,
                                          LocalDate readingDate, BigDecimal chargeableUsage, String note) {
        // 校验水表存在
        Household household = householdRepository.findByWaterMeterId(waterMeterId)
                .orElseThrow(() -> new BusinessException("水表不存在: " + waterMeterId));

        // 获取上次表底
        BigDecimal previousReading = getLastReadingBefore(waterMeterId, readingDate);
        BigDecimal usageAmount = currentReading.subtract(previousReading);

        // 计费用水量：优先使用传入值，否则使用实际用量
        BigDecimal effectiveChargeable = (chargeableUsage != null) ? chargeableUsage : usageAmount;

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
                .readingDate(readingDate)
                .currentReading(currentReading)
                .previousReading(previousReading)
                .usageAmount(usageAmount)
                .isAbnormal(isAbnormal)
                .abnormalReason(abnormalReason)
                .note(note)
                .build();

        // 检查当天是否已有记录（唯一约束 water_meter_id + reading_date）
        var existingOpt = readingRepository
                .findByWaterMeterIdAndReadingDate(waterMeterId, readingDate);
        if (existingOpt.isPresent()) {
            reading.setId(existingOpt.get().getId());
        }

        reading = readingRepository.save(reading);

        // 生成水费账单（应收水费 = 计费用水量 × 水价）
        int billYear = readingDate.getYear();
        int billMonth = readingDate.getMonthValue();

        BigDecimal waterCharge = effectiveChargeable.compareTo(BigDecimal.ZERO) > 0
                ? effectiveChargeable.multiply(waterPrice).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        waterBillRepository
                .findByWaterMeterIdAndBillYearAndBillMonth(waterMeterId, billYear, billMonth)
                .ifPresentOrElse(
                        existingBill -> {
                            // 更新已有账单，同时重算状态（防止改量后已收账单变欠费死账）
                            existingBill.setWaterAmount(effectiveChargeable);
                            existingBill.setWaterCharge(waterCharge);
                            existingBill.setWaterStatus(calcBillStatus(existingBill.getActualWaterPaid(), waterCharge));
                            waterBillRepository.save(existingBill);
                        },
                        () -> {
                            // 新建账单
                            WaterBill bill = WaterBill.builder()
                                    .waterMeterId(waterMeterId)
                                    .billYear(billYear)
                                    .billMonth(billMonth)
                                    .waterAmount(effectiveChargeable)
                                    .waterCharge(waterCharge)
                                    .actualWaterPaid(BigDecimal.ZERO)
                                    .waterStatus("未收")
                                    .build();
                            waterBillRepository.save(bill);
                        });

        return reading;
    }

    /**
     * 获取某个水表在指定日期之前的最新表底
     */
    private BigDecimal getLastReadingBefore(String waterMeterId, LocalDate beforeDate) {
        List<Reading> readings = readingRepository
                .findByWaterMeterIdInOrderByReadingDateDesc(List.of(waterMeterId));
        // 已按日期降序排列，取第一条在 beforeDate 之前的记录
        for (Reading r : readings) {
            if (r.getReadingDate().isBefore(beforeDate)) {
                return r.getCurrentReading();
            }
        }
        return BigDecimal.ZERO;
    }

    /**
     * 根据已缴金额和应收金额计算账单状态
     */
    private String calcBillStatus(BigDecimal paid, BigDecimal total) {
        if (paid.compareTo(BigDecimal.ZERO) == 0) return "未收";
        if (paid.compareTo(total) >= 0) return "已收";
        return "部分收";
    }

    /**
     * 批量获取水表的最新读数 → {waterMeterId: lastReading}
     */
    private Map<String, BigDecimal> getLastReadings(List<String> meterIds) {
        if (meterIds.isEmpty()) return Collections.emptyMap();
        Map<String, BigDecimal> result = new HashMap<>();
        List<Reading> allReadings = readingRepository
                .findByWaterMeterIdInOrderByReadingDateDesc(meterIds);
        Set<String> seen = new HashSet<>();
        for (Reading r : allReadings) {
            if (!seen.contains(r.getWaterMeterId())) {
                seen.add(r.getWaterMeterId());
                result.put(r.getWaterMeterId(), r.getCurrentReading());
            }
        }
        for (String id : meterIds) {
            result.putIfAbsent(id, BigDecimal.ZERO);
        }
        return result;
    }

    /**
     * 异常检测：倒转（current < previous）或 突增（用量 > 阈值）
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
