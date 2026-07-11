package com.example.watermanagement.service.impl;

import com.example.watermanagement.dto.AccountingHealthIssue;
import com.example.watermanagement.dto.AccountingRepairExecuteRequest;
import com.example.watermanagement.dto.AccountingRepairPreviewRequest;
import com.example.watermanagement.entity.AccountingRepairAudit;
import com.example.watermanagement.entity.PrepaymentLog;
import com.example.watermanagement.entity.WaterBill;
import com.example.watermanagement.exception.BusinessException;
import com.example.watermanagement.repository.AccountingRepairAuditRepository;
import com.example.watermanagement.repository.PrepaymentLogRepository;
import com.example.watermanagement.repository.WaterBillRepository;
import com.example.watermanagement.service.AccountingHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class AccountingRepairTransactionExecutor {

    private static final Set<String> REPAIR_RELATED_ISSUE_TYPES = Set.of(
            "PAYMENT_TOTAL_MISMATCH",
            "INCONSISTENT_WATER_BILL_STATUS");

    private final AccountingRepairPlanner planner;
    private final PrepaymentLogRepository prepaymentLogRepository;
    private final WaterBillRepository waterBillRepository;
    private final AccountingRepairAuditRepository auditRepository;
    private final AccountingHealthService accountingHealthService;
    private final ObjectMapper objectMapper;

    @Transactional
    AccountingRepairExecutionOutcome execute(
            AccountingRepairPlan expectedPlan,
            AccountingRepairExecuteRequest request,
            Path snapshot) {
        AccountingRepairPlan currentPlan = planner.plan(new AccountingRepairPreviewRequest(
                request.getIssueType(), request.getRefType(), request.getRefId()));
        if (!currentPlan.preview().isRepairable()
                || !currentPlan.signature().equals(expectedPlan.signature())) {
            throw new BusinessException("数据已变化，修复已取消，请重新预览");
        }

        for (LogMove move : currentPlan.logMoves()) {
            PrepaymentLog log = prepaymentLogRepository.findById(move.logId())
                    .orElseThrow(() -> new BusinessException("预存抵扣流水已不存在"));
            if (!Objects.equals(move.expectedWaterMeterId(), log.getWaterMeterId())
                    || !Objects.equals(move.expectedType(), log.getType())
                    || !AccountingRepairValues.decimalEquals(move.expectedAmount(), log.getAmount())
                    || !Objects.equals(move.fromBillId(), log.getBillId())) {
                throw new BusinessException("预存抵扣流水数据已变化，修复已取消");
            }
            log.setBillId(move.toBillId());
            prepaymentLogRepository.save(log);
        }
        prepaymentLogRepository.flush();

        for (BillUpdate update : currentPlan.billUpdates()) {
            WaterBill bill = waterBillRepository.findById(update.before().id())
                    .orElseThrow(() -> new BusinessException("受影响账单已不存在"));
            verifyBillUnchanged(bill, update.before());
            if (update.changed()) {
                bill.setActualWaterPaid(update.after().actual());
                bill.setWaterStatus(update.after().status());
                waterBillRepository.save(bill);
            }
        }
        waterBillRepository.flush();

        AccountingRepairAudit audit = auditRepository.saveAndFlush(AccountingRepairAudit.builder()
                .issueType(request.getIssueType())
                .targetType(request.getRefType())
                .targetId(request.getRefId())
                .beforeJson(toJson(currentPlan.preview().getBefore()))
                .afterJson(toJson(currentPlan.preview().getAfter()))
                .operator(request.getOperator().trim())
                .reason(request.getReason().trim())
                .snapshotPath(snapshot.toAbsolutePath().normalize().toString())
                .build());

        List<AccountingHealthIssue> remainingIssues = accountingHealthService.check();
        Set<Long> affectedBillIds = currentPlan.billUpdates().stream()
                .map(update -> update.before().id())
                .collect(Collectors.toUnmodifiableSet());
        boolean relatedIssueRemains = remainingIssues.stream().anyMatch(issue ->
                "water_bill".equals(issue.getRefType())
                        && affectedBillIds.contains(issue.getRefId())
                        && REPAIR_RELATED_ISSUE_TYPES.contains(issue.getType()));
        if (relatedIssueRemains) {
            throw new BusinessException("账务修复复检未通过，所有修改已回滚");
        }
        return new AccountingRepairExecutionOutcome(audit, List.copyOf(remainingIssues));
    }

    private void verifyBillUnchanged(WaterBill bill, BillState expected) {
        if (!Objects.equals(bill.getWaterMeterId(), expected.meter())
                || !Objects.equals(bill.getBillYear(), expected.year())
                || !Objects.equals(bill.getBillMonth(), expected.month())
                || !AccountingRepairValues.decimalEquals(bill.getWaterCharge(), expected.charge())
                || !AccountingRepairValues.decimalEquals(bill.getActualWaterPaid(), expected.actual())
                || !Objects.equals(bill.getWaterStatus(), expected.status())
                || !Objects.equals(bill.getVersion(), expected.version())) {
            throw new BusinessException("账单数据已变化，修复已取消");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new BusinessException("生成修复审计记录失败");
        }
    }
}
