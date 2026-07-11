package com.example.watermanagement.service.impl;

import com.example.watermanagement.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AccountingRepairPreviewRegistry {

    static final Duration DEFAULT_VALIDITY = Duration.ofMinutes(10);
    private static final String REFRESH_MESSAGE = "账务数据或确认内容已变化，请重新查看处理方式后再操作";

    private final ConcurrentHashMap<String, PreviewConfirmation> confirmations = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration validity;

    public AccountingRepairPreviewRegistry() {
        this(Clock.systemUTC(), DEFAULT_VALIDITY);
    }

    AccountingRepairPreviewRegistry(Clock clock, Duration validity) {
        this.clock = Objects.requireNonNull(clock);
        this.validity = Objects.requireNonNull(validity);
    }

    String register(String issueType, String refType, Long refId, String planSignature) {
        Instant now = clock.instant();
        confirmations.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        String token = UUID.randomUUID().toString();
        confirmations.put(token, new PreviewConfirmation(
                issueType, refType, refId, planSignature, now.plus(validity)));
        return token;
    }

    void consume(String token, String issueType, String refType, Long refId, String currentPlanSignature) {
        PreviewConfirmation confirmation = token == null ? null : confirmations.remove(token);
        if (confirmation == null
                || !confirmation.expiresAt().isAfter(clock.instant())
                || !Objects.equals(confirmation.issueType(), issueType)
                || !Objects.equals(confirmation.refType(), refType)
                || !Objects.equals(confirmation.refId(), refId)
                || !Objects.equals(confirmation.planSignature(), currentPlanSignature)) {
            throw new BusinessException(REFRESH_MESSAGE);
        }
    }

    private record PreviewConfirmation(
            String issueType,
            String refType,
            Long refId,
            String planSignature,
            Instant expiresAt) {
    }
}
