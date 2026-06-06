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
 * 缴费明细表 — payments
 * <p>
 * 每笔缴费生成一条记录。bill_type 区分 water（水费）和 material（材料费），
 * bill_id 关联对应账单表的主键。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 账单类型：water（水费）/ material（材料费） */
    @Column(name = "bill_type", nullable = false)
    private String billType;

    /** 关联的账单 ID（water_bills.id 或 material_bills.id） */
    @Column(name = "bill_id", nullable = false)
    private Long billId;

    /** 本次缴费金额 */
    @Column(precision = 10, scale = 2)
    private BigDecimal amount;

    /** 缴费日期 */
    @Column(name = "paid_date", columnDefinition = "DATE")
    private LocalDate paidDate;

    /** 缴费方式，默认"现金" */
    @Column(name = "payment_method")
    @Builder.Default
    private String paymentMethod = "现金";

    /** 操作员/收款人姓名 */
    private String operator;

    /** 备注 */
    private String note;

    /** 创建时间 */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
