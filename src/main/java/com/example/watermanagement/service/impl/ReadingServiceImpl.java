package com.example.watermanagement.service.impl;

import com.example.watermanagement.dto.ReadingBatchItem;
import com.example.watermanagement.dto.ConfigUpdateDTO;
import com.example.watermanagement.dto.ReadingExportRow;
import com.example.watermanagement.dto.HistoricalReadingImportRow;
import com.example.watermanagement.dto.ReadingImportDetail;
import com.example.watermanagement.dto.ReadingRowDTO;
import com.example.watermanagement.entity.Household;
import com.example.watermanagement.entity.PrepaymentLog;
import com.example.watermanagement.entity.Reading;
import com.example.watermanagement.entity.WaterBill;
import com.example.watermanagement.exception.BusinessException;
import com.example.watermanagement.repository.HouseholdRepository;
import com.example.watermanagement.repository.MonthLockRepository;
import com.example.watermanagement.repository.PrepaymentLogRepository;
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
import java.time.format.DateTimeFormatter;
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
    private final PrepaymentLogRepository prepaymentLogRepository;
    private final MonthLockRepository monthLockRepository;
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

        List<ReadingExportRow> rows = new ArrayList<>();
        for (int i = 0; i < households.size(); i++) {
            Household h = households.get(i);
            rows.add(ReadingExportRow.builder()
                        .index(i + 1)
                        .waterMeterId(h.getWaterMeterId())
                        .householdName(h.getHouseholdName())
                        .villageName(h.getVillageName())
                        .previousReading(lastReadings.getOrDefault(h.getWaterMeterId(), BigDecimal.ZERO))
                        .currentReading(null)
                        .waterPrice(waterPrice)
                        .waterCharge(null)
                        .build());
        }

        String filename = "抄表模板_" + LocalDate.now();
        ExcelUtil.export(response, filename, ReadingExportRow.class, rows,
                readingTemplateTips(), readingTemplateFormats());
        log.info("导出抄表模板: {} 户", rows.size());
    }

    @Override
    public void exportHistoricalTemplate(List<String> villageNames, HttpServletResponse response) throws IOException {
        List<Household> households;
        if (villageNames != null && !villageNames.isEmpty()) {
            households = householdRepository.findByVillageNameInAndIsActiveTrue(villageNames);
        } else {
            households = householdRepository.findByIsActiveTrue();
        }
        List<String> meterIds = households.stream()
                .map(Household::getWaterMeterId).collect(Collectors.toList());
        Map<String, BigDecimal> lastReadings = getLastReadings(meterIds);

        List<HistoricalReadingImportRow> rows = new ArrayList<>();
        for (int i = 0; i < households.size(); i++) {
            Household h = households.get(i);
            rows.add(HistoricalReadingImportRow.builder()
                    .index(i + 1)
                    .householdName(h.getHouseholdName())
                    .waterMeterId(h.getWaterMeterId())
                    .villageName(h.getVillageName())
                    .readingDate(null)
                    .previousReading(lastReadings.getOrDefault(h.getWaterMeterId(), BigDecimal.ZERO))
                    .currentReading(null)
                    .waterPrice(waterPrice)
                    .waterCharge(null)
                    .build());
        }

        String filename = "历史抄表模板_" + LocalDate.now();
        ExcelUtil.export(response, filename, HistoricalReadingImportRow.class, rows,
                historicalTemplateTips(), historicalTemplateFormats());
        log.info("导出历史抄表模板: {} 户", rows.size());
    }

    private Map<Integer, String> readingTemplateTips() {
        return Map.of(
                0, "序号：系统生成，不需要修改。",
                1, "户名：系统带出，仅用于核对。",
                2, "水表编号：必填，导入时按此编号匹配住户。",
                3, "村名：系统带出，仅用于核对。",
                4, "上次表底：系统带出，一般不要修改。",
                5, "本次表底：必填，只填数字，如 1234.50。",
                6, "水价：系统带出，一般不要修改。",
                7, "水费：可不填，导入后系统按用水量和水价计算。"
        );
    }

    private Map<Integer, String> readingTemplateFormats() {
        return Map.of(4, "0.00", 5, "0.00", 6, "0.00", 7, "0.00");
    }

    private Map<Integer, String> historicalTemplateTips() {
        return Map.of(
                0, "序号：系统生成，不需要修改。",
                1, "户名：系统带出，仅用于核对。",
                2, "水表编号：必填，导入时按此编号匹配住户。",
                3, "村名：系统带出，仅用于核对。",
                4, "抄表日期：必填，格式为 2026-07-04，也兼容 2026/7/4。",
                5, "上次表底：系统带出，一般不要修改。",
                6, "本次表底：必填，只填数字，如 1234.50。",
                7, "水价：系统带出，一般不要修改。",
                8, "水费：可不填，历史导入默认不生成账单。"
        );
    }

    private Map<Integer, String> historicalTemplateFormats() {
        return Map.of(4, "yyyy-mm-dd", 5, "0.00", 6, "0.00", 7, "0.00", 8, "0.00");
    }

    // ==================== 模板导入 ====================

    @Override
    public Map<String, Object> previewImportReadings(InputStream inputStream, LocalDate readingDate) {
        List<ReadingExportRow> rows = ExcelUtil.read(inputStream, ReadingExportRow.class);
        List<ReadingImportDetail> details = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            details.add(previewStandardReading(rows.get(i), i + 1, readingDate));
        }
        return buildImportResult(details);
    }

    @Override
    @Transactional
    public Map<String, Object> importReadings(InputStream inputStream, LocalDate readingDate) {
        List<ReadingExportRow> rows = ExcelUtil.read(inputStream, ReadingExportRow.class);

        List<ReadingImportDetail> details = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            ReadingExportRow row = rows.get(i);
            ReadingImportDetail detail = previewStandardReading(row, i + 1, readingDate);
            if (isWritable(detail)) {
                try {
                Reading reading = processSingleReading(
                        row.getWaterMeterId(), row.getCurrentReading(), readingDate, null, null);
                    detail.setStatus(Boolean.TRUE.equals(reading.getIsAbnormal()) ? "abnormal" : "success");
                    detail.setMessage(Boolean.TRUE.equals(reading.getIsAbnormal())
                            ? "导入成功，已标记异常：" + reading.getAbnormalReason()
                            : "导入成功");
                if (Boolean.TRUE.equals(reading.getIsAbnormal())) {
                        detail.setStatus("abnormal");
                }
            } catch (Exception e) {
                    detail.setStatus("fail");
                    detail.setMessage(e.getMessage());
                log.warn("导入抄表数据失败: waterMeterId={}, {}", row.getWaterMeterId(), e.getMessage());
            }
            }
            details.add(detail);
        }

        Map<String, Object> result = buildImportResult(details);
        log.info("导入抄表完成: 成功{}条, 异常{}条, 错误{}条",
                result.get("success"), result.get("abnormal"), result.get("fail"));
        return result;
    }

    @Override
    public Map<String, Object> previewHistoricalReadings(InputStream inputStream) {
        List<HistoricalReadingImportRow> rows = ExcelUtil.read(inputStream, HistoricalReadingImportRow.class);
        return buildImportResult(previewHistoricalRows(rows, false));
    }

    @Override
    @Transactional
    public Map<String, Object> importHistoricalReadings(InputStream inputStream) {
        List<HistoricalReadingImportRow> rows = ExcelUtil.read(inputStream, HistoricalReadingImportRow.class);
        return buildImportResult(previewHistoricalRows(rows, true));
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
    public void updateConfig(ConfigUpdateDTO config) {
        if (config.getWaterPrice() != null) {
            waterPrice = config.getWaterPrice();
            configProps.setProperty("water.price", waterPrice.toString());
        }
        if (config.getAbnormalThreshold() != null) {
            abnormalThreshold = BigDecimal.valueOf(config.getAbnormalThreshold());
            configProps.setProperty("water.abnormal.threshold", abnormalThreshold.toString());
        }
        CONFIG_FILE.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            configProps.store(fos, "Water Management Config");
        } catch (Exception e) { log.error("Save config failed", e); }
        log.info("Config updated: waterPrice={}, threshold={}", waterPrice, abnormalThreshold);
    }

    private ReadingImportDetail previewStandardReading(ReadingExportRow row, int fallbackIndex, LocalDate readingDate) {
        Integer index = row.getIndex() != null ? row.getIndex() : fallbackIndex;
        ReadingImportDetail detail = ReadingImportDetail.builder()
                .type("抄表")
                .index(index)
                .householdName(row.getHouseholdName())
                .waterMeterId(row.getWaterMeterId())
                .villageName(row.getVillageName())
                .readingDate(readingDate)
                .currentReading(row.getCurrentReading())
                .waterPrice(waterPrice)
                .build();

        if (row.getWaterMeterId() == null || row.getWaterMeterId().isBlank() || row.getCurrentReading() == null) {
            return fail(detail, "水表编号或本次表底为空");
        }
        if (monthLockRepository.existsByBillYearAndBillMonth(readingDate.getYear(), readingDate.getMonthValue())) {
            return fail(detail, "该月份已月结锁定，不能导入抄表记录");
        }
        Optional<Household> household = householdRepository.findByWaterMeterId(row.getWaterMeterId());
        if (household.isEmpty()) {
            return fail(detail, "水表不存在: " + row.getWaterMeterId());
        }
        detail.setHouseholdName(household.get().getHouseholdName());
        detail.setVillageName(household.get().getVillageName());

        BigDecimal previous = getLastReadingBefore(row.getWaterMeterId(), readingDate);
        BigDecimal usage = row.getCurrentReading().subtract(previous);
        detail.setPreviousReading(previous);
        detail.setUsageAmount(usage);
        detail.setWaterCharge(calcWaterCharge(usage));
        if (usage.compareTo(BigDecimal.ZERO) < 0) {
            return fail(detail, "本次表底小于上次表底");
        }
        String anomaly = checkAbnormal(row.getCurrentReading(), previous);
        if (anomaly != null) {
            detail.setStatus("abnormal");
            detail.setMessage("将导入并标记异常：" + anomaly);
            return detail;
        }
        detail.setStatus("success");
        detail.setMessage(readingRepository.findByWaterMeterIdAndReadingDate(row.getWaterMeterId(), readingDate).isPresent()
                ? "将更新同日抄表记录并生成/更新账单"
                : "将新增抄表记录并生成账单");
        return detail;
    }

    private List<ReadingImportDetail> previewHistoricalRows(List<HistoricalReadingImportRow> rows, boolean write) {
        List<HistoricalReadingImportRow> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator
                .comparing(HistoricalReadingImportRow::getWaterMeterId, Comparator.nullsLast(String::compareTo))
                .thenComparing(row -> parseReadingDate(row.getReadingDate()), Comparator.nullsLast(LocalDate::compareTo)));

        Map<String, BigDecimal> lastImportedReading = new HashMap<>();
        List<ReadingImportDetail> details = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            HistoricalReadingImportRow row = sorted.get(i);
            ReadingImportDetail detail = previewHistoricalReading(row, i + 1);
            if (isWritable(detail) && lastImportedReading.containsKey(detail.getWaterMeterId())) {
                applyHistoricalPrevious(detail, lastImportedReading.get(detail.getWaterMeterId()));
            }
            if (write && isWritable(detail)) {
                Reading reading = Reading.builder()
                        .waterMeterId(detail.getWaterMeterId())
                        .readingDate(detail.getReadingDate())
                        .currentReading(detail.getCurrentReading())
                        .previousReading(detail.getPreviousReading())
                        .usageAmount(detail.getUsageAmount())
                        .isAbnormal("abnormal".equals(detail.getStatus()))
                        .abnormalReason("abnormal".equals(detail.getStatus()) ? detail.getMessage() : null)
                        .note("历史导入")
                        .build();
                readingRepository.save(reading);
                detail.setMessage("历史表底导入成功");
            }
            if (isWritable(detail)) {
                lastImportedReading.put(detail.getWaterMeterId(), detail.getCurrentReading());
            }
            details.add(detail);
        }
        return details;
    }

    private ReadingImportDetail previewHistoricalReading(HistoricalReadingImportRow row, int fallbackIndex) {
        LocalDate readingDate = parseReadingDate(row.getReadingDate());
        ReadingImportDetail detail = ReadingImportDetail.builder()
                .type("历史抄表")
                .index(row.getIndex() != null ? row.getIndex() : fallbackIndex)
                .householdName(row.getHouseholdName())
                .waterMeterId(row.getWaterMeterId())
                .villageName(row.getVillageName())
                .readingDate(readingDate)
                .currentReading(row.getCurrentReading())
                .waterPrice(waterPrice)
                .build();

        if (row.getWaterMeterId() == null || row.getWaterMeterId().isBlank()
                || row.getCurrentReading() == null || readingDate == null) {
            return fail(detail, "水表编号、抄表日期或本次表底为空");
        }
        Optional<Household> household = householdRepository.findByWaterMeterId(row.getWaterMeterId());
        if (household.isEmpty()) {
            return fail(detail, "水表不存在: " + row.getWaterMeterId());
        }
        if (readingRepository.findByWaterMeterIdAndReadingDate(row.getWaterMeterId(), readingDate).isPresent()) {
            detail.setStatus("skip");
            detail.setMessage("同一水表同一天已有抄表记录，已跳过");
            return detail;
        }
        detail.setHouseholdName(household.get().getHouseholdName());
        detail.setVillageName(household.get().getVillageName());
        BigDecimal previous = getLastReadingBefore(row.getWaterMeterId(), readingDate);
        BigDecimal usage = row.getCurrentReading().subtract(previous);
        detail.setPreviousReading(previous);
        detail.setUsageAmount(usage);
        detail.setWaterCharge(calcWaterCharge(usage));
        if (usage.compareTo(BigDecimal.ZERO) < 0) {
            return fail(detail, "本次表底小于上次表底");
        }
        String anomaly = checkAbnormal(row.getCurrentReading(), previous);
        if (anomaly != null) {
            detail.setStatus("abnormal");
            detail.setMessage("将导入历史表底并标记异常：" + anomaly);
            return detail;
        }
        detail.setStatus("success");
        detail.setMessage("将导入历史表底，不生成水费账单");
        return detail;
    }

    private Map<String, Object> buildImportResult(List<ReadingImportDetail> details) {
        long fail = details.stream().filter(d -> "fail".equals(d.getStatus())).count();
        long skip = details.stream().filter(d -> "skip".equals(d.getStatus())).count();
        long abnormal = details.stream().filter(d -> "abnormal".equals(d.getStatus())).count();
        long success = details.stream().filter(d -> "success".equals(d.getStatus())).count();
        Map<String, Object> result = new HashMap<>();
        result.put("total", details.size());
        result.put("success", (int) success);
        result.put("abnormal", (int) abnormal);
        result.put("fail", (int) fail);
        result.put("skip", (int) skip);
        result.put("details", details);
        result.put("errors", details.stream()
                .filter(d -> "fail".equals(d.getStatus()))
                .map(ReadingImportDetail::getMessage)
                .toList());
        return result;
    }

    private void applyHistoricalPrevious(ReadingImportDetail detail, BigDecimal previous) {
        BigDecimal usage = detail.getCurrentReading().subtract(previous);
        detail.setPreviousReading(previous);
        detail.setUsageAmount(usage);
        detail.setWaterCharge(calcWaterCharge(usage));
        if (usage.compareTo(BigDecimal.ZERO) < 0) {
            detail.setStatus("fail");
            detail.setMessage("本次表底小于上次表底");
            return;
        }
        String anomaly = checkAbnormal(detail.getCurrentReading(), previous);
        if (anomaly != null) {
            detail.setStatus("abnormal");
            detail.setMessage("将导入历史表底并标记异常：" + anomaly);
        }
    }

    private ReadingImportDetail fail(ReadingImportDetail detail, String message) {
        detail.setStatus("fail");
        detail.setMessage(message);
        return detail;
    }

    private boolean isWritable(ReadingImportDetail detail) {
        return "success".equals(detail.getStatus()) || "abnormal".equals(detail.getStatus());
    }

    private BigDecimal calcWaterCharge(BigDecimal usage) {
        return usage.compareTo(BigDecimal.ZERO) > 0
                ? usage.multiply(waterPrice).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

    private LocalDate parseReadingDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().replace("/", "-");
        try {
            return LocalDate.parse(normalized);
        } catch (Exception ignored) {
            try {
                return LocalDate.parse(normalized, DateTimeFormatter.ofPattern("yyyy-M-d"));
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
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
        if (monthLockRepository.existsByBillYearAndBillMonth(readingDate.getYear(), readingDate.getMonthValue())) {
            throw new BusinessException("该月份已月结锁定，不能修改抄表记录，请先解锁或走调账");
        }

        // 校验水表存在
        Household household = householdRepository.findByWaterMeterId(waterMeterId)
                .orElseThrow(() -> new BusinessException("水表不存在: " + waterMeterId));

        // 获取上次表底
        if (currentReading == null) {
            throw new BusinessException("current reading is required: " + waterMeterId);
        }
        BigDecimal previousReading = getLastReadingBefore(waterMeterId, readingDate);
        BigDecimal usageAmount = currentReading.subtract(previousReading);
        if (usageAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("current reading is lower than previous reading: " + waterMeterId);
        }

        // 计费用水量：优先使用传入值，否则使用实际用量
        boolean hasManualChargeableUsage = chargeableUsage != null;
        BigDecimal effectiveChargeable = hasManualChargeableUsage ? chargeableUsage : usageAmount;

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

        if (isAbnormal && !hasManualChargeableUsage) {
            return reading;
        }

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
                            BigDecimal paid = existingBill.getActualWaterPaid() == null
                                    ? BigDecimal.ZERO
                                    : existingBill.getActualWaterPaid();
                            if (paid.compareTo(waterCharge) > 0) {
                                BigDecimal excess = paid.subtract(waterCharge).setScale(2, RoundingMode.HALF_UP);
                                paid = waterCharge;
                                saveOverpaymentAdjustment(waterMeterId, existingBill, excess);
                            } else if (paid.compareTo(waterCharge) < 0) {
                                BigDecimal prepaidDeduction = getPrepaidDeduction(waterMeterId, waterCharge.subtract(paid));
                                if (prepaidDeduction.compareTo(BigDecimal.ZERO) > 0) {
                                    paid = paid.add(prepaidDeduction);
                                    savePrepaymentDeduction(waterMeterId, existingBill, prepaidDeduction);
                                }
                            }
                            existingBill.setWaterAmount(effectiveChargeable);
                            existingBill.setWaterCharge(waterCharge);
                            existingBill.setActualWaterPaid(paid);
                            existingBill.setWaterStatus(calcBillStatus(paid, waterCharge));
                            waterBillRepository.save(existingBill);
                        },
                        () -> {
                            // 新建账单
                            BigDecimal prepaidDeduction = getPrepaidDeduction(waterMeterId, waterCharge);
                            WaterBill bill = WaterBill.builder()
                                    .waterMeterId(waterMeterId)
                                    .billYear(billYear)
                                    .billMonth(billMonth)
                                    .waterAmount(effectiveChargeable)
                                    .waterCharge(waterCharge)
                                    .actualWaterPaid(prepaidDeduction)
                                    .waterStatus(calcBillStatus(prepaidDeduction, waterCharge))
                                    .note(buildPrepaymentNote(prepaidDeduction))
                                    .build();
                            bill = waterBillRepository.save(bill);
                            savePrepaymentDeduction(waterMeterId, bill, prepaidDeduction);
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
        paid = paid == null ? BigDecimal.ZERO : paid;
        total = total == null ? BigDecimal.ZERO : total;
        if (paid.compareTo(BigDecimal.ZERO) == 0) return "未收";
        if (paid.compareTo(total) >= 0) return "已收";
        return "部分收";
    }

    private BigDecimal getPrepaidDeduction(String waterMeterId, BigDecimal waterCharge) {
        if (waterCharge.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal balance = prepaymentLogRepository.getBalance(waterMeterId);
        if (balance == null || balance.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return balance.min(waterCharge).setScale(2, RoundingMode.HALF_UP);
    }

    private String buildPrepaymentNote(BigDecimal prepaidDeduction) {
        if (prepaidDeduction.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return "已使用预存抵扣 " + prepaidDeduction.setScale(2, RoundingMode.HALF_UP) + " 元";
    }

    private void savePrepaymentDeduction(String waterMeterId, WaterBill bill, BigDecimal prepaidDeduction) {
        if (prepaidDeduction.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        prepaymentLogRepository.save(PrepaymentLog.builder()
                .waterMeterId(waterMeterId)
                .amount(prepaidDeduction.negate())
                .type("AUTO_DEDUCT")
                .billId(bill.getId())
                .remark(bill.getBillYear() + "年" + bill.getBillMonth()
                        + "月水费自动抵扣预存 " + prepaidDeduction.setScale(2, RoundingMode.HALF_UP) + " 元")
                .build());
    }

    private void saveOverpaymentAdjustment(String waterMeterId, WaterBill bill, BigDecimal excess) {
        if (excess.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        prepaymentLogRepository.save(PrepaymentLog.builder()
                .waterMeterId(waterMeterId)
                .amount(excess)
                .type("BILL_RECALC_OVERPAYMENT")
                .billId(bill.getId())
                .remark("bill recalculation overpayment " + excess.setScale(2, RoundingMode.HALF_UP))
                .build());
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
                .setMaxResults(Math.min(Math.max(limit, 0), 500))
                .getResultList();
    }
}
