package com.example.watermanagement.service.impl;

import com.example.watermanagement.dto.AccountingRepairAffectedRecord;
import com.example.watermanagement.dto.AccountingRepairPreview;
import com.example.watermanagement.dto.AccountingRepairPreviewRequest;
import com.example.watermanagement.entity.Payment;
import com.example.watermanagement.entity.PrepaymentLog;
import com.example.watermanagement.entity.WaterBill;
import com.example.watermanagement.repository.PaymentRepository;
import com.example.watermanagement.repository.PrepaymentLogRepository;
import com.example.watermanagement.repository.WaterBillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class AccountingRepairPlanner {

    private static final String PAYMENT_MISMATCH = "PAYMENT_TOTAL_MISMATCH";
    private static final String STATUS_MISMATCH = "INCONSISTENT_WATER_BILL_STATUS";

    private final WaterBillRepository waterBillRepository;
    private final PaymentRepository paymentRepository;
    private final PrepaymentLogRepository prepaymentLogRepository;

    @Transactional(readOnly = true)
    AccountingRepairPlan plan(AccountingRepairPreviewRequest request) {
        if (request == null || request.getIssueType() == null || request.getRefType() == null
                || request.getRefId() == null) {
            return unsupported(request, "修复请求不完整");
        }
        if (!"water_bill".equals(request.getRefType())
                || !(PAYMENT_MISMATCH.equals(request.getIssueType())
                || STATUS_MISMATCH.equals(request.getIssueType()))) {
            return unsupported(request, "该类问题无法安全自动修复，请进入对应业务页面人工核对");
        }

        WaterBill target = waterBillRepository.findById(request.getRefId()).orElse(null);
        if (target == null) {
            return unsupported(request, "目标水费账单不存在，数据可能已经变化");
        }

        List<LogMove> moves = new ArrayList<>();
        Set<Long> affectedBillIds = new LinkedHashSet<>();
        affectedBillIds.add(target.getId());
        if (PAYMENT_MISMATCH.equals(request.getIssueType())) {
            List<PrepaymentLog> targetLogs = sortedLogs(prepaymentLogRepository.findByBillId(target.getId()));
            String invalidReason = validatePrepayments(targetLogs);
            if (invalidReason != null) return unsupported(request, invalidReason);
            for (PrepaymentLog log : targetLogs) {
                if (Objects.equals(log.getWaterMeterId(), target.getWaterMeterId())) continue;
                List<WaterBill> candidates = waterBillRepository.findAllByWaterMeterIdAndBillYearAndBillMonth(
                        log.getWaterMeterId(), target.getBillYear(), target.getBillMonth());
                if (candidates.size() != 1) {
                    return unsupported(request,
                            "错绑流水对应的同水表同月份账单无法唯一确定，系统不会猜测修复目标");
                }
                WaterBill correct = candidates.getFirst();
                moves.add(new LogMove(log.getId(), log.getWaterMeterId(), log.getType(), log.getAmount(),
                        target.getId(), correct.getId()));
                affectedBillIds.add(correct.getId());
            }
        }

        List<WaterBill> bills = new ArrayList<>();
        List<PaymentState> payments = new ArrayList<>();
        Map<Long, PrepaymentState> prepaymentsById = new LinkedHashMap<>();
        for (Long billId : affectedBillIds) {
            WaterBill bill = waterBillRepository.findById(billId).orElse(null);
            if (bill == null) return unsupported(request, "受影响账单不存在，数据可能已经变化");
            if (bill.getWaterCharge() == null || bill.getWaterCharge().signum() < 0) {
                return unsupported(request, "账单应收金额为空或小于零，需人工核对后处理");
            }
            bills.add(bill);

            List<Payment> billPayments = paymentRepository.findByBillTypeAndBillId("water", billId);
            for (Payment payment : billPayments) {
                if (payment.getAmount() == null || payment.getAmount().signum() <= 0) {
                    return unsupported(request, "付款流水金额为空或不大于零，需人工核对后处理");
                }
                payments.add(new PaymentState(payment.getId(), payment.getBillId(), payment.getAmount()));
            }

            List<PrepaymentLog> billLogs = sortedLogs(prepaymentLogRepository.findByBillId(billId));
            String invalidReason = validatePrepayments(billLogs);
            if (invalidReason != null) return unsupported(request, invalidReason);
            for (PrepaymentLog log : billLogs) {
                prepaymentsById.put(log.getId(), prepaymentState(log));
            }
        }

        Map<Long, LogMove> movesByLogId = moves.stream()
                .collect(Collectors.toMap(LogMove::logId, Function.identity()));
        for (PrepaymentState log : prepaymentsById.values()) {
            WaterBill linkedBill = bills.stream().filter(bill -> bill.getId().equals(log.billId()))
                    .findFirst().orElseThrow();
            if (!Objects.equals(log.meter(), linkedBill.getWaterMeterId())
                    && !movesByLogId.containsKey(log.id())) {
                return unsupported(request, "受影响账单还存在其他跨水表预存流水，需人工核对后处理");
            }
        }

        payments.sort(Comparator.comparing(PaymentState::id));
        List<PrepaymentState> prepayments = prepaymentsById.values().stream()
                .sorted(Comparator.comparing(PrepaymentState::id)).toList();
        List<BillUpdate> updates = new ArrayList<>();
        for (WaterBill bill : bills) {
            BigDecimal actual = calculateActualPaid(bill, payments, prepayments, movesByLogId);
            if (actual.compareTo(bill.getWaterCharge()) > 0) {
                return unsupported(request, "有效缴费和抵扣合计超过应收金额，需人工核对后处理");
            }
            String afterStatus = status(bill.getWaterCharge(), actual);
            boolean changed = !AccountingRepairValues.decimalEquals(bill.getActualWaterPaid(), actual)
                    || !Objects.equals(bill.getWaterStatus(), afterStatus);
            BigDecimal beforeRemaining = remaining(bill.getWaterCharge(), nvl(bill.getActualWaterPaid()));
            BillState before = new BillState(
                    bill.getId(), bill.getWaterMeterId(), bill.getBillYear(), bill.getBillMonth(),
                    bill.getWaterCharge(), bill.getActualWaterPaid(), bill.getWaterStatus(), bill.getVersion(),
                    beforeRemaining);
            BillState after = new BillState(
                    bill.getId(), bill.getWaterMeterId(), bill.getBillYear(), bill.getBillMonth(),
                    bill.getWaterCharge(), actual, afterStatus,
                    changed ? nextVersion(bill.getVersion()) : bill.getVersion(),
                    remaining(bill.getWaterCharge(), actual));
            updates.add(new BillUpdate(before, after));
        }
        updates.sort(Comparator.comparing(update -> update.before().id()));

        boolean valuesChanged = updates.stream().anyMatch(BillUpdate::changed);
        if (moves.isEmpty() && !valuesChanged) {
            return unsupported(request, "问题已不存在或数据已变化，请重新执行健康检查");
        }

        Map<String, Object> before = stateMap(target.getId(), moves, updates, payments, prepayments, false);
        Map<String, Object> after = stateMap(target.getId(), moves, updates, payments, prepayments, true);
        String cause = moves.isEmpty()
                ? "账单记录的实收金额或收款状态与有效缴费流水不一致，将按有效流水重新计算"
                : "预存抵扣流水误关联到其他住户的账单，将重绑到唯一的同水表同月份账单并重算两张账单";
        AccountingRepairPreview preview = new AccountingRepairPreview(
                request.getIssueType(), request.getRefType(), request.getRefId(), true, cause,
                before, after, affectedRecords(moves, updates), true);
        String signature = moves + "|" + updates + "|" + payments + "|" + prepayments;
        return new AccountingRepairPlan(preview, List.copyOf(moves), List.copyOf(updates),
                List.copyOf(payments), List.copyOf(prepayments), signature);
    }

    private BigDecimal calculateActualPaid(
            WaterBill bill,
            List<PaymentState> payments,
            List<PrepaymentState> prepayments,
            Map<Long, LogMove> movesByLogId) {
        BigDecimal paymentTotal = payments.stream()
                .filter(payment -> payment.billId().equals(bill.getId()))
                .map(PaymentState::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deductionTotal = prepayments.stream()
                .filter(log -> effectiveBillId(log, movesByLogId).equals(bill.getId()))
                .filter(log -> Objects.equals(log.meter(), bill.getWaterMeterId()))
                .map(log -> log.amount().negate()).reduce(BigDecimal.ZERO, BigDecimal::add);
        return paymentTotal.add(deductionTotal);
    }

    private Long effectiveBillId(PrepaymentState log, Map<Long, LogMove> movesByLogId) {
        LogMove move = movesByLogId.get(log.id());
        return move == null ? log.billId() : move.toBillId();
    }

    private String validatePrepayments(List<PrepaymentLog> logs) {
        for (PrepaymentLog log : logs) {
            if (!"AUTO_DEDUCT".equals(log.getType())
                    || log.getAmount() == null || log.getAmount().signum() >= 0) {
                return "账单关联的预存流水类型或金额不合法，需人工核对后处理";
            }
        }
        return null;
    }

    private List<PrepaymentLog> sortedLogs(List<PrepaymentLog> logs) {
        return logs.stream().sorted(Comparator.comparing(PrepaymentLog::getId)).toList();
    }

    private PrepaymentState prepaymentState(PrepaymentLog log) {
        return new PrepaymentState(
                log.getId(), log.getBillId(), log.getWaterMeterId(), log.getType(), log.getAmount());
    }

    private Map<String, Object> stateMap(
            Long targetId,
            List<LogMove> moves,
            List<BillUpdate> updates,
            List<PaymentState> payments,
            List<PrepaymentState> prepayments,
            boolean after) {
        Map<Long, LogMove> movesByLogId = moves.stream()
                .collect(Collectors.toMap(LogMove::logId, Function.identity()));
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("wrongBillId", targetId);
        state.put("bills", updates.stream().map(update -> billMap(after ? update.after() : update.before())).toList());
        state.put("payments", payments.stream().map(this::paymentMap).toList());
        state.put("prepaymentLogs", prepayments.stream()
                .map(log -> prepaymentMap(log, after ? effectiveBillId(log, movesByLogId) : log.billId()))
                .toList());
        state.put("movedLogs", moves.stream().map(move -> movedLogMap(move, after)).toList());
        return state;
    }

    private Map<String, Object> billMap(BillState bill) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", bill.id());
        value.put("meter", bill.meter());
        value.put("year", bill.year());
        value.put("month", bill.month());
        value.put("charge", bill.charge());
        value.put("actual", bill.actual());
        value.put("status", bill.status());
        value.put("version", bill.version());
        value.put("remainingDue", bill.remainingDue());
        return value;
    }

    private Map<String, Object> paymentMap(PaymentState payment) {
        return Map.of("id", payment.id(), "billId", payment.billId(), "amount", payment.amount());
    }

    private Map<String, Object> prepaymentMap(PrepaymentState log, Long billId) {
        return Map.of(
                "id", log.id(), "billId", billId, "meter", log.meter(),
                "type", log.type(), "amount", log.amount());
    }

    private Map<String, Object> movedLogMap(LogMove move, boolean after) {
        return Map.of(
                "id", move.logId(),
                "billId", after ? move.toBillId() : move.fromBillId(),
                "meter", move.expectedWaterMeterId(),
                "type", move.expectedType(),
                "amount", move.expectedAmount(),
                "fromBillId", move.fromBillId(),
                "toBillId", move.toBillId());
    }

    private List<AccountingRepairAffectedRecord> affectedRecords(
            List<LogMove> moves, List<BillUpdate> updates) {
        List<AccountingRepairAffectedRecord> records = new ArrayList<>();
        moves.forEach(move -> records.add(new AccountingRepairAffectedRecord(
                "prepayment_log", move.logId(), "预存抵扣流水将改绑账单")));
        updates.forEach(update -> records.add(new AccountingRepairAffectedRecord(
                "water_bill", update.before().id(), "将按有效流水重算实收金额和状态")));
        return records;
    }

    private AccountingRepairPlan unsupported(AccountingRepairPreviewRequest request, String cause) {
        String issueType = request == null ? null : request.getIssueType();
        String refType = request == null ? null : request.getRefType();
        Long refId = request == null ? null : request.getRefId();
        AccountingRepairPreview preview = new AccountingRepairPreview(
                issueType, refType, refId, false, cause, Map.of(), Map.of(), List.of(), false);
        return new AccountingRepairPlan(preview, List.of(), List.of(), List.of(), List.of(), "UNREPAIRABLE");
    }

    private String status(BigDecimal charge, BigDecimal actual) {
        if (charge.signum() == 0) return "无需缴费";
        if (actual.signum() == 0) return "未收";
        if (actual.compareTo(charge) >= 0) return "已收";
        return "部分收";
    }

    private BigDecimal remaining(BigDecimal charge, BigDecimal actual) {
        return charge.subtract(actual).max(BigDecimal.ZERO);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Long nextVersion(Long version) {
        return version == null ? 0L : version + 1;
    }
}
