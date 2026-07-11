package com.example.watermanagement.repository;

import com.example.watermanagement.entity.AccountingAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountingAdjustmentRepository extends JpaRepository<AccountingAdjustment, Long> {

    List<AccountingAdjustment> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, Long targetId);

    boolean existsByTargetTypeAndTargetId(String targetType, Long targetId);

    List<AccountingAdjustment> findAllByOrderByCreatedAtDesc();
}
