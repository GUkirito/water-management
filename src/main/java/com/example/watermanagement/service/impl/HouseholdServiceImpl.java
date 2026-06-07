package com.example.watermanagement.service.impl;

import com.example.watermanagement.dto.HouseholdExportRow;
import com.example.watermanagement.dto.HouseholdRequest;
import com.example.watermanagement.entity.Household;
import com.example.watermanagement.entity.MaterialBill;
import com.example.watermanagement.exception.BusinessException;
import com.example.watermanagement.repository.HouseholdRepository;
import com.example.watermanagement.repository.MaterialBillRepository;
import com.example.watermanagement.service.HouseholdService;
import com.example.watermanagement.util.ExcelUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 村民管理 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HouseholdServiceImpl implements HouseholdService {

    private final HouseholdRepository householdRepository;
    private final MaterialBillRepository materialBillRepository;

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

        // 同时创建材料费账单
        MaterialBill materialBill = MaterialBill.builder()
                .waterMeterId(household.getWaterMeterId())
                .totalFee(household.getMaterialFeeTotal())
                .actualPaid(java.math.BigDecimal.ZERO)
                .status("未收")
                .build();
        materialBillRepository.save(materialBill);

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
            // 同步更新材料费账单的水表编号
            materialBillRepository.findByWaterMeterId(household.getWaterMeterId())
                    .ifPresent(mb -> {
                        mb.setWaterMeterId(request.getWaterMeterId());
                        materialBillRepository.save(mb);
                    });
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
        household.setIsActive(false);
        householdRepository.save(household);
        log.info("软删除村民: {} [水表: {}]", household.getHouseholdName(), household.getWaterMeterId());
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

            materialBillRepository.save(MaterialBill.builder()
                    .waterMeterId(household.getWaterMeterId())
                    .totalFee(household.getMaterialFeeTotal())
                    .actualPaid(java.math.BigDecimal.ZERO)
                    .status("未收")
                    .build());
            count++;
        }

        log.info("批量导入村民: {} 户", count);
        Map<String, Object> result = new HashMap<>();
        result.put("total", count);
        result.put("message", "成功导入 " + count + " 户");
        return result;
    }
}
