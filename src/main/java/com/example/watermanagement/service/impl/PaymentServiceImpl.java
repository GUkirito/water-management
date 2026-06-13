package com.example.watermanagement.service.impl;

import com.example.watermanagement.dto.PaymentRequest;
import com.example.watermanagement.entity.Payment;
import com.example.watermanagement.entity.WaterBill;
import com.example.watermanagement.exception.BusinessException;
import com.example.watermanagement.repository.PaymentRepository;
import com.example.watermanagement.repository.WaterBillRepository;
import com.example.watermanagement.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 收费管理 Service 实现
 * <p>
 * 核心逻辑：
 * - 水费合并缴费：按比例分配实收金额到多个月份账单
 * - 材料费缴费：单一账单直接缴
 * - 缴费后自动重算 bill 的 water_status / status
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final WaterBillRepository waterBillRepository;
    private final PaymentRepository paymentRepository;

    @Override
    public List<WaterBill> getPendingWaterBills(String waterMeterId) {
        // 返回所有未缴清的水费账单
        return waterBillRepository.findByWaterMeterIdAndWaterStatusNot(waterMeterId, "已收");
    }


    @Override
    @Transactional
    public List<Payment> pay(PaymentRequest request) {
        if ("water".equals(request.getBillType())) {
            return payWaterBills(request);
        }
        // 材料费已迁移到独立系统 MaterialRecordService.collect()
        throw new BusinessException("材料费请使用材料费管理页面操作");
    }

    @Override
    public List<Payment> getHistory(String waterMeterId) {
        // 缴费历史需要通过 water_bills 和 material_bills 关联查询
        // 简化实现：先查水费账单ID和材料费账单ID，再查对应的 payments
        List<Payment> allPayments = new ArrayList<>();

        // 水费相关的缴费记录
        List<WaterBill> waterBills = waterBillRepository.findByWaterMeterId(waterMeterId);
        List<Long> waterBillIds = waterBills.stream().map(WaterBill::getId).toList();
        if (!waterBillIds.isEmpty()) {
            allPayments.addAll(paymentRepository.findByBillTypeAndBillIdIn("water", waterBillIds));
        }

        // 按缴费日期降序排列
        allPayments.sort((a, b) -> b.getPaidDate().compareTo(a.getPaidDate()));
        return allPayments;
    }

    // ==================== 私有方法 ====================

    /**
     * 水费合并缴费：按欠费比例分配实收金额到多个月份账单
     */
    private List<Payment> payWaterBills(PaymentRequest request) {
        List<WaterBill> bills = waterBillRepository.findAllById(request.getBillIds());
        if (bills.isEmpty()) {
            throw new BusinessException("未找到指定水费账单");
        }
        if (bills.size() != request.getBillIds().size()) {
            throw new BusinessException("部分账单ID无效，请检查");
        }

        // 计算各账单的未缴金额和总欠费
        for (WaterBill bill : bills) {
            if ("已收".equals(bill.getWaterStatus())) {
                throw new BusinessException("账单 " + bill.getId()
                        + "（" + bill.getBillYear() + "年" + bill.getBillMonth() + "月）已缴清，请勿重复缴费");
            }
        }

        BigDecimal totalDue = bills.stream()
                .map(b -> b.getWaterCharge().subtract(b.getActualWaterPaid()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("水费合并缴费: {} 个月份, 总欠费={}元, 实收={}元",
                bills.size(), totalDue, request.getAmount());

        List<Payment> payments = new ArrayList<>();
        BigDecimal remaining = request.getAmount();

        for (int i = 0; i < bills.size(); i++) {
            WaterBill bill = bills.get(i);
            BigDecimal due = bill.getWaterCharge().subtract(bill.getActualWaterPaid());

            // 按比例分配：本次缴费金额 = 实收总额 × (该账单欠费 / 总欠费)
            BigDecimal thisPayment;
            if (i == bills.size() - 1) {
                // 最后一笔拿走全部剩余金额（避免小数点误差）
                thisPayment = remaining.min(due);
            } else {
                thisPayment = request.getAmount()
                        .multiply(due)
                        .divide(totalDue, 2, RoundingMode.HALF_UP)
                        .min(due);
            }

            if (thisPayment.compareTo(BigDecimal.ZERO) <= 0) continue;

            // 创建缴费记录
            Payment payment = Payment.builder()
                    .billType("water")
                    .billId(bill.getId())
                    .amount(thisPayment)
                    .paidDate(request.getPaidDate())
                    .paymentMethod(request.getPaymentMethod())
                    .operator(request.getOperator())
                    .note(request.getNote())
                    .build();
            payments.add(paymentRepository.save(payment));

            // 更新水费账单
            BigDecimal newPaid = bill.getActualWaterPaid().add(thisPayment);
            bill.setActualWaterPaid(newPaid);
            bill.setWaterStatus(calcStatus(newPaid, bill.getWaterCharge()));
            waterBillRepository.save(bill);

            remaining = remaining.subtract(thisPayment);
        }

        log.info("缴费完成: 共{}笔, 合计{}元", payments.size(),
                payments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        return payments;
    }

    /**
     * 根据已缴金额和应收金额计算状态
     */
    private String calcStatus(BigDecimal paid, BigDecimal total) {
        if (paid.compareTo(BigDecimal.ZERO) == 0) {
            return "未收";
        } else if (paid.compareTo(total) >= 0) {
            return "已收";
        } else {
            return "部分收";
        }
    }
}
