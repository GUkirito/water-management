package com.example.watermanagement.service;

import com.example.watermanagement.dto.AccountingHealthIssue;

import java.util.List;

public interface AccountingHealthService {
    List<AccountingHealthIssue> check();
}
