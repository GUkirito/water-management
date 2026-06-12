package com.example.watermanagement.service.impl;

import com.example.watermanagement.dto.MaterialCollectRequest;
import com.example.watermanagement.dto.MaterialFeeRow;
import com.example.watermanagement.entity.Household;
import com.example.watermanagement.entity.MaterialBill;
import com.example.watermanagement.entity.Payment;
import com.example.watermanagement.exception.BusinessException;
import com.example.watermanagement.repository.HouseholdRepository;
import com.example.watermanagement.repository.MaterialBillRepository;
import com.example.watermanagement.repository.PaymentRepository;
import com.example.watermanagement.service.MaterialFeeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 材料费管理 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialFeeServiceImpl implements MaterialFeeService {

    private final HouseholdRepository householdRepository;
    private final MaterialBillRepository materialBillRepository;
    private final PaymentRepository paymentRepository;

    @Override
    public Page<MaterialFeeRow> listByVillage(String villageName, Pageable pageable) {
        // 查询该村所有活跃住户（分页）
        Page<Household> householdPage;
        if (villageName != null && !villageName.isEmpty()) {
            householdPage = householdRepository
                    .findByVillageNameInAndIsActiveTrue(List.of(villageName), pageable);
        } else {
            householdPage = householdRepository.findByIsActiveTrue(pageable);
        }

        List<MaterialFeeRow> rows = new ArrayList<>();
        for (Household h : householdPage.getContent()) {
            MaterialBill bill = materialBillRepository.findByWaterMeterId(h.getWaterMeterId())
                    .orElse(null);

            BigDecimal totalFee = bill != null ? bill.getTotalFee() : h.getMaterialFeeTotal();
            BigDecimal actualPaid = bill != null ? bill.getActualPaid() : BigDecimal.ZERO;
            BigDecimal unpaid = totalFee.subtract(actualPaid);
            String status = bill != null ? bill.getStatus() : "未收";

            rows.add(MaterialFeeRow.builder()
                    .waterMeterId(h.getWaterMeterId())
                    .householdName(h.getHouseholdName())
                    .villageName(h.getVillageName())
                    .phone(h.getPhone())
                    .totalFee(totalFee)
                    .actualPaid(actualPaid)
                    .unpaid(unpaid.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : unpaid)
                    .status(status)
                    .paidAt(bill != null ? bill.getPaidAt() : null)
                    .collector(bill != null ? bill.getCollector() : null)
                    .build());
        }

        return new PageImpl<>(rows, pageable, householdPage.getTotalElements());
    }

    @Override
    @Transactional
    public Payment collect(MaterialCollectRequest request) {
        // 查找材料费账单
        MaterialBill bill = materialBillRepository.findByWaterMeterId(request.getWaterMeterId())
                .orElseThrow(() -> new BusinessException(
                        "未找到材料费账单: 水表编号=" + request.getWaterMeterId()));

        if ("已收".equals(bill.getStatus())) {
            throw new BusinessException("该户材料费已缴清");
        }

        // 校验实收金额不超过欠费
        BigDecimal unpaid = bill.getTotalFee().subtract(bill.getActualPaid());
        if (request.getAmount().compareTo(unpaid) > 0) {
            throw new BusinessException("实收金额（" + request.getAmount()
                    + "）超过欠费金额（" + unpaid + "）");
        }

        // 创建缴费记录
        Payment payment = Payment.builder()
                .billType("material")
                .billId(bill.getId())
                .amount(request.getAmount())
                .paidDate(request.getPaidDate())
                .paymentMethod("现金")
                .operator(request.getCollector())
                .note(request.getNote())
                .build();
        payment = paymentRepository.save(payment);

        // 更新材料费账单
        BigDecimal newPaid = bill.getActualPaid().add(request.getAmount());
        bill.setActualPaid(newPaid);
        bill.setStatus(calcStatus(newPaid, bill.getTotalFee()));
        bill.setPaidAt(request.getPaidDate());
        bill.setCollector(request.getCollector());
        materialBillRepository.save(bill);

        log.info("材料费收费: 水表={}, 户名请求={}, 金额={}元, 累计已缴={}元, 状态={}",
                request.getWaterMeterId(), request.getAmount(), newPaid, bill.getStatus());

        return payment;
    }

    @Override
    public List<Payment> getHistory(String waterMeterId) {
        MaterialBill bill = materialBillRepository.findByWaterMeterId(waterMeterId)
                .orElseThrow(() -> new BusinessException(
                        "未找到材料费账单: 水表编号=" + waterMeterId));

        return paymentRepository.findByBillTypeAndBillIdIn("material", List.of(bill.getId()));
    }

    /**
     * 根据已缴金额和应收金额计算状态
     */
    private String calcStatus(BigDecimal paid, BigDecimal total) {
        if (paid.compareTo(BigDecimal.ZERO) <= 0) {
            return "未收";
        } else if (paid.compareTo(total) >= 0) {
            return "已收";
        } else {
            return "部分收";
        }
    }
}
