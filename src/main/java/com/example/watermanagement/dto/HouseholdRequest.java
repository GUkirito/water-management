package com.example.watermanagement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新增/更新村民的请求体
 */
@Data
public class HouseholdRequest {

    /** 户主姓名 */
    @NotBlank(message = "户主姓名不能为空")
    private String householdName;

    /** 联系电话 */
    private String phone;

    /** 所属村名 */
    @NotBlank(message = "村名不能为空")
    private String villageName;

    /** 水表编号 */
    @NotBlank(message = "水表编号不能为空")
    private String waterMeterId;

    /** 材料费总额（不传则默认 1500） */
    private BigDecimal materialFeeTotal;
}
