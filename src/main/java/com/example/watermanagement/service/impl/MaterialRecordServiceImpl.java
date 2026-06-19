package com.example.watermanagement.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.read.metadata.ReadSheet;
import com.example.watermanagement.dto.MaterialRecordExportRow;
import com.example.watermanagement.dto.MaterialRecordRequest;
import com.example.watermanagement.entity.MaterialPayment;
import com.example.watermanagement.entity.MaterialRecord;
import com.example.watermanagement.exception.BusinessException;
import com.example.watermanagement.repository.MaterialPaymentRepository;
import com.example.watermanagement.repository.MaterialRecordRepository;
import com.example.watermanagement.service.MaterialRecordService;
import com.example.watermanagement.util.ExcelUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialRecordServiceImpl implements MaterialRecordService {

    private final MaterialRecordRepository recordRepository;
    private final MaterialPaymentRepository paymentRepository;

    @Override
    public Page<MaterialRecord> list(String villageName, String status, String keyword,
                                      LocalDate paidDateFrom, LocalDate paidDateTo,
                                      Pageable pageable) {
        return recordRepository.search(
                villageName, status,
                (keyword != null && !keyword.isBlank()) ? keyword : null,
                paidDateFrom, paidDateTo,
                pageable);
    }

    @Override
    public MaterialRecord getById(Long id) {
        return recordRepository.findById(id)
                .orElseThrow(() -> new BusinessException("材料费记录不存在: id=" + id));
    }

    @Override
    @Transactional
    public MaterialRecord create(MaterialRecordRequest request) {
        if (request.getWaterMeterId() != null && !request.getWaterMeterId().isBlank()
                && recordRepository.existsByWaterMeterId(request.getWaterMeterId())) {
            throw new BusinessException("表号已存在: " + request.getWaterMeterId());
        }
        MaterialRecord record = MaterialRecord.builder()
                .householdName(request.getHouseholdName())
                .waterMeterId(request.getWaterMeterId())
                .phone(request.getPhone())
                .villageName(request.getVillageName())
                .totalFee(request.getTotalFee() != null ? request.getTotalFee() : new BigDecimal("1500.00"))
                .actualPaid(BigDecimal.ZERO)
                .status("未收")
                .note(request.getNote())
                .build();
        record = recordRepository.save(record);
        log.info("新增材料费记录: {} [表号: {}]", record.getHouseholdName(), record.getWaterMeterId());
        return record;
    }

    @Override
    @Transactional
    public MaterialRecord update(Long id, MaterialRecordRequest request) {
        MaterialRecord record = getById(id);
        if (request.getHouseholdName() != null) record.setHouseholdName(request.getHouseholdName());
        if (request.getPhone() != null) record.setPhone(request.getPhone());
        if (request.getVillageName() != null) record.setVillageName(request.getVillageName());
        if (request.getTotalFee() != null) {
            record.setTotalFee(request.getTotalFee());
            record.setStatus(calcStatus(record.getActualPaid(), request.getTotalFee()));
        }
        if (request.getPaidAt() != null) record.setPaidAt(request.getPaidAt());
        if (request.getNote() != null) record.setNote(request.getNote());
        record = recordRepository.save(record);
        log.info("更新材料费记录: id={}", id);
        return record;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        MaterialRecord record = getById(id);
        paymentRepository.deleteByRecordId(id);
        recordRepository.delete(record);
        log.info("删除材料费记录: id={}", id);
    }

    @Override
    @Transactional
    public void batchDelete(List<Long> ids) {
        for (Long id : ids) {
            paymentRepository.deleteByRecordId(id);
            recordRepository.deleteById(id);
        }
        log.info("批量删除材料费记录: {} 条", ids.size());
    }

    @Override
    @Transactional
    public Map<String, Object> importFromExcel(InputStream inputStream, String defaultCollector) {
        int totalRows = 0, inserted = 0, skipped = 0;
        List<String> errors = new ArrayList<>();

        try {
            byte[] fileBytes = inputStream.readAllBytes();

            // 先获取所有 Sheet 名称
            List<String> sheetNames = new ArrayList<>();
            try (InputStream nameStream = new ByteArrayInputStream(fileBytes)) {
                ExcelReader nameReader = EasyExcel.read(nameStream).build();
                for (ReadSheet s : nameReader.excelExecutor().sheetList()) {
                    sheetNames.add(s.getSheetName());
                }
                nameReader.finish();
            }

            for (String sheetName : sheetNames) {
                String villageName = sheetName.trim();
                List<Object> rows;
                try (InputStream dataStream = new ByteArrayInputStream(fileBytes)) {
                    rows = EasyExcel.read(dataStream).sheet(sheetName).doReadSync();
                }
                if (rows == null || rows.size() < 3) continue;

                // 动态检测表头行（含"序号"或"户主姓名"的那行）
                int headerRow = -1;
                for (int hi = 0; hi < Math.min(rows.size(), 4); hi++) {
                    Map<Integer, String> hr = toRowMap(rows.get(hi));
                    if (hr != null) {
                        String v0 = hr.get(0);
                        String v1 = hr.get(1);
                        if (("序号".equals(v0) || "序号".equals(v1))
                                && ("户主姓名".equals(v1) || "户主姓名".equals(hr.get(2)))) {
                            headerRow = hi;
                            break;
                        }
                    }
                }
                int dataStart = (headerRow >= 0) ? headerRow + 1 : 3; // 找不到则 fallback 到第4行

                for (int i = dataStart; i < rows.size(); i++) {
                    Object rowObj = rows.get(i);
                    Map<Integer, String> rowMap = toRowMap(rowObj);
                    if (rowMap == null) continue;

                    totalRows++;
                    String householdName = cellStr(rowMap, 1);
                    String waterMeterId = cellStr(rowMap, 2);
                    String phone = cellStr(rowMap, 3);
                    String feeStr = cellStr(rowMap, 4);
                    String remark = cellStr(rowMap, 5);

                    if (waterMeterId == null || waterMeterId.isBlank()) {
                        errors.add("第" + (i + 1) + "行：表号为空");
                        continue;
                    }

                    if (recordRepository.existsByWaterMeterId(waterMeterId.trim())) {
                        skipped++;
                        errors.add("跳过：第" + (i + 1) + "行 表号 " + waterMeterId.trim() + " 已存在");
                        continue;
                    }

                    BigDecimal totalFee = parseFeeStr(feeStr);
                    MaterialRecord record = MaterialRecord.builder()
                            .householdName(householdName != null ? householdName.trim() : "未知")
                            .waterMeterId(waterMeterId.trim())
                            .phone(phone != null ? phone.trim() : "")
                            .villageName(villageName)
                            .totalFee(totalFee)
                            .actualPaid(BigDecimal.ZERO)
                            .status("未收")
                            .note(null)
                            .build();

                    if (remark != null && remark.contains("已收")) {
                        record.setActualPaid(totalFee);
                        record.setStatus("已收");
                        record.setPaidAt(LocalDate.now());
                        record.setCollector(defaultCollector != null ? defaultCollector : "导入");
                    }

                    record = recordRepository.save(record);

                    if (remark != null && remark.contains("已收")) {
                        paymentRepository.save(MaterialPayment.builder()
                                .recordId(record.getId())
                                .amount(totalFee)
                                .paidDate(LocalDate.now())
                                .collector(defaultCollector != null ? defaultCollector : "导入")
                                .note("批量导入自动标记已收")
                                .build());
                    }

                    inserted++;
                }
            }
        } catch (IOException e) {
            errors.add("读取文件失败: " + e.getMessage());
            log.error("材料费导入失败", e);
        }

        log.info("材料费导入完成: 总数{}行, 成功{}行, 跳过{}行, 错误{}条", totalRows, inserted, skipped, errors.size());

        Map<String, Object> result = new HashMap<>();
        result.put("total", totalRows);
        result.put("inserted", inserted);
        result.put("skipped", skipped);
        result.put("errors", errors);
        return result;
    }

    @Override
    public void exportToExcel(String villageName, String status, String keyword,
                               LocalDate paidDateFrom, LocalDate paidDateTo,
                               HttpServletResponse response) throws IOException {
        List<MaterialRecord> records = recordRepository.searchAll(
                villageName, status,
                (keyword != null && !keyword.isBlank()) ? keyword : null,
                paidDateFrom, paidDateTo);

        List<MaterialRecordExportRow> rows = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            MaterialRecord r = records.get(i);
            rows.add(MaterialRecordExportRow.builder()
                    .index(i + 1)
                    .householdName(r.getHouseholdName())
                    .waterMeterId(r.getWaterMeterId())
                    .phone(r.getPhone())
                    .villageName(r.getVillageName())
                    .totalFee(r.getTotalFee())
                    .actualPaid(r.getActualPaid())
                    .unpaid(r.getTotalFee().subtract(r.getActualPaid()))
                    .status(r.getStatus())
                    .paidAt(r.getPaidAt() != null ? r.getPaidAt().toString() : "")
                    .collector(r.getCollector())
                    .note(r.getNote())
                    .build());
        }

        String filename = "材料费管理_" + LocalDate.now();
        ExcelUtil.export(response, filename, MaterialRecordExportRow.class, rows);
        log.info("导出材料费: {} 条", rows.size());
    }

    @Override
    @Transactional
    public MaterialPayment collect(Long recordId, BigDecimal amount, LocalDate paidDate,
                                    String collector, String note) {
        MaterialRecord record = getById(recordId);
        if ("已收".equals(record.getStatus())) {
            throw new BusinessException("该户材料费已缴清");
        }
        BigDecimal unpaid = record.getTotalFee().subtract(record.getActualPaid());
        if (amount.compareTo(unpaid) > 0) {
            throw new BusinessException("实收金额超过欠费金额（" + unpaid + "）");
        }

        MaterialPayment payment = MaterialPayment.builder()
                .recordId(recordId)
                .amount(amount)
                .paidDate(paidDate)
                .collector(collector)
                .note(note)
                .build();
        payment = paymentRepository.save(payment);

        BigDecimal newPaid = record.getActualPaid().add(amount);
        record.setActualPaid(newPaid);
        record.setStatus(calcStatus(newPaid, record.getTotalFee()));
        record.setPaidAt(paidDate);
        record.setCollector(collector);
        recordRepository.save(record);

        log.info("材料费收费: recordId={}, 金额={}元, 累计已缴={}元", recordId, amount, newPaid);
        return payment;
    }

    @Override
    public List<MaterialPayment> getPayments(Long recordId) {
        return paymentRepository.findByRecordIdOrderByPaidDateDesc(recordId);
    }

    // ===== Private helpers =====

    private String calcStatus(BigDecimal paid, BigDecimal total) {
        if (paid.compareTo(BigDecimal.ZERO) <= 0) return "未收";
        else if (paid.compareTo(total) >= 0) return "已收";
        else return "部分收";
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, String> toRowMap(Object rowObj) {
        if (rowObj == null) return null;
        if (rowObj instanceof Map) return (Map<Integer, String>) rowObj;
        if (rowObj instanceof List) {
            List<Object> list = (List<Object>) rowObj;
            if (list.isEmpty() || list.stream().allMatch(r -> r == null || r.toString().isBlank()))
                return null;
            Map<Integer, String> map = new LinkedHashMap<>();
            for (int j = 0; j < list.size(); j++) {
                Object v = list.get(j);
                map.put(j, v != null ? v.toString().trim() : null);
            }
            return map;
        }
        return null;
    }

    private String cellStr(Map<Integer, String> row, int index) {
        Object obj = row.get(index);
        if (obj == null) return null;
        String val = obj.toString().trim();
        // 修复 EasyExcel 将长数字转科学计数法的问题（如 20250533947 → 2.0250533947E10）
        if (val.contains("E") || val.contains("e")) {
            try {
                val = new java.math.BigDecimal(val).toPlainString();
            } catch (NumberFormatException ignored) {}
        }
        return val.isEmpty() ? null : val;
    }

    private BigDecimal parseFeeStr(String feeStr) {
        try {
            return feeStr != null && !feeStr.isBlank()
                    ? new BigDecimal(feeStr) : new BigDecimal("1500.00");
        } catch (NumberFormatException e) {
            return new BigDecimal("1500.00");
        }
    }
}
