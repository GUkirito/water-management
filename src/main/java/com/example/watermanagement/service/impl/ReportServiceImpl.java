package com.example.watermanagement.service.impl;

import com.example.watermanagement.dto.VillageCollectionSummaryRow;
import com.example.watermanagement.dto.WaterBillReportRow;
import com.example.watermanagement.entity.Household;
import com.example.watermanagement.entity.Payment;
import com.example.watermanagement.entity.Reading;
import com.example.watermanagement.entity.WaterBill;
import com.example.watermanagement.exception.BusinessException;
import com.example.watermanagement.repository.HouseholdRepository;
import com.example.watermanagement.repository.PaymentRepository;
import com.example.watermanagement.repository.ReadingRepository;
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
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final WaterBillRepository waterBillRepository;
    private final HouseholdRepository householdRepository;
    private final ReadingRepository readingRepository;
    private final PaymentRepository paymentRepository;

    @Override
    public List<WaterBillReportRow> getWaterBillReportData(int year, int month,
                                                            List<String> villageNames) {
        List<WaterBill> bills = waterBillRepository.findByBillYearAndBillMonth(year, month);
        List<Household> households = getHouseholds(villageNames);
        Map<String, Household> meterMap = households.stream()
                .collect(Collectors.toMap(Household::getWaterMeterId, h -> h, (a, b) -> a));
        Map<Long, String> paymentMethodMap = buildPaymentMethodMap(bills);

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
                            .paymentMethod(paymentMethodMap.getOrDefault(b.getId(), "-"))
                            .note(b.getNote())
                            .build();
                })
                .collect(Collectors.toList());
    }

    private Map<Long, String> buildPaymentMethodMap(List<WaterBill> bills) {
        List<Long> billIds = bills.stream()
                .map(WaterBill::getId)
                .filter(id -> id != null)
                .toList();
        if (billIds.isEmpty()) {
            return Map.of();
        }

        return paymentRepository.findByBillTypeAndBillIdInOrderByPaidDateDesc("water", billIds).stream()
                .filter(payment -> payment.getPaymentMethod() != null && !payment.getPaymentMethod().isBlank())
                .collect(Collectors.groupingBy(
                        Payment::getBillId,
                        Collectors.collectingAndThen(
                                Collectors.toMap(
                                        Payment::getPaymentMethod,
                                        Payment::getPaymentMethod,
                                        (first, ignored) -> first,
                                        LinkedHashMap::new
                                ),
                                methods -> String.join(", ", methods.keySet())
                        )
                ));
    }

    @Override
    public void exportWaterBillReport(int year, int month,
                                      List<String> villageNames,
                                      HttpServletResponse response) throws IOException {
        List<WaterBillReportRow> data = getWaterBillReportData(year, month, villageNames);
        if (data.isEmpty()) {
            throw new BusinessException("没有符合条件的数据");
        }
        String filename = year + "年" + month + "月水费报表";
        ExcelUtil.export(response, filename, WaterBillReportRow.class, data);
        log.info("Export water bill report: year={}, month={}, rows={}", year, month, data.size());
    }

    @Override
    public List<VillageCollectionSummaryRow> getVillageCollectionSummary(int year, int month) {
        List<Household> households = householdRepository.findByIsActiveTrue();
        Map<String, List<Household>> householdsByVillage = households.stream()
                .collect(Collectors.groupingBy(Household::getVillageName));
        Map<String, Household> meterMap = households.stream()
                .collect(Collectors.toMap(Household::getWaterMeterId, h -> h, (a, b) -> a));

        List<WaterBill> bills = waterBillRepository.findByBillYearAndBillMonth(year, month);
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1).minusDays(1);
        List<Reading> abnormalReadings = readingRepository.findByReadingDateBetween(start, end).stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsAbnormal()))
                .toList();

        return householdsByVillage.entrySet().stream()
                .map(entry -> buildVillageSummary(entry.getKey(), entry.getValue(), meterMap, bills, abnormalReadings))
                .sorted((a, b) -> a.getVillageName().compareTo(b.getVillageName()))
                .collect(Collectors.toList());
    }

    private List<Household> getHouseholds(List<String> villageNames) {
        if (villageNames != null && !villageNames.isEmpty()) {
            return householdRepository.findByVillageNameInAndIsActiveTrue(villageNames);
        }
        return householdRepository.findByIsActiveTrue();
    }

    private VillageCollectionSummaryRow buildVillageSummary(String villageName,
                                                            List<Household> villageHouseholds,
                                                            Map<String, Household> meterMap,
                                                            List<WaterBill> bills,
                                                            List<Reading> abnormalReadings) {
        Set<String> villageMeterIds = villageHouseholds.stream()
                .map(Household::getWaterMeterId)
                .collect(Collectors.toSet());

        BigDecimal waterCharge = BigDecimal.ZERO;
        BigDecimal actualWaterPaid = BigDecimal.ZERO;
        Set<String> unpaidMeters = new HashSet<>();

        for (WaterBill bill : bills) {
            Household household = meterMap.get(bill.getWaterMeterId());
            if (household == null || !villageName.equals(household.getVillageName())) {
                continue;
            }
            BigDecimal charge = bill.getWaterCharge() != null ? bill.getWaterCharge() : BigDecimal.ZERO;
            BigDecimal paid = bill.getActualWaterPaid() != null ? bill.getActualWaterPaid() : BigDecimal.ZERO;
            waterCharge = waterCharge.add(charge);
            actualWaterPaid = actualWaterPaid.add(paid);
            if (charge.subtract(paid).compareTo(BigDecimal.ZERO) > 0) {
                unpaidMeters.add(bill.getWaterMeterId());
            }
        }

        long abnormalCount = abnormalReadings.stream()
                .filter(r -> villageMeterIds.contains(r.getWaterMeterId()))
                .count();
        BigDecimal unpaidAmount = waterCharge.subtract(actualWaterPaid).max(BigDecimal.ZERO);
        BigDecimal collectionRate = waterCharge.compareTo(BigDecimal.ZERO) > 0
                ? actualWaterPaid.multiply(new BigDecimal("100")).divide(waterCharge, 2, RoundingMode.HALF_UP)
                : new BigDecimal("100.00");

        return VillageCollectionSummaryRow.builder()
                .villageName(villageName)
                .householdCount(villageHouseholds.size())
                .waterCharge(waterCharge)
                .actualWaterPaid(actualWaterPaid)
                .unpaidAmount(unpaidAmount)
                .unpaidHouseholdCount(unpaidMeters.size())
                .collectionRate(collectionRate)
                .abnormalReadingCount(abnormalCount)
                .build();
    }
}
