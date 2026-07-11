package com.example.watermanagement.repository;

import com.example.watermanagement.entity.AccountingRepairAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountingRepairAuditRepository extends JpaRepository<AccountingRepairAudit, Long> {
}
