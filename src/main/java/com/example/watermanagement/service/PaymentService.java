package com.example.watermanagement.service;

import com.example.watermanagement.dto.PaymentRequest;
import com.example.watermanagement.entity.Payment;
import com.example.watermanagement.entity.PrepaymentLog;
import com.example.watermanagement.entity.WaterBill;

import java.math.BigDecimal;
import java.util.List;

/**
 * 收费管理 Service
 */
public interface PaymentService {

    /**
     * 获取指定水表下所有未缴清的水费账单（water_status != '已收'）
     */
    List<WaterBill> getPendingWaterBills(String waterMeterId);

    /**
     * 合并缴费：支持一次缴纳多个月水费
     */
    List<Payment> pay(PaymentRequest request);

    /**
     * 按水表编号查询缴费历史
     */
    List<Payment> getHistory(String waterMeterId);
    /**
     * 获取指定水表下所有水费账单（含已缴清，供历史查询用）
     */
    List<WaterBill> getAllWaterBills(String waterMeterId);

    /**
     * 查询水费预存余额。
     */
    BigDecimal getWaterPrepaymentBalance(String waterMeterId);

    /**
     * 查询水费预存流水。
     */
    List<PrepaymentLog> getWaterPrepaymentLogs(String waterMeterId);
}
