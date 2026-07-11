package com.example.watermanagement.service.impl;

import com.example.watermanagement.dto.ReadingBatchItem;
import com.example.watermanagement.dto.ConfigUpdateDTO;
import com.example.watermanagement.dto.ReadingExportRow;
import com.example.watermanagement.dto.HistoricalReadingImportRow;
import com.example.watermanagement.dto.ReadingImportDetail;
import com.example.watermanagement.dto.ReadingRowDTO;
import com.example.watermanagement.entity.Household;
import com.example.watermanagement.entity.Reading;
import com.example.watermanagement.entity.WaterBill;
import com.example.watermanagement.exception.BusinessException;
import com.example.watermanagement.repository.HouseholdRepository;
import com.example.watermanagement.repository.MonthLockRepository;
import com.example.watermanagement.repository.ReadingRepository;
import com.example.watermanagement.repository.WaterBillRepository;
import com.example.watermanagement.service.ReadingService;
import com.example.watermanagement.service.ReadingWriteService;
import com.example.watermanagement.service.AccountingWriteGuard;
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
    private final MonthLockRepository monthLockRepository;
    private final EntityManager entityManager;
    private final ReadingWriteService readingWriteService;
    private final AccountingWriteGuard accountingWriteGuard;

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
        Set<String> seenMeters = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            ReadingExportRow row = rows.get(i);
            ReadingImportDetail detail = previewStandardReading(row, i + 1, readingDate);
            if (isWritable(detail) && !seenMeters.add(row.getWaterMeterId())) {
                fail(detail, "导入文件中同一水表存在重复行");
            }
            details.add(detail);
        }

        if (details.stream().anyMatch(detail -> "fail".equals(detail.getStatus()))) {
            return buildImportResult(details);
        }

        for (int i = 0; i < rows.size(); i++) {
            ReadingImportDetail detail = details.get(i);
            if (!isWritable(detail)) continue;
            ReadingExportRow row = rows.get(i);
            Reading reading = readingWriteService.saveImportedRow(
                    row.getWaterMeterId(), row.getCurrentReading(), readingDate,
                    null, null, waterPrice, abnormalThreshold);
            detail.setStatus(Boolean.TRUE.equals(reading.getIsAbnormal()) ? "abnormal" : "success");
            detail.setMessage(Boolean.TRUE.equals(reading.getIsAbnormal())
                    ? "导入成功，已标记异常：" + reading.getAbnormalReason()
                    : "导入成功");
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
        List<ReadingImportDetail> details = previewHistoricalRows(rows, false);
        if (details.stream().anyMatch(detail -> "fail".equals(detail.getStatus()))) {
            return buildImportResult(details);
        }
        for (ReadingImportDetail detail : details) {
            if (!isWritable(detail)) continue;
            readingRepository.save(Reading.builder()
                    .waterMeterId(detail.getWaterMeterId())
                    .readingDate(detail.getReadingDate())
                    .currentReading(detail.getCurrentReading())
                    .previousReading(detail.getPreviousReading())
                    .usageAmount(detail.getUsageAmount())
                    .isAbnormal("abnormal".equals(detail.getStatus()))
                    .abnormalReason("abnormal".equals(detail.getStatus()) ? detail.getMessage() : null)
                    .note("历史导入")
                    .build());
            detail.setMessage("历史表底导入成功");
        }
        entityManager.flush();
        return buildImportResult(details);
    }

    // ==================== 批量保存 ====================

    @Override
    public Map<String, Object> batchSave(List<ReadingBatchItem> items, LocalDate readingDate) {
        int total = 0, abnormal = 0;
        List<String> errors = new ArrayList<>();
        List<Map<String, Object>> details = new ArrayList<>();

        for (ReadingBatchItem item : items) {
            try {
                Reading reading = readingWriteService.saveRow(
                        item.getWaterMeterId(), item.getCurrentReading(),
                        readingDate, item.getChargeableUsage(), item.getNote(), waterPrice, abnormalThreshold);
                total++;
                String status = Boolean.TRUE.equals(reading.getIsAbnormal()) ? "abnormal" : "success";
                if (Boolean.TRUE.equals(reading.getIsAbnormal())) {
                    abnormal++;
                }
                details.add(batchDetail(item.getWaterMeterId(), status,
                        Boolean.TRUE.equals(reading.getIsAbnormal()) ? reading.getAbnormalReason() : "保存成功"));
            } catch (Exception e) {
                errors.add(item.getWaterMeterId() + ": " + e.getMessage());
                details.add(batchDetail(item.getWaterMeterId(), "fail", e.getMessage()));
            }
        }

        log.info("批量保存抄表完成: 成功{}条, 异常{}条", total, abnormal);
        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("abnormal", abnormal);
        result.put("fail", errors.size());
        result.put("errors", errors);
        result.put("details", details);
        return result;
    }

    // ==================== 单个录入 ====================

    @Override
    public Reading singleSave(String waterMeterId, BigDecimal currentReading, LocalDate readingDate) {
        return readingWriteService.saveRow(waterMeterId, currentReading, readingDate,
                null, null, waterPrice, abnormalThreshold);
    }

    private Map<String, Object> batchDetail(String waterMeterId, String status, String message) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("waterMeterId", waterMeterId);
        householdRepository.findByWaterMeterId(waterMeterId)
                .ifPresent(household -> detail.put("householdName", household.getHouseholdName()));
        detail.put("status", status);
        detail.put("message", message);
        return detail;
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
        try {
            accountingWriteGuard.requireNoLaterReading(row.getWaterMeterId(), readingDate);
            Reading sameMonth = accountingWriteGuard.findReadingInMonth(row.getWaterMeterId(), readingDate)
                    .orElse(null);
            if (sameMonth != null && !sameMonth.getReadingDate().equals(readingDate)) {
                return fail(detail, "同月已有抄表记录: " + sameMonth.getReadingDate());
            }
        } catch (BusinessException e) {
            return fail(detail, e.getMessage());
        }

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
        Set<String> importedMonths = new HashSet<>();
        List<ReadingImportDetail> details = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            HistoricalReadingImportRow row = sorted.get(i);
            ReadingImportDetail detail = previewHistoricalReading(row, i + 1);
            if (isWritable(detail)) {
                String monthKey = detail.getWaterMeterId() + ":" + detail.getReadingDate().withDayOfMonth(1);
                if (!importedMonths.add(monthKey)) {
                    fail(detail, "导入文件中同一水表同月存在多条抄表记录");
                }
            }
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
        if (monthLockRepository.existsByBillYearAndBillMonth(readingDate.getYear(), readingDate.getMonthValue())) {
            return fail(detail, "该月份已月结锁定，不能导入历史抄表记录");
        }
        if (readingRepository.findByWaterMeterIdAndReadingDate(row.getWaterMeterId(), readingDate).isPresent()) {
            detail.setStatus("skip");
            detail.setMessage("同一水表同一天已有抄表记录，已跳过");
            return detail;
        }
        detail.setHouseholdName(household.get().getHouseholdName());
        detail.setVillageName(household.get().getVillageName());
        try {
            accountingWriteGuard.requireNoLaterReading(row.getWaterMeterId(), readingDate);
            Reading sameMonth = accountingWriteGuard.findReadingInMonth(row.getWaterMeterId(), readingDate)
                    .orElse(null);
            if (sameMonth != null) {
                return fail(detail, "同月已有抄表记录: " + sameMonth.getReadingDate());
            }
        } catch (BusinessException e) {
            return fail(detail, e.getMessage());
        }
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
