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

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "material_records")
public class MaterialRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "household_name", nullable = false)
    private String householdName;

    @Column(name = "water_meter_id", unique = true)
    private String waterMeterId;

    @Column(name = "phone")
    private String phone;

    @Column(name = "village_name", nullable = false)
    private String villageName;

    @Column(name = "total_fee", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalFee = new BigDecimal("1500.00");

    @Column(name = "actual_paid", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal actualPaid = BigDecimal.ZERO;

    @Column(name = "status")
    @Builder.Default
    private String status = "未收";

    @Column(name = "paid_at", columnDefinition = "DATE")
    private LocalDate paidAt;

    @Column(name = "collector")
    private String collector;

    @Column(name = "note")
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
