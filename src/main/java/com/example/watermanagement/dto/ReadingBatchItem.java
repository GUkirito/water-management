package com.example.watermanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
}
