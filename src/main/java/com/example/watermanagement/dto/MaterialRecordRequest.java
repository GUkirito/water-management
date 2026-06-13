package com.example.watermanagement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class MaterialRecordRequest {

    @NotBlank(message = "户主姓名不能为空")
    private String householdName;

    private String waterMeterId;

    private String phone;

    @NotBlank(message = "村组名称不能为空")
    private String villageName;

    private BigDecimal totalFee;

    private LocalDate paidAt;

    private String note;
}
