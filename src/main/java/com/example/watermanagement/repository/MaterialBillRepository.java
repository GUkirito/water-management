package com.example.watermanagement.repository;

import com.example.watermanagement.entity.MaterialBill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 材料费账单 Repository
 */
@Repository
public interface MaterialBillRepository extends JpaRepository<MaterialBill, Long> {

    /**
     * 按水表编号查找材料费账单
     */
    Optional<MaterialBill> findByWaterMeterId(String waterMeterId);

    /**
     * 查找所有未缴清的材料费账单
     */
    Optional<MaterialBill> findByWaterMeterIdAndStatusNot(String waterMeterId, String status);
}
