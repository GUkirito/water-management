package com.example.watermanagement.service.impl;

import com.example.watermanagement.dto.HouseholdExportRow;
import com.example.watermanagement.dto.HouseholdRequest;
import com.example.watermanagement.entity.Household;
import com.example.watermanagement.exception.BusinessException;
import com.example.watermanagement.repository.HouseholdRepository;
import com.example.watermanagement.repository.PaymentRepository;
import com.example.watermanagement.repository.ReadingRepository;
import com.example.watermanagement.repository.WaterBillRepository;
import com.example.watermanagement.service.HouseholdService;
import com.example.watermanagement.util.ExcelUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 村民管理 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HouseholdServiceImpl implements HouseholdService {

    private final HouseholdRepository householdRepository;
    private final ReadingRepository readingRepository;
    private final WaterBillRepository waterBillRepository;
    private final PaymentRepository paymentRepository;

    @Override
    public Page<Household> list(List<String> villageNames, String waterMeterId, Pageable pageable) {
        boolean hasVillages = villageNames != null && !villageNames.isEmpty();
        boolean hasKeyword = waterMeterId != null && !waterMeterId.isBlank();

        if (hasVillages && hasKeyword) {
            return householdRepository
                    .findByVillageNameInAndWaterMeterIdContainingAndIsActiveTrue(
                            villageNames, waterMeterId, pageable);
        } else if (hasVillages) {
            return householdRepository
                    .findByVillageNameInAndIsActiveTrue(villageNames, pageable);
        } else if (hasKeyword) {
            return householdRepository
                    .findByWaterMeterIdContainingAndIsActiveTrue(waterMeterId, pageable);
        } else {
            return householdRepository.findByIsActiveTrue(pageable);
        }
    }

    @Override
    public Household getById(Long id) {
        return householdRepository.findById(id)
                .orElseThrow(() -> new BusinessException("村民不存在: id=" + id));
    }

    @Override
    @Transactional
    public Household add(HouseholdRequest request) {
        // 校验水表编号唯一性
        if (householdRepository.existsByWaterMeterId(request.getWaterMeterId())) {
            throw new BusinessException("水表编号已存在: " + request.getWaterMeterId());
        }

        // 保存村民信息
        Household household = Household.builder()
                .householdName(request.getHouseholdName())
                .phone(request.getPhone())
                .villageName(request.getVillageName())
                .waterMeterId(request.getWaterMeterId())
                .materialFeeTotal(request.getMaterialFeeTotal() != null
                        ? request.getMaterialFeeTotal()
                        : new java.math.BigDecimal("1500.00"))
                .isActive(true)
                .build();
        household = householdRepository.save(household);

        log.info("新增村民: {} [水表: {}]", household.getHouseholdName(), household.getWaterMeterId());
        return household;
    }

    @Override
    @Transactional
    public Household update(Long id, HouseholdRequest request) {
        Household household = getById(id);

        // 如果修改了水表编号，检查新编号是否已被占用
        if (!household.getWaterMeterId().equals(request.getWaterMeterId())) {
            if (householdRepository.existsByWaterMeterId(request.getWaterMeterId())) {
                throw new BusinessException("水表编号已被占用: " + request.getWaterMeterId());
            }
        }

        household.setHouseholdName(request.getHouseholdName());
        household.setPhone(request.getPhone());
        household.setVillageName(request.getVillageName());
        household.setWaterMeterId(request.getWaterMeterId());
        if (request.getMaterialFeeTotal() != null) {
            household.setMaterialFeeTotal(request.getMaterialFeeTotal());
        }

        log.info("更新村民: {} [水表: {}]", household.getHouseholdName(), household.getWaterMeterId());
        return householdRepository.save(household);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Household household = getById(id);
        String meterId = household.getWaterMeterId();
        readingRepository.findByWaterMeterIdInOrderByReadingDateDesc(List.of(meterId))
                .forEach(r -> readingRepository.delete(r));
        waterBillRepository.findByWaterMeterId(meterId)
                .forEach(wb -> {
                    paymentRepository.findByBillTypeAndBillIdIn("water", List.of(wb.getId()))
                            .forEach(p -> paymentRepository.delete(p));
                    waterBillRepository.delete(wb);
                });
        householdRepository.delete(household);
        log.info("物理删除村民: {} [水表: {}]", household.getHouseholdName(), meterId);
    }

    @Override
    @Transactional
    public void batchDelete(List<Long> ids) {
        for (Long id : ids) { delete(id); }
        log.info("批量物理删除村民: {} 户", ids.size());
    }

    @Override
    @Transactional
    public void deleteByVillage(String villageName) {
        List<Household> list = householdRepository.findByVillageNameInAndIsActiveTrue(List.of(villageName));
        for (Household h : list) { delete(h.getId()); }
        log.info("按村物理删除: {} [{}户]", villageName, list.size());
    }

    @Override
    @Transactional
    public void batchUpdateVillage(List<Long> ids, String villageName) {
        for (Long id : ids) {
            householdRepository.findById(id).ifPresent(h -> {
                h.setVillageName(villageName);
                householdRepository.save(h);
            });
        }
        log.info("批量改村组: {} 户 → {}", ids.size(), villageName);
    }

    @Override
    public List<Household> findAllActive() {
        return householdRepository.findByIsActiveTrue();
    }

    @Override
    public void exportToExcel(List<String> villageNames, String waterMeterId,
                               jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        List<Household> households;
        if (villageNames != null && !villageNames.isEmpty()) {
            households = householdRepository.findByVillageNameInAndIsActiveTrue(villageNames);
        } else {
            households = householdRepository.findByIsActiveTrue();
        }

        List<HouseholdExportRow> rows = households.stream()
                .map(h -> HouseholdExportRow.builder()
                        .householdName(h.getHouseholdName())
                        .phone(h.getPhone())
                        .villageName(h.getVillageName())
                        .waterMeterId(h.getWaterMeterId())
                        .materialFeeTotal(h.getMaterialFeeTotal())
                        .build())
                .collect(Collectors.toList());

        ExcelUtil.export(response, "村民信息", HouseholdExportRow.class, rows);
        log.info("导出村民信息: {} 户", rows.size());
    }

    @Override
    @Transactional
    public Map<String, Object> importFromExcel(java.io.InputStream inputStream) {
        List<HouseholdExportRow> rows = ExcelUtil.read(inputStream, HouseholdExportRow.class);

        // ===== 全量唯一性校验 =====
        Set<String> excelIds = new HashSet<>();
        List<String> conflicts = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            String id = rows.get(i).getWaterMeterId();
            if (id == null || id.isBlank()) {
                conflicts.add("第" + (i + 1) + "行：水表编号为空");
            } else if (!excelIds.add(id)) {
                conflicts.add("第" + (i + 1) + "行：水表编号 " + id + " 在文件中重复");
            }
        }

        for (HouseholdExportRow row : rows) {
            if (row.getWaterMeterId() != null && !row.getWaterMeterId().isBlank()
                    && householdRepository.existsByWaterMeterId(row.getWaterMeterId())) {
                conflicts.add("水表编号 " + row.getWaterMeterId()
                        + "（" + row.getHouseholdName() + "）已在系统中存在");
            }
        }

        if (!conflicts.isEmpty()) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("conflicts", conflicts);
            errorResult.put("message", "存在 " + conflicts.size() + " 个冲突，已中止导入");
            return errorResult;
        }

        // ===== 全部通过，批量写入 =====
        int count = 0;
        for (HouseholdExportRow row : rows) {
            Household household = Household.builder()
                    .householdName(row.getHouseholdName())
                    .phone(row.getPhone())
                    .villageName(row.getVillageName())
                    .waterMeterId(row.getWaterMeterId())
                    .materialFeeTotal(row.getMaterialFeeTotal() != null
                            ? row.getMaterialFeeTotal()
                            : new java.math.BigDecimal("1500.00"))
                    .isActive(true)
                    .build();
            householdRepository.save(household);

            count++;
        }

        log.info("批量导入村民: {} 户", count);
        Map<String, Object> result = new HashMap<>();
        result.put("total", count);
        result.put("message", "成功导入 " + count + " 户");
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> importFromWaterRegister(java.io.InputStream inputStream) {
        // 使用 EasyExcel 同步读取所有行（返回 List<Map<Integer,String>>）
        List<Object> rawRows = com.alibaba.excel.EasyExcel.read(inputStream)
                .sheet().doReadSync();

        int inserted = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        if (rawRows.size() < 3) {
            errors.add("Excel 文件格式不正确：至少需要 3 行");
            Map<String, Object> result = new HashMap<>();
            result.put("inserted", 0);
            result.put("skipped", 0);
            result.put("errors", errors);
            return result;
        }

        // 提取村名：遍历前几行找含"村（居）委会"的行
        String villageName = "";
        for (int vi = 0; vi < Math.min(rawRows.size(), 3); vi++) {
            String s = rowToString(rawRows.get(vi));
            Pattern p = Pattern.compile("委会\\s*[：:]\\s*(\\S+?)\\s*村");
            Matcher m = p.matcher(s);
            if (m.find()) { villageName = m.group(1).trim(); break; }
        }
        if (villageName.isEmpty()) {
            errors.add("无法从前3行提取村名");
        }

        // 动态检测表头行（含"序号"或"户主姓名"）
        int headerRow = -1;
        for (int hi = 0; hi < Math.min(rawRows.size(), 4); hi++) {
            Map<Integer, String> hr = toRowMap(rawRows.get(hi));
            if (hr != null) {
                String v0 = hr.get(0); String v1 = hr.get(1);
                if (("序号".equals(v0) || "序号".equals(v1))
                        && ("户主姓名".equals(v1) || "户主姓名".equals(hr.get(2)))) {
                    headerRow = hi; break;
                }
            }
        }
        int dataStart = (headerRow >= 0) ? headerRow + 1 : 3;

        for (int i = dataStart; i < rawRows.size(); i++) {
            Map<Integer, String> rowMap = toRowMap(rawRows.get(i));
            if (rowMap == null) continue;

            String householdName = cellStr(rowMap, 1);
            String waterMeterId = cellStr(rowMap, 2);
            String phone = cellStr(rowMap, 3);

            if (householdName == null || householdName.isBlank()
                    || waterMeterId == null || waterMeterId.isBlank()) {
                errors.add("第" + (i + 1) + "行：户主姓名或表号为空，跳过");
                continue;
            }

            // 检查是否已存在
            if (householdRepository.existsByWaterMeterId(waterMeterId.trim())) {
                skipped++;
                errors.add("跳过：第" + (i + 1) + "行 表号 " + waterMeterId.trim() + " 已存在");
                continue;
            }

            // 创建新户
            Household household = Household.builder()
                    .householdName(householdName.trim())
                    .phone(phone != null ? phone.trim() : "")
                    .villageName(villageName)
                    .waterMeterId(waterMeterId.trim())
                    .materialFeeTotal(new BigDecimal("1500.00"))
                    .isActive(true)
                    .build();
            householdRepository.save(household);

            inserted++;
        }

        log.info("水费登记册导入完成: 新增{}户, 跳过{}户（已存在）, 错误{}条", inserted, skipped, errors.size());
        Map<String, Object> result = new HashMap<>();
        result.put("inserted", inserted);
        result.put("skipped", skipped);
        result.put("errors", errors);
        return result;
    }

    /** 将行对象转为字符串（用于提取村名等） */
    @SuppressWarnings("unchecked")
    private String rowToString(Object rowObj) {
        if (rowObj == null) return "";
        if (rowObj instanceof Map) {
            return ((Map<Integer, String>) rowObj).values().stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.joining());
        }
        if (rowObj instanceof List) {
            return ((List<Object>) rowObj).stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.joining());
        }
        return rowObj.toString();
    }

    /** 将 doReadSync 返回的行对象转为 Map（兼容 List 和 Map 两种格式） */
    @SuppressWarnings("unchecked")
    private Map<Integer, String> toRowMap(Object rowObj) {
        if (rowObj == null) return null;
        if (rowObj instanceof Map) {
            return (Map<Integer, String>) rowObj;
        }
        if (rowObj instanceof List) {
            List<Object> list = (List<Object>) rowObj;
            if (list.isEmpty() || list.stream().allMatch(r -> r == null || r.toString().isBlank())) {
                return null;
            }
            Map<Integer, String> map = new LinkedHashMap<>();
            for (int j = 0; j < list.size(); j++) {
                Object v = list.get(j);
                map.put(j, v != null ? v.toString().trim() : null);
            }
            return map;
        }
        return null;
    }

    /** 从行 Map 安全获取指定列的字符串值 */
    private String cellStr(Map<Integer, String> row, int index) {
        Object obj = row.get(index);
        if (obj == null) return null;
        String val = obj.toString().trim();
        if (val.contains("E") || val.contains("e")) {
            try {
                val = new java.math.BigDecimal(val).toPlainString();
            } catch (NumberFormatException ignored) {}
        }
        return val.isEmpty() ? null : val;
    }

}
