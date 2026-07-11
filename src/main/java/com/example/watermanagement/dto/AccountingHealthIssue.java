package com.example.watermanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountingHealthIssue {
    private String type;
    private String severity;
    private String refType;
    private Long refId;
    private String waterMeterId;
    private String message;
}
