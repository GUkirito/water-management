package com.example.watermanagement.repository;

import com.example.watermanagement.entity.MaterialPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaterialPaymentRepository extends JpaRepository<MaterialPayment, Long> {

    List<MaterialPayment> findByRecordIdOrderByPaidDateDesc(Long recordId);

    void deleteByRecordId(Long recordId);
}
