package com.example.watermanagement.repository;

import com.example.watermanagement.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 缴费明细 Repository
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * 按账单类型 + 账单 ID 查找缴费记录
     */
    List<Payment> findByBillTypeAndBillId(String billType, Long billId);

    /**
     * 按账单类型 + 多个账单 ID 查找缴费记录（合并缴费时使用）
     */
    List<Payment> findByBillTypeAndBillIdIn(String billType, List<Long> billIds);

    /**
     * 按水表编号关联查询缴费历史（通过 JPQL 连表）
     */
    List<Payment> findByBillTypeAndBillIdInOrderByPaidDateDesc(String billType, List<Long> billIds);
}
