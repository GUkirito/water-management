package com.example.watermanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "accounting_repair_audits")
public class AccountingRepairAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issue_type", nullable = false)
    private String issueType;

    @Column(name = "target_type", nullable = false)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "before_json", nullable = false, columnDefinition = "TEXT")
    private String beforeJson;

    @Column(name = "after_json", nullable = false, columnDefinition = "TEXT")
    private String afterJson;

    @Column(nullable = false)
    private String operator;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "snapshot_path", nullable = false, length = 1000)
    private String snapshotPath;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
