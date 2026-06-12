package com.example.watermanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 材料费收款请求体
 */
@Data
public class MaterialCollectRequest {

    /** 水表编号 */
    @NotBlank(message = "水表编号不能为空")
    private String waterMeterId;

    /** 本次实收金额 */
    @NotNull(message = "收费金额不能为空")
    private BigDecimal amount;

    /** 收款日期 */
    @NotNull(message = "收费日期不能为空")
    private LocalDate paidDate;

    /** 收款人 */
    @NotBlank(message = "收款人不能为空")
    private String collector;

    /** 备注 */
    private String note;
}
