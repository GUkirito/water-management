package com.example.watermanagement.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 抄表记录表 — readings
 * <p>
 * 每次抄表生成一条记录，包含本次表底、上次表底和计算出的用量。
 * 同一水表同一天只能有一条记录（water_meter_id + reading_date 唯一约束）。
 * 异常检测（倒转、突增）在业务层处理，此处仅存储标记。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "readings", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"water_meter_id", "reading_date"})
})
public class Reading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 水表编号（关联 households.water_meter_id） */
    @Column(name = "water_meter_id", nullable = false)
    private String waterMeterId;

    /** 抄表日期 */
    @Column(name = "reading_date", nullable = false, columnDefinition = "DATE")
    private LocalDate readingDate;

    /** 本次表底数 */
    @Column(name = "current_reading", precision = 10, scale = 2)
    private BigDecimal currentReading;

    /** 上次表底数（从上次抄表记录中获取，避免复杂联查） */
    @Column(name = "previous_reading", precision = 10, scale = 2)
    private BigDecimal previousReading;

    /** 本次用量 = currentReading - previousReading */
    @Column(name = "usage_amount", precision = 10, scale = 2)
    private BigDecimal usageAmount;

    /** 是否异常（倒转 or 突增） */
    @Column(name = "is_abnormal", columnDefinition = "BOOLEAN DEFAULT 0")
    @Builder.Default
    private Boolean isAbnormal = false;

    /** 异常原因说明（如"表底倒转"、"用量突增"） */
    @Column(name = "abnormal_reason")
    private String abnormalReason;
}
