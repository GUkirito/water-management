package com.example.watermanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountingRepairPreviewRequest {
    @NotBlank(message = "问题类型不能为空")
    private String issueType;

    @NotBlank(message = "记录类型不能为空")
    private String refType;

    @NotNull(message = "记录编号不能为空")
    private Long refId;
}
