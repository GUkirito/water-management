package com.example.watermanagement.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 材料费账单表 — material_bills
 * <p>
 * 每户一条记录，默认材料费总额 1500 元。
 * status 根据 actual_paid 与 total_fee 的关系判定。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "material_bills")
public class MaterialBill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 水表编号（关联 households.water_meter_id） */
    @Column(name = "water_meter_id", nullable = false)
    private String waterMeterId;

    /** 材料费总额，默认 1500 元 */
    @Column(name = "total_fee", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalFee = new BigDecimal("1500.00");

    /** 已缴金额 */
    @Column(name = "actual_paid", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal actualPaid = BigDecimal.ZERO;

    /** 缴费状态：未收 / 部分收 / 已收 */
    @Column(name = "status")
    @Builder.Default
    private String status = "未收";

    /** 最近缴费日期 */
    @Column(name = "paid_at", columnDefinition = "DATE")
    private LocalDate paidAt;

    /** 收款人 */
    @Column(name = "collector")
    private String collector;

    /** 创建时间 */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
