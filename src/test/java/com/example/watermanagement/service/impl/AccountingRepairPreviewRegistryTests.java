package com.example.watermanagement.service.impl;

import com.example.watermanagement.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountingRepairPreviewRegistryTests {

    @Test
    void expiredPreviewConfirmationIsRejectedAndConsumed() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-12T00:00:00Z"));
        AccountingRepairPreviewRegistry registry =
                new AccountingRepairPreviewRegistry(clock, Duration.ofMinutes(10));
        String token = registry.register("PAYMENT_TOTAL_MISMATCH", "water_bill", 1L, "plan-a");
        clock.advance(Duration.ofMinutes(11));

        assertThatThrownBy(() -> registry.consume(
                token, "PAYMENT_TOTAL_MISMATCH", "water_bill", 1L, "plan-a"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重新查看处理方式");
        assertThatThrownBy(() -> registry.consume(
                token, "PAYMENT_TOTAL_MISMATCH", "water_bill", 1L, "plan-a"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重新查看处理方式");
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
