package com.example.watermanagement.dto;

import lombok.Data;

@Data
public class MonthLockRequest {
    private Integer billYear;
    private Integer billMonth;
    private String operator;
    private String note;
}
