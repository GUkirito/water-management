package com.example.watermanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "month_locks", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"bill_year", "bill_month"})
})
public class MonthLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_year", nullable = false)
    private Integer billYear;

    @Column(name = "bill_month", nullable = false)
    private Integer billMonth;

    @Column(name = "operator")
    private String operator;

    @Column(length = 500)
    private String note;

    @Column(name = "is_locked")
    @Builder.Default
    private Boolean locked = true;

    @Column(name = "unlocked_at")
    private LocalDateTime unlockedAt;

    @Column(name = "unlocked_by")
    private String unlockedBy;

    @Column(name = "unlock_reason", length = 500)
    private String unlockReason;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;
}
