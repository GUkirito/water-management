package com.example.watermanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountingRepairExecuteRequest {
    @NotBlank(message = "问题类型不能为空")
    private String issueType;

    @NotBlank(message = "记录类型不能为空")
    private String refType;

    @NotNull(message = "记录编号不能为空")
    private Long refId;

    @NotBlank(message = "请重新查看处理方式后再操作")
    private String previewToken;

    @NotBlank(message = "操作人不能为空")
    private String operator;

    @NotBlank(message = "修复原因不能为空")
    private String reason;
}
