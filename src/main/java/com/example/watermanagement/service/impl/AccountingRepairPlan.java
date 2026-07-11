package com.example.watermanagement.service.impl;

import com.example.watermanagement.dto.AccountingHealthIssue;
import com.example.watermanagement.dto.AccountingRepairPreview;
import com.example.watermanagement.entity.AccountingRepairAudit;

import java.math.BigDecimal;
import java.util.List;

record AccountingRepairPlan(
        AccountingRepairPreview preview,
        List<LogMove> logMoves,
        List<BillUpdate> billUpdates,
        List<PaymentState> payments,
        List<PrepaymentState> prepaymentLogs,
        String signature) {
}

record LogMove(
        Long logId,
        String expectedWaterMeterId,
        String expectedType,
        BigDecimal expectedAmount,
        Long fromBillId,
        Long toBillId) {
}

record BillState(
        Long id,
        String meter,
        Integer year,
        Integer month,
        BigDecimal charge,
        BigDecimal actual,
        String status,
        Long version,
        BigDecimal remainingDue) {
}

record BillUpdate(BillState before, BillState after) {
    boolean changed() {
        return !AccountingRepairValues.decimalEquals(before.actual(), after.actual())
                || !java.util.Objects.equals(before.status(), after.status());
    }
}

record PaymentState(Long id, Long billId, BigDecimal amount) {
}

record PrepaymentState(Long id, Long billId, String meter, String type, BigDecimal amount) {
}

record AccountingRepairExecutionOutcome(
        AccountingRepairAudit audit,
        List<AccountingHealthIssue> remainingIssues) {
}

final class AccountingRepairValues {
    private AccountingRepairValues() {
    }

    static boolean decimalEquals(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) return left == right;
        return left.compareTo(right) == 0;
    }
}
