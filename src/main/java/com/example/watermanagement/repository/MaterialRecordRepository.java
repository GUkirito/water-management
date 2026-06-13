package com.example.watermanagement.repository;

import com.example.watermanagement.entity.MaterialRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MaterialRecordRepository extends JpaRepository<MaterialRecord, Long> {

    Optional<MaterialRecord> findByWaterMeterId(String waterMeterId);

    boolean existsByWaterMeterId(String waterMeterId);

    Page<MaterialRecord> findByVillageName(String villageName, Pageable pageable);

    @Query("SELECT r FROM MaterialRecord r WHERE "
            + "(:villageName IS NULL OR r.villageName = :villageName) "
            + "AND (:status IS NULL OR r.status = :status) "
            + "AND (:keyword IS NULL OR r.householdName LIKE %:keyword% OR r.waterMeterId LIKE %:keyword%) "
            + "AND (:paidFrom IS NULL OR r.paidAt >= :paidFrom) "
            + "AND (:paidTo IS NULL OR r.paidAt <= :paidTo)")
    Page<MaterialRecord> search(
            @Param("villageName") String villageName,
            @Param("status") String status,
            @Param("keyword") String keyword,
            @Param("paidFrom") LocalDate paidFrom,
            @Param("paidTo") LocalDate paidTo,
            Pageable pageable);

    @Query("SELECT r FROM MaterialRecord r WHERE "
            + "(:villageName IS NULL OR r.villageName = :villageName) "
            + "AND (:status IS NULL OR r.status = :status) "
            + "AND (:keyword IS NULL OR r.householdName LIKE %:keyword% OR r.waterMeterId LIKE %:keyword%) "
            + "AND (:paidFrom IS NULL OR r.paidAt >= :paidFrom) "
            + "AND (:paidTo IS NULL OR r.paidAt <= :paidTo)")
    List<MaterialRecord> searchAll(
            @Param("villageName") String villageName,
            @Param("status") String status,
            @Param("keyword") String keyword,
            @Param("paidFrom") LocalDate paidFrom,
            @Param("paidTo") LocalDate paidTo);
}
