package dev.kavrin.banking_ledger.outbox.application.service;

import dev.kavrin.banking_ledger.outbox.domain.model.OutboxStatus;
import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;

@Component
public class OutboxMetrics {

    private final OutboxEventRepository outboxEventRepository;
    private final Counter publishSuccessCounter;
    private final Counter publishFailureCounter;
    private final Counter deadLetterCounter;

    public OutboxMetrics(OutboxEventRepository outboxEventRepository, MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;
        this.publishSuccessCounter = Counter.builder("banking_ledger_outbox_publish_success_total")
                .description("Total successfully published outbox events.")
                .register(meterRegistry);
        this.publishFailureCounter = Counter.builder("banking_ledger_outbox_publish_failure_total")
                .description("Total failed outbox publish attempts.")
                .register(meterRegistry);
        this.deadLetterCounter = Counter.builder("banking_ledger_outbox_dead_letter_total")
                .description("Total outbox events moved to dead letter.")
                .register(meterRegistry);

        Gauge.builder("banking_ledger_outbox_pending_count", this, OutboxMetrics::pendingCount)
                .description("Current number of pending outbox events.")
                .register(meterRegistry);
        Gauge.builder("banking_ledger_outbox_dead_letter_count", this, OutboxMetrics::deadLetterCount)
                .description("Current number of dead-lettered outbox events.")
                .register(meterRegistry);
        Gauge.builder("banking_ledger_outbox_oldest_pending_age_seconds", this, OutboxMetrics::oldestPendingAgeSeconds)
                .description("Age in seconds of the oldest pending outbox event.")
                .register(meterRegistry);
    }

    public void recordPublishSuccess() {
        publishSuccessCounter.increment();
    }

    public void recordPublishFailure() {
        publishFailureCounter.increment();
    }

    public void recordDeadLetter() {
        deadLetterCounter.increment();
    }

    private double pendingCount() {
        return outboxEventRepository.countByStatus(OutboxStatus.PENDING);
    }

    private double deadLetterCount() {
        return outboxEventRepository.countByStatus(OutboxStatus.DEAD_LETTER);
    }

    private double oldestPendingAgeSeconds() {
        return outboxEventRepository.findOldestPendingCreatedAt()
                .map(createdAt -> Math.max(0, Duration.between(createdAt, OffsetDateTime.now()).toSeconds()))
                .orElse(0L);
    }
}
