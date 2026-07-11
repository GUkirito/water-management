package com.example.watermanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountingRepairAffectedRecord {
    private String recordType;
    private Long recordId;
    private String description;
}
