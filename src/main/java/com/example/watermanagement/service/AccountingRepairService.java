package com.example.watermanagement.service;

import com.example.watermanagement.dto.AccountingRepairExecuteRequest;
import com.example.watermanagement.dto.AccountingRepairPreview;
import com.example.watermanagement.dto.AccountingRepairPreviewRequest;
import com.example.watermanagement.dto.AccountingRepairResult;

public interface AccountingRepairService {
    AccountingRepairPreview preview(AccountingRepairPreviewRequest request);

    AccountingRepairResult execute(AccountingRepairExecuteRequest request);
}
