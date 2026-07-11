package com.example.watermanagement.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ConfigUpdateDTO {

    @NotNull(message = "waterPrice is required")
    @DecimalMin(value = "0.01", message = "waterPrice must be at least 0.01")
    private BigDecimal waterPrice;

    @NotNull(message = "abnormalThreshold is required")
    @Min(value = 0, message = "abnormalThreshold must be at least 0")
    private Integer abnormalThreshold;
}
