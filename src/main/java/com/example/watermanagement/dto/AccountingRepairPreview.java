package com.example.watermanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountingRepairPreview {
    private String issueType;
    private String refType;
    private Long refId;
    private boolean repairable;
    private String cause;
    private Map<String, Object> before;
    private Map<String, Object> after;
    private List<AccountingRepairAffectedRecord> affectedRecords;
    private boolean snapshotRequired;
    private String previewToken;
}
