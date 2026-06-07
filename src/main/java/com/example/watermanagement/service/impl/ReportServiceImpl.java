package com.example.watermanagement.service.impl;

import com.example.watermanagement.dto.MaterialSummaryRow;
import com.example.watermanagement.dto.VillageCollectionRateRow;
import com.example.watermanagement.dto.WaterBillReportRow;
import com.example.watermanagement.entity.Household;
import com.example.watermanagement.entity.MaterialBill;
import com.example.watermanagement.entity.WaterBill;
import com.example.watermanagement.exception.BusinessException;
import com.example.watermanagement.repository.HouseholdRepository;
import com.example.watermanagement.repository.MaterialBillRepository;
import com.example.watermanagement.repository.WaterBillRepository;
import com.example.watermanagement.service.ReportService;
import com.example.watermanagement.util.ExcelUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 报表管理 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final WaterBillRepository waterBillRepository;
    private final MaterialBillRepository materialBillRepository;
    private final HouseholdRepository householdRepository;

    // ==================== 水费月报表 ====================

    @Override
    public List<WaterBillReportRow> getWaterBillReportData(int year, int month,
                                                            List<String> villageNames) {
        List<WaterBill> bills = waterBillRepository.findByBillYearAndBillMonth(year, month);

        // 构建水表编号 → 村民信息 的映射
        List<Household> households = getHouseholds(villageNames);
        Map<String, Household> meterMap = households.stream()
                .collect(Collectors.toMap(Household::getWaterMeterId, h -> h, (a, b) -> a));

        // 只为指定村的水表生成报表行
        return bills.stream()
                .filter(b -> meterMap.containsKey(b.getWaterMeterId()))
                .map(b -> {
                    Household h = meterMap.get(b.getWaterMeterId());
                    return WaterBillReportRow.builder()
                            .villageName(h.getVillageName())
                            .waterMeterId(b.getWaterMeterId())
                            .householdName(h.getHouseholdName())
                            .waterAmount(b.getWaterAmount())
                            .waterCharge(b.getWaterCharge())
                            .actualWaterPaid(b.getActualWaterPaid())
                            .waterStatus(b.getWaterStatus())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    public void exportWaterBillReport(int year, int month,
                                       List<String> villageNames,
                                       HttpServletResponse response) throws IOException {
        List<WaterBillReportRow> data = getWaterBillReportData(year, month, villageNames);
        if (data.isEmpty()) {
            throw new BusinessException("没有符合条件的数据");
        }
        String filename = year + "年" + month + "月_水费报表";
        ExcelUtil.export(response, filename, WaterBillReportRow.class, data);
        log.info("导出水费月报表: {}年{}月, {}条", year, month, data.size());
    }

    // ==================== 材料费统计表 ====================

    @Override
    public List<MaterialSummaryRow> getMaterialSummaryData(List<String> villageNames) {
        List<Household> households = getHouseholds(villageNames);
        Map<String, Household> meterMap = households.stream()
                .collect(Collectors.toMap(Household::getWaterMeterId, h -> h, (a, b) -> a));

        List<MaterialSummaryRow> rows = new ArrayList<>();
        for (Household h : households) {
            materialBillRepository.findByWaterMeterId(h.getWaterMeterId())
                    .ifPresent(mb -> {
                        BigDecimal unpaid = mb.getTotalFee().subtract(mb.getActualPaid());
                        rows.add(MaterialSummaryRow.builder()
                                .waterMeterId(h.getWaterMeterId())
                                .householdName(h.getHouseholdName())
                                .villageName(h.getVillageName())
                                .totalFee(mb.getTotalFee())
                                .actualPaid(mb.getActualPaid())
                                .unpaid(unpaid)
                                .status(mb.getStatus())
                                .build());
                    });
        }
        return rows;
    }

    @Override
    public void exportMaterialSummary(List<String> villageNames,
                                       HttpServletResponse response) throws IOException {
        List<MaterialSummaryRow> data = getMaterialSummaryData(villageNames);
        if (data.isEmpty()) {
            throw new BusinessException("没有符合条件的数据");
        }
        String filename = "材料费统计表";
        ExcelUtil.export(response, filename, MaterialSummaryRow.class, data);
        log.info("导出材料费统计表: {}条", data.size());
    }

    // ==================== 各村收缴率 ====================

    @Override
    public List<VillageCollectionRateRow> getVillageCollectionRates(int year, int month) {
        List<WaterBill> bills = waterBillRepository.findByBillYearAndBillMonth(year, month);

        List<Household> households = householdRepository.findByIsActiveTrue();
        Map<String, String> meterToVillage = households.stream()
                .collect(Collectors.toMap(Household::getWaterMeterId, Household::getVillageName, (a, b) -> a));

        // 按村分组统计 [应收总额, 实收总额]
        Map<String, BigDecimal[]> villageStats = new LinkedHashMap<>();
        for (WaterBill bill : bills) {
            String village = meterToVillage.getOrDefault(bill.getWaterMeterId(), "未知");
            villageStats.computeIfAbsent(village, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal[] stats = villageStats.get(village);
            stats[0] = stats[0].add(bill.getWaterCharge() != null ? bill.getWaterCharge() : BigDecimal.ZERO);
            stats[1] = stats[1].add(bill.getActualWaterPaid() != null ? bill.getActualWaterPaid() : BigDecimal.ZERO);
        }

        return villageStats.entrySet().stream()
                .map(e -> {
                    BigDecimal[] s = e.getValue();
                    double rate = s[0].compareTo(BigDecimal.ZERO) > 0
                            ? s[1].multiply(new BigDecimal("100")).divide(s[0], 1, RoundingMode.HALF_UP).doubleValue()
                            : 100.0;
                    return VillageCollectionRateRow.builder()
                            .villageName(e.getKey())
                            .totalCharge(s[0])
                            .totalPaid(s[1])
                            .collectionRate(rate)
                            .build();
                })
                .sorted(Comparator.comparingDouble(VillageCollectionRateRow::getCollectionRate))
                .collect(Collectors.toList());
    }

    // ==================== 私有方法 ====================

    private List<Household> getHouseholds(List<String> villageNames) {
        if (villageNames != null && !villageNames.isEmpty()) {
            return householdRepository.findByVillageNameInAndIsActiveTrue(villageNames);
        }
        return householdRepository.findByIsActiveTrue();
    }
}
