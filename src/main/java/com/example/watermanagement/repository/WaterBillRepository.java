package com.example.watermanagement.repository;

import com.example.watermanagement.entity.WaterBill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 水费账单 Repository
 */
@Repository
public interface WaterBillRepository extends JpaRepository<WaterBill, Long> {

    /**
     * 按水表编号 + 年月查找
     */
    Optional<WaterBill> findByWaterMeterIdAndBillYearAndBillMonth(
            String waterMeterId, Integer billYear, Integer billMonth);

    /**
     * 查询某水表的所有水费账单
     */
    List<WaterBill> findByWaterMeterId(String waterMeterId);

    /**
     * 查询某水表所有未缴清的水费账单（用于缴费页面展示）
     */
    List<WaterBill> findByWaterMeterIdAndWaterStatusNot(String waterMeterId, String waterStatus);

    /**
     * 按年月查询所有水费账单
     */
    List<WaterBill> findByBillYearAndBillMonth(Integer billYear, Integer billMonth);

    /**
     * 按状态查询（如：查询所有"未收"账单）
     */
    List<WaterBill> findByWaterStatus(String waterStatus);
}
