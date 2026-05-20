package dev.kavrin.banking_ledger.outbox.application.service;

import dev.kavrin.banking_ledger.outbox.config.OutboxPublisherProperties;
import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventEntity;
import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherWorker {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1_000;

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final OutboxPublisherProperties properties;

    @Scheduled(fixedDelayString = "${banking-ledger.outbox.publisher.interval:5s}")
    @Transactional
    public void publishBatch() {
        if (!properties.enabled()) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();

        List<OutboxEventEntity> events =
                outboxEventRepository.findPublishableEventsForUpdate(
                        now,
                        PageRequest.of(0, properties.batchSize())
                );

        for (OutboxEventEntity event : events) {
            publishOne(event, now);
        }
    }

    private void publishOne(OutboxEventEntity event, OffsetDateTime now) {
        try {
            outboxEventPublisher.publish(event);
            event.markPublished(now);
        } catch (Exception ex) {
            String safeErrorMessage = safeErrorMessage(ex);
            int nextRetryCount = event.getRetryCount() + 1;

            log.warn(
                    "Failed to publish outbox event id={}, type={}, correlationId={}, retryCount={}, maxAttempts={}",
                    event.getId(),
                    event.getEventType(),
                    event.getCorrelationId(),
                    nextRetryCount,
                    properties.maxAttempts(),
                    ex
            );

            if (nextRetryCount >= properties.maxAttempts()) {
                event.markDeadLettered(safeErrorMessage);
                return;
            }

            event.markFailed(
                    safeErrorMessage,
                    computeNextRetryAt(now, nextRetryCount)
            );
        }
    }

    private OffsetDateTime computeNextRetryAt(OffsetDateTime now, int retryCount) {
        Duration initialDelay = properties.initialRetryDelay();
        Duration maxDelay = properties.maxRetryDelay();

        long multiplier = 1L << Math.max(0, retryCount - 1);
        Duration delay = initialDelay.multipliedBy(multiplier);

        if (delay.compareTo(maxDelay) > 0) {
            delay = maxDelay;
        }

        return now.plus(delay);
    }

    private String safeErrorMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = ex.getClass().getSimpleName();
        }

        if (message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }

        return message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
