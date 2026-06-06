package com.example.watermanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 缴费请求体
 * <p>
 * 支持合并缴纳多个月份水费：billType="water", billIds=[1,2,3]。
 */
@Data
public class PaymentRequest {

    /** 账单类型：water / material */
    @NotBlank(message = "账单类型不能为空")
    private String billType;

    /** 要缴费的账单 ID 列表（支持合并缴费） */
    @NotEmpty(message = "账单ID列表不能为空")
    private List<Long> billIds;

    /** 本次实收金额 */
    @NotNull(message = "缴费金额不能为空")
    private BigDecimal amount;

    /** 缴费日期 */
    @NotNull(message = "缴费日期不能为空")
    private LocalDate paidDate;

    /** 缴费方式，默认"现金" */
    private String paymentMethod = "现金";

    /** 操作员/收款人 */
    private String operator;

    /** 备注 */
    private String note;
}
