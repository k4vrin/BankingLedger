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

import java.time.OffsetDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherWorker {

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
            log.warn(
                    "Failed to publish outbox event id={}, type={}",
                    event.getId(),
                    event.getEventType(),
                    ex
            );

            event.markFailed(
                    ex.getMessage(),
                    now.plusSeconds(30)
            );
        }
    }
}