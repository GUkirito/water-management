package com.example.watermanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 批量抄表时单条记录的请求体
 */
@Data
public class ReadingBatchItem {

    /** 水表编号 */
    @NotBlank(message = "水表编号不能为空")
    private String waterMeterId;

    /** 本次表底数 */
    @NotNull(message = "本次表底不能为空")
    private BigDecimal currentReading;

    /** 计费用水量（可选，不传则使用实际用量 currentReading - previousReading） */
    @DecimalMin(value = "0.0", message = "计费用水量不能为负数")
    private BigDecimal chargeableUsage;

    /** 备注 */
    private String note;
}
