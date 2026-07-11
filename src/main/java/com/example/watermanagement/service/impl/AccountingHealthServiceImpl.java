package com.example.watermanagement.service.impl;

import com.example.watermanagement.dto.AccountingHealthIssue;
import com.example.watermanagement.entity.MaterialPayment;
import com.example.watermanagement.entity.Payment;
import com.example.watermanagement.entity.PrepaymentLog;
import com.example.watermanagement.entity.Reading;
import com.example.watermanagement.entity.WaterBill;
import com.example.watermanagement.entity.AccountingAdjustment;
import com.example.watermanagement.repository.AccountingAdjustmentRepository;
import com.example.watermanagement.repository.HouseholdRepository;
import com.example.watermanagement.repository.MaterialPaymentRepository;
import com.example.watermanagement.repository.MaterialRecordRepository;
import com.example.watermanagement.repository.PaymentRepository;
import com.example.watermanagement.repository.PrepaymentLogRepository;
import com.example.watermanagement.repository.ReadingRepository;
import com.example.watermanagement.repository.WaterBillRepository;
import com.example.watermanagement.service.AccountingHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Comparator;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountingHealthServiceImpl implements AccountingHealthService {

    private final WaterBillRepository waterBillRepository;
    private final ReadingRepository readingRepository;
    private final HouseholdRepository householdRepository;
    private final PrepaymentLogRepository prepaymentLogRepository;
    private final PaymentRepository paymentRepository;
    private final MaterialPaymentRepository materialPaymentRepository;
    private final MaterialRecordRepository materialRecordRepository;
    private final AccountingAdjustmentRepository adjustmentRepository;

    @Override
    public List<AccountingHealthIssue> check() {
        List<AccountingHealthIssue> issues = new ArrayList<>();
        checkWaterBills(issues);
        checkReadings(issues);
        checkPrepayments(issues);
        checkPayments(issues);
        checkAdjustments(issues);
        return issues;
    }

    private void checkWaterBills(List<AccountingHealthIssue> issues) {
        List<WaterBill> bills = waterBillRepository.findAll();
        Map<String, List<WaterBill>> groups = new HashMap<>();
        for (WaterBill bill : bills) {
            String key = bill.getWaterMeterId() + "|" + bill.getBillYear() + "|" + bill.getBillMonth();
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(bill);
            if (nvl(bill.getWaterCharge()).compareTo(BigDecimal.ZERO) < 0
                    || nvl(bill.getWaterAmount()).compareTo(BigDecimal.ZERO) < 0) {
                issues.add(issue("NEGATIVE_WATER_BILL_AMOUNT", "ERROR", "water_bill", bill.getId(),
                        bill.getWaterMeterId(), "water bill amount or charge is negative"));
            }
            if (nvl(bill.getActualWaterPaid()).compareTo(nvl(bill.getWaterCharge())) > 0) {
                issues.add(issue("WATER_BILL_OVERPAID", "ERROR", "water_bill", bill.getId(),
                        bill.getWaterMeterId(), "water bill paid amount is greater than charge"));
            }
            if (!expectedStatus(bill).equals(bill.getWaterStatus())) {
                issues.add(issue("INCONSISTENT_WATER_BILL_STATUS", "ERROR", "water_bill", bill.getId(),
                        bill.getWaterMeterId(), "water bill status does not match charge and paid amount"));
            }
            if (householdRepository.findByWaterMeterId(bill.getWaterMeterId()).isEmpty()) {
                issues.add(issue("ORPHAN_WATER_BILL", "ERROR", "water_bill", bill.getId(),
                        bill.getWaterMeterId(), "water bill has no matching household"));
            }
            BigDecimal paymentTotal = paymentRepository.findByBillTypeAndBillId("water", bill.getId()).stream()
                    .map(Payment::getAmount).map(this::nvl).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal prepaymentTotal = prepaymentLogRepository.findAll().stream()
                    .filter(log -> Objects.equals(log.getBillId(), bill.getId()) && nvl(log.getAmount()).signum() < 0)
                    .map(log -> nvl(log.getAmount()).abs()).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (paymentTotal.add(prepaymentTotal).compareTo(nvl(bill.getActualWaterPaid())) != 0) {
                issues.add(issue("PAYMENT_TOTAL_MISMATCH", "ERROR", "water_bill", bill.getId(),
                        bill.getWaterMeterId(), "payment and prepayment total does not match bill paid amount"));
            }
        }
        groups.values().stream()
                .filter(group -> group.size() > 1)
                .flatMap(List::stream)
                .forEach(bill -> issues.add(issue("DUPLICATE_WATER_BILL", "ERROR", "water_bill",
                        bill.getId(), bill.getWaterMeterId(), "duplicate water bill for same meter and month")));
    }

    private void checkReadings(List<AccountingHealthIssue> issues) {
        List<Reading> allReadings = readingRepository.findAll();
        Map<String, List<Reading>> byMeter = allReadings.stream()
                .collect(Collectors.groupingBy(Reading::getWaterMeterId));
        for (Reading reading : allReadings) {
            if (nvl(reading.getUsageAmount()).compareTo(BigDecimal.ZERO) < 0) {
                issues.add(issue("NEGATIVE_READING_USAGE", "ERROR", "reading", reading.getId(),
                        reading.getWaterMeterId(), "reading usage is negative"));
            }
            if (householdRepository.findByWaterMeterId(reading.getWaterMeterId()).isEmpty()) {
                issues.add(issue("ORPHAN_READING", "ERROR", "reading", reading.getId(),
                        reading.getWaterMeterId(), "reading has no matching household"));
            }
        }
        for (List<Reading> meterReadings : byMeter.values()) {
            meterReadings.sort(Comparator.comparing(Reading::getReadingDate));
            Map<String, List<Reading>> monthly = meterReadings.stream().collect(Collectors.groupingBy(
                    reading -> reading.getReadingDate().getYear() + "-" + reading.getReadingDate().getMonthValue()));
            monthly.values().stream().filter(group -> group.size() > 1).flatMap(List::stream)
                    .forEach(reading -> issues.add(issue("DUPLICATE_MONTHLY_READING", "ERROR", "reading",
                            reading.getId(), reading.getWaterMeterId(), "multiple readings exist in same month")));
            for (int index = 1; index < meterReadings.size(); index++) {
                Reading previous = meterReadings.get(index - 1);
                Reading current = meterReadings.get(index);
                if (nvl(current.getPreviousReading()).compareTo(nvl(previous.getCurrentReading())) != 0) {
                    issues.add(issue("BROKEN_READING_CHAIN", "ERROR", "reading", current.getId(),
                            current.getWaterMeterId(), "reading previous value does not match prior current value"));
                }
            }
        }
    }

    private void checkPrepayments(List<AccountingHealthIssue> issues) {
        List<PrepaymentLog> logs = prepaymentLogRepository.findAll();
        for (PrepaymentLog log : logs) {
            if (log.getWaterMeterId() == null
                    || householdRepository.findByWaterMeterId(log.getWaterMeterId()).isEmpty()) {
                issues.add(issue("ORPHAN_PREPAYMENT_LOG", "ERROR", "prepayment_log", log.getId(),
                        log.getWaterMeterId(), "prepayment log has no matching household"));
            }
        }
        logs.stream().collect(Collectors.groupingBy(PrepaymentLog::getWaterMeterId)).forEach((meterId, values) -> {
            BigDecimal balance = values.stream().map(PrepaymentLog::getAmount)
                    .map(this::nvl).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (balance.signum() < 0) {
                issues.add(issue("NEGATIVE_PREPAYMENT_BALANCE", "ERROR", "prepayment_log",
                        values.getFirst().getId(), meterId, "prepayment balance is negative"));
            }
        });
    }

    private void checkAdjustments(List<AccountingHealthIssue> issues) {
        for (AccountingAdjustment adjustment : adjustmentRepository.findAll()) {
            boolean exists = switch (adjustment.getTargetType()) {
                case "WATER_BILL" -> waterBillRepository.existsById(adjustment.getTargetId());
                case "MATERIAL_RECORD" -> materialRecordRepository.existsById(adjustment.getTargetId());
                default -> false;
            };
            if (!exists) {
                issues.add(issue("ORPHAN_ADJUSTMENT_TARGET", "ERROR", "accounting_adjustment",
                        adjustment.getId(), null, "accounting adjustment target does not exist"));
            }
        }
    }

    private String expectedStatus(WaterBill bill) {
        BigDecimal paid = nvl(bill.getActualWaterPaid());
        BigDecimal charge = nvl(bill.getWaterCharge());
        if (charge.signum() == 0 && paid.signum() == 0) return "无需缴费";
        if (paid.signum() <= 0) return "未收";
        if (paid.compareTo(charge) >= 0) return "已收";
        return "部分收";
    }

    private void checkPayments(List<AccountingHealthIssue> issues) {
        for (Payment payment : paymentRepository.findAll()) {
            if (Objects.equals(payment.getBillType(), "water")
                    && !waterBillRepository.existsById(payment.getBillId())) {
                issues.add(issue("ORPHAN_WATER_PAYMENT", "ERROR", "payment", payment.getId(),
                        null, "water payment has no matching water bill"));
            }
        }
        for (MaterialPayment payment : materialPaymentRepository.findAll()) {
            if (!materialRecordRepository.existsById(payment.getRecordId())) {
                issues.add(issue("ORPHAN_MATERIAL_PAYMENT", "ERROR", "material_payment", payment.getId(),
                        null, "material payment has no matching material record"));
            }
        }
    }

    private AccountingHealthIssue issue(String type, String severity, String refType, Long refId,
                                        String waterMeterId, String message) {
        return new AccountingHealthIssue(type, severity, refType, refId, waterMeterId, message);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
