package com.example.watermanagement.service.impl;

import com.example.watermanagement.dto.AccountingRepairExecuteRequest;
import com.example.watermanagement.dto.AccountingRepairPreview;
import com.example.watermanagement.dto.AccountingRepairPreviewRequest;
import com.example.watermanagement.dto.AccountingRepairResult;
import com.example.watermanagement.exception.BusinessException;
import com.example.watermanagement.service.AccountingRepairService;
import com.example.watermanagement.service.DatabaseSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class AccountingRepairServiceImpl implements AccountingRepairService {

    private final AccountingRepairPlanner planner;
    private final AccountingRepairPreviewRegistry previewRegistry;
    private final DatabaseSnapshotService databaseSnapshotService;
    private final AccountingRepairTransactionExecutor transactionExecutor;

    @Override
    @Transactional(readOnly = true)
    public AccountingRepairPreview preview(AccountingRepairPreviewRequest request) {
        AccountingRepairPlan plan = planner.plan(request);
        AccountingRepairPreview preview = plan.preview();
        if (preview.isRepairable()) {
            preview.setPreviewToken(previewRegistry.register(
                    preview.getIssueType(), preview.getRefType(), preview.getRefId(), plan.signature()));
        }
        return preview;
    }

    @Override
    public AccountingRepairResult execute(AccountingRepairExecuteRequest request) {
        validateOperatorAndReason(request);
        AccountingRepairPreviewRequest previewRequest = new AccountingRepairPreviewRequest(
                request.getIssueType(), request.getRefType(), request.getRefId());
        AccountingRepairPlan plan = planner.plan(previewRequest);
        previewRegistry.consume(
                request.getPreviewToken(), request.getIssueType(), request.getRefType(), request.getRefId(),
                plan.signature());
        if (!plan.preview().isRepairable()) {
            throw new BusinessException("账务数据已变化，请重新查看处理方式后再操作");
        }

        Path snapshot = databaseSnapshotService.createVerifiedSnapshot("accounting-repair");
        AccountingRepairExecutionOutcome outcome = transactionExecutor.execute(plan, request, snapshot);
        return new AccountingRepairResult(
                snapshot.getFileName().toString(),
                outcome.audit().getId(),
                plan.preview().getBefore(),
                plan.preview().getAfter(),
                outcome.remainingIssues());
    }

    private void validateOperatorAndReason(AccountingRepairExecuteRequest request) {
        if (request == null || request.getPreviewToken() == null || request.getPreviewToken().isBlank()) {
            throw new BusinessException("请重新查看处理方式后再操作");
        }
        if (request == null || request.getOperator() == null || request.getOperator().isBlank()) {
            throw new BusinessException("操作人不能为空");
        }
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException("修复原因不能为空");
        }
    }
}
