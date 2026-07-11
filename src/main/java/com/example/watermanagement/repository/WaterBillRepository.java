package com.example.watermanagement.repository;

import com.example.watermanagement.entity.WaterBill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

    List<WaterBill> findAllByWaterMeterIdAndBillYearAndBillMonth(
            String waterMeterId, Integer billYear, Integer billMonth);

    /**
     * 查询某水表的所有水费账单
     */
    List<WaterBill> findByWaterMeterId(String waterMeterId);

    /**
     * 查询某水表所有未缴清的水费账单（用于缴费页面展示）
     */
    @Query("""
            SELECT wb
            FROM WaterBill wb
            WHERE wb.waterMeterId = :waterMeterId
              AND wb.waterStatus IN ('未收', '部分收')
              AND wb.waterCharge - COALESCE(wb.actualWaterPaid, 0) > 0
            """)
    List<WaterBill> findPendingByWaterMeterId(String waterMeterId);

    /**
     * 按年月查询所有水费账单
     */
    List<WaterBill> findByBillYearAndBillMonth(Integer billYear, Integer billMonth);

    /**
     * 按状态查询（如：查询所有"未收"账单）
     */
    List<WaterBill> findByWaterStatus(String waterStatus);

    /**
     * 查询所有未缴清水费账单，并关联住户信息。
     */
    @Query("""
            SELECT new com.example.watermanagement.dto.PendingWaterBillRow(
                wb.id,
                wb.waterMeterId,
                h.householdName,
                h.villageName,
                wb.billYear,
                wb.billMonth,
                wb.waterAmount,
                wb.waterCharge,
                wb.actualWaterPaid,
                wb.waterCharge - COALESCE(wb.actualWaterPaid, 0),
                wb.waterStatus,
                wb.note,
                h.isActive
            )
            FROM WaterBill wb
            JOIN Household h ON wb.waterMeterId = h.waterMeterId
            WHERE wb.waterStatus IN ('未收', '部分收')
              AND wb.waterCharge - COALESCE(wb.actualWaterPaid, 0) > 0
              AND (:villageName IS NULL OR h.villageName = :villageName)
              AND (:keyword IS NULL OR h.householdName LIKE CONCAT('%', :keyword, '%')
                   OR wb.waterMeterId LIKE CONCAT('%', :keyword, '%'))
              AND (:billYear IS NULL OR wb.billYear = :billYear)
              AND (:billMonth IS NULL OR wb.billMonth = :billMonth)
            ORDER BY wb.billYear DESC, wb.billMonth DESC, h.villageName ASC, h.householdName ASC
            """)
    List<com.example.watermanagement.dto.PendingWaterBillRow> listPendingWaterBills(
            String villageName,
            String keyword,
            Integer billYear,
            Integer billMonth);
}
