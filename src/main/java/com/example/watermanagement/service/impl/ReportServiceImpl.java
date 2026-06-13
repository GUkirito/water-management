package com.example.watermanagement.service.impl;

import com.example.watermanagement.dto.WaterBillReportRow;
import com.example.watermanagement.entity.Household;
import com.example.watermanagement.entity.WaterBill;
import com.example.watermanagement.exception.BusinessException;
import com.example.watermanagement.repository.HouseholdRepository;
import com.example.watermanagement.repository.WaterBillRepository;
import com.example.watermanagement.service.ReportService;
import com.example.watermanagement.util.ExcelUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 报表管理 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final WaterBillRepository waterBillRepository;
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

    // ==================== 私有方法 ====================

    private List<Household> getHouseholds(List<String> villageNames) {
        if (villageNames != null && !villageNames.isEmpty()) {
            return householdRepository.findByVillageNameInAndIsActiveTrue(villageNames);
        }
        return householdRepository.findByIsActiveTrue();
    }
}
