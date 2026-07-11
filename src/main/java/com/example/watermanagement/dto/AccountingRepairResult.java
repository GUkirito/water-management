package com.example.watermanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountingRepairResult {
    private String snapshotName;
    private Long auditId;
    private Map<String, Object> before;
    private Map<String, Object> after;
    private List<AccountingHealthIssue> remainingIssues;
}
