package com.example.watermanagement.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 水费账单表 — water_bills
 * <p>
 * 每月抄表后自动生成一条水费账单。
 * water_status 根据 actual_water_paid 与 water_charge 的关系自动判定：未收 / 部分收 / 已收。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "water_bills", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"water_meter_id", "bill_year", "bill_month"})
})
public class WaterBill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 水表编号（关联 households.water_meter_id） */
    @Column(name = "water_meter_id", nullable = false)
    private String waterMeterId;

    /** 账单年份 */
    @Column(name = "bill_year")
    private Integer billYear;

    /** 账单月份 */
    @Column(name = "bill_month")
    private Integer billMonth;

    /** 当月用水量（吨） */
    @Column(name = "water_amount", precision = 10, scale = 2)
    private BigDecimal waterAmount;

    /** 生成或允许更新该账单时实际使用的水价 */
    @Column(name = "water_price", precision = 10, scale = 2)
    private BigDecimal waterPrice;

    /** 应收水费 = 用水量 × 1.8 元/吨 */
    @Column(name = "water_charge", precision = 10, scale = 2)
    private BigDecimal waterCharge;

    /** 实收水费（累计） */
    @Column(name = "actual_water_paid", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal actualWaterPaid = BigDecimal.ZERO;

    /** 缴费状态：未收 / 部分收 / 已收 */
    @Column(name = "water_status")
    @Builder.Default
    private String waterStatus = "未收";

    /** 账单备注，如预存抵扣说明 */
    @Column(length = 500)
    private String note;

    @Version
    @Column(name = "version")
    private Long version;

    /** 创建时间 */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
