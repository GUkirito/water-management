package com.example.watermanagement.repository;

import com.example.watermanagement.entity.Reading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 抄表记录 Repository
 */
@Repository
public interface ReadingRepository extends JpaRepository<Reading, Long> {

    /**
     * 按水表编号 + 抄表日期精确查找（唯一约束）
     */
    Optional<Reading> findByWaterMeterIdAndReadingDate(String waterMeterId, LocalDate readingDate);

    boolean existsByWaterMeterIdAndReadingDateAfter(String waterMeterId, LocalDate readingDate);

    /**
     * 查询某水表在指定日期范围内的所有抄表记录
     */
    List<Reading> findByWaterMeterIdAndReadingDateBetween(
            String waterMeterId, LocalDate startDate, LocalDate endDate);

    /**
     * 查询某水表某月的抄表记录（月初到月末）
     */
    List<Reading> findByWaterMeterIdAndReadingDateGreaterThanEqualAndReadingDateLessThanEqual(
            String waterMeterId, LocalDate monthStart, LocalDate monthEnd);

    /**
     * 查询指定水表编号列表中最新的抄表记录（用于获取上次表底）
     */
    List<Reading> findByWaterMeterIdInOrderByReadingDateDesc(List<String> waterMeterIds);

    /**
     * 查询指定日期范围内的所有抄表记录（按村汇总时使用）
     */
    List<Reading> findByReadingDateBetween(LocalDate startDate, LocalDate endDate);
}
