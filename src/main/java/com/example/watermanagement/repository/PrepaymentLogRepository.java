package com.example.watermanagement.repository;

import com.example.watermanagement.entity.PrepaymentLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface PrepaymentLogRepository extends JpaRepository<PrepaymentLog, Long> {

    List<PrepaymentLog> findByWaterMeterIdOrderByCreatedAtDesc(String waterMeterId);

    boolean existsByBillId(Long billId);

    List<PrepaymentLog> findByBillId(Long billId);

    @Query("select coalesce(sum(l.amount), 0) from PrepaymentLog l where l.waterMeterId = :waterMeterId")
    BigDecimal getBalance(@Param("waterMeterId") String waterMeterId);
}
