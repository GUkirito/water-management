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
 * 村民/水表信息表 — households
 * <p>
 * 每一户对应一条记录，记录户主姓名、联系方式、所在村名、水表编号等基础信息。
 * 无历史数据时可物理删除；存在抄表或账务历史时停用归档。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "households")
public class Household {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 户主姓名 */
    @Column(name = "household_name", nullable = false)
    private String householdName;

    /** 联系电话 */
    private String phone;

    /** 所属村名 */
    @Column(name = "village_name", nullable = false)
    private String villageName;

    /** 水表编号（唯一） */
    @Column(name = "water_meter_id", unique = true, nullable = false)
    private String waterMeterId;

    /** 材料费总额，默认 1500 元 */
    @Column(name = "material_fee_total", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal materialFeeTotal = new BigDecimal("1500.00");

    /** 是否活跃（1=活跃, 0=已删除/停用） */
    @Column(name = "is_active", columnDefinition = "BOOLEAN DEFAULT 1")
    @Builder.Default
    private Boolean isActive = true;

    /** 创建时间 */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
