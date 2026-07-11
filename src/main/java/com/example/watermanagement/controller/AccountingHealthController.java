package com.example.watermanagement.controller;

import com.example.watermanagement.dto.AccountingHealthIssue;
import com.example.watermanagement.dto.AccountingAdjustmentRequest;
import com.example.watermanagement.dto.AccountingRepairExecuteRequest;
import com.example.watermanagement.dto.AccountingRepairPreview;
import com.example.watermanagement.dto.AccountingRepairPreviewRequest;
import com.example.watermanagement.dto.AccountingRepairResult;
import com.example.watermanagement.dto.ApiResponse;
import com.example.watermanagement.dto.MonthLockRequest;
import com.example.watermanagement.entity.AccountingAdjustment;
import com.example.watermanagement.entity.MonthLock;
import com.example.watermanagement.service.AccountingControlService;
import com.example.watermanagement.service.AccountingHealthService;
import com.example.watermanagement.service.AccountingRepairService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/accounting")
@RequiredArgsConstructor
public class AccountingHealthController {

    private final AccountingHealthService accountingHealthService;
    private final AccountingControlService accountingControlService;
    private final AccountingRepairService accountingRepairService;

    @GetMapping("/health-check")
    public ApiResponse<List<AccountingHealthIssue>> check() {
        return ApiResponse.ok(accountingHealthService.check());
    }

    @PostMapping("/health/repair/preview")
    public ApiResponse<AccountingRepairPreview> previewRepair(
            @Valid @RequestBody AccountingRepairPreviewRequest request) {
        return ApiResponse.ok(accountingRepairService.preview(request));
    }

    @PostMapping("/health/repair/execute")
    public ApiResponse<AccountingRepairResult> executeRepair(
            @Valid @RequestBody AccountingRepairExecuteRequest request) {
        return ApiResponse.ok("账务修复完成", accountingRepairService.execute(request));
    }

    @GetMapping("/month-locks")
    public ApiResponse<List<MonthLock>> listMonthLocks() {
        return ApiResponse.ok(accountingControlService.listMonthLocks());
    }

    @PostMapping("/month-locks")
    public ApiResponse<MonthLock> lockMonth(@RequestBody MonthLockRequest request) {
        return ApiResponse.ok("月结锁定成功", accountingControlService.lockMonth(
                request.getBillYear(),
                request.getBillMonth(),
                request.getOperator(),
                request.getNote()));
    }

    @DeleteMapping("/month-locks")
    public ApiResponse<MonthLock> unlockMonth(
            @RequestParam Integer billYear,
            @RequestParam Integer billMonth,
            @RequestParam String operator,
            @RequestParam String reason) {
        return ApiResponse.ok("已解除月结锁定",
                accountingControlService.unlockMonth(billYear, billMonth, operator, reason));
    }

    @GetMapping("/adjustments")
    public ApiResponse<List<AccountingAdjustment>> listAdjustments() {
        return ApiResponse.ok(accountingControlService.listAdjustments());
    }

    @PostMapping("/adjustments/water-bills/{id}")
    public ApiResponse<AccountingAdjustment> adjustWaterBill(
            @PathVariable Long id,
            @RequestBody AccountingAdjustmentRequest request) {
        return ApiResponse.ok("水费调账成功", accountingControlService.adjustWaterBill(
                id,
                request.getAfterAmount(),
                request.getReason(),
                request.getOperator()));
    }

    @PostMapping("/adjustments/material-records/{id}")
    public ApiResponse<AccountingAdjustment> adjustMaterialRecord(
            @PathVariable Long id,
            @RequestBody AccountingAdjustmentRequest request) {
        return ApiResponse.ok("材料费调账成功", accountingControlService.adjustMaterialRecord(
                id,
                request.getAfterAmount(),
                request.getReason(),
                request.getOperator()));
    }
}
