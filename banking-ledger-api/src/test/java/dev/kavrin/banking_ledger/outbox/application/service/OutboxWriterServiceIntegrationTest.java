package dev.kavrin.banking_ledger.outbox.application.service;

import dev.kavrin.banking_ledger.outbox.application.command.WriteOutboxEventCommand;
import dev.kavrin.banking_ledger.outbox.domain.model.OutboxAggregateType;
import dev.kavrin.banking_ledger.outbox.domain.model.OutboxDestination;
import dev.kavrin.banking_ledger.outbox.domain.model.OutboxEventType;
import dev.kavrin.banking_ledger.outbox.domain.model.OutboxStatus;
import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class OutboxWriterServiceIntegrationTest {

    @Autowired
    private OutboxWriterService outboxWriterService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID aggregateId;

    private static String raw(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    @AfterEach
    void cleanUp() {
        if (aggregateId != null) {
            jdbcTemplate.update("delete from outbox_events where aggregate_id = hextoraw(?)", raw(aggregateId));
        }
    }

    @Test
    void writeRequiresCallerTransaction() {
        aggregateId = UUID.randomUUID();

        assertThatThrownBy(() -> outboxWriterService.write(command(aggregateId)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void writePersistsPendingEventInsideCallerTransaction() {
        aggregateId = UUID.randomUUID();

        var eventId = transactionTemplate.execute(status -> outboxWriterService.write(command(aggregateId)).getId());

        var event = outboxEventRepository.findById(eventId).orElseThrow();
        assertThat(event.getAggregateId()).isEqualTo(aggregateId);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getCorrelationId()).isEqualTo("corr-writer");
    }

    private WriteOutboxEventCommand command(UUID aggregateId) {
        return new WriteOutboxEventCommand(
                OutboxAggregateType.LEDGER_TRANSACTION.name(),
                aggregateId,
                OutboxEventType.LEDGER_TRANSACTION_POSTED,
                OutboxDestination.LEDGER_EVENTS,
                "corr-writer",
                new Payload("ok")
        );
    }

    private record Payload(String value) {
    }
}
