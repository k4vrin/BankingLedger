package dev.kavrin.banking_ledger.reversal.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kavrin.banking_ledger.account.domain.model.AccountCategory;
import dev.kavrin.banking_ledger.account.domain.model.AccountStatus;
import dev.kavrin.banking_ledger.account.domain.model.AccountType;
import dev.kavrin.banking_ledger.account.persistence.AccountEntity;
import dev.kavrin.banking_ledger.account.persistence.AccountRepository;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorRole;
import dev.kavrin.banking_ledger.audit.persistence.AuditEventRepository;
import dev.kavrin.banking_ledger.customer.domain.model.CustomerStatus;
import dev.kavrin.banking_ledger.customer.persistence.CustomerEntity;
import dev.kavrin.banking_ledger.customer.persistence.CustomerRepository;
import dev.kavrin.banking_ledger.ledger.domain.model.LedgerTransactionType;
import dev.kavrin.banking_ledger.ledger.persistence.repository.LedgerTransactionRepository;
import dev.kavrin.banking_ledger.ledger.persistence.repository.PostingRepository;
import dev.kavrin.banking_ledger.outbox.OutboxStatus;
import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventRepository;
import dev.kavrin.banking_ledger.reversal.application.command.ReverseTransferCommand;
import dev.kavrin.banking_ledger.reversal.domain.model.ReversalReasonCode;
import dev.kavrin.banking_ledger.reversal.domain.model.ReversalStatus;
import dev.kavrin.banking_ledger.reversal.persistence.ReversalRepository;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.ConflictException;
import dev.kavrin.banking_ledger.shared.error.SecurityDomainException;
import dev.kavrin.banking_ledger.shared.money.CurrencyCode;
import dev.kavrin.banking_ledger.transfer.application.command.CreateTransferCommand;
import dev.kavrin.banking_ledger.transfer.application.service.CreateTransferUseCase;
import dev.kavrin.banking_ledger.transfer.domain.model.RequestedByActorType;
import dev.kavrin.banking_ledger.transfer.domain.model.TransferStatus;
import dev.kavrin.banking_ledger.transfer.persistence.TransferRequestRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ReverseTransferUseCaseIntegrationTests {

    private final Set<UUID> transferIds = new HashSet<>();
    private final Set<UUID> reversalIds = new HashSet<>();
    private final Set<UUID> ledgerTransactionIds = new HashSet<>();
    private final Set<UUID> accountIds = new HashSet<>();
    private final Set<UUID> customerIds = new HashSet<>();
    private final Set<String> idempotencyKeys = new HashSet<>();
    @Autowired
    private CreateTransferUseCase createTransferUseCase;
    @Autowired
    private ReverseTransferUseCase reverseTransferUseCase;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private TransferRequestRepository transferRequestRepository;
    @Autowired
    private ReversalRepository reversalRepository;
    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;
    @Autowired
    private PostingRepository postingRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static String shortSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String raw(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    @AfterEach
    void cleanUp() {
        for (UUID reversalId : reversalIds) {
            jdbcTemplate.update("delete from reversals where id = hextoraw(?)", raw(reversalId));
        }
        for (UUID transferId : transferIds) {
            jdbcTemplate.update("delete from outbox_events where aggregate_id = hextoraw(?)", raw(transferId));
            jdbcTemplate.update("delete from audit_events where entity_id = hextoraw(?)", raw(transferId));
            jdbcTemplate.update("delete from transfer_requests where id = hextoraw(?)", raw(transferId));
        }
        for (String idempotencyKey : idempotencyKeys) {
            jdbcTemplate.update("delete from idempotency_records where idempotency_key = ?", idempotencyKey);
        }
        for (UUID ledgerTransactionId : ledgerTransactionIds) {
            jdbcTemplate.update("delete from outbox_events where aggregate_id = hextoraw(?)", raw(ledgerTransactionId));
            jdbcTemplate.update("delete from audit_events where entity_id = hextoraw(?)", raw(ledgerTransactionId));
            jdbcTemplate.update(
                    """
                    delete from postings
                    where journal_entry_id in (
                        select id from journal_entries where ledger_transaction_id = hextoraw(?)
                    )
                    """,
                    raw(ledgerTransactionId)
            );
            jdbcTemplate.update("delete from journal_entries where ledger_transaction_id = hextoraw(?)", raw(ledgerTransactionId));
            jdbcTemplate.update("delete from ledger_transactions where id = hextoraw(?)", raw(ledgerTransactionId));
        }
        for (UUID accountId : accountIds) {
            jdbcTemplate.update("delete from accounts where id = hextoraw(?)", raw(accountId));
        }
        for (UUID customerId : customerIds) {
            jdbcTemplate.update("delete from customers where id = hextoraw(?)", raw(customerId));
        }
    }

    @Test
    void successfulReversalCreatesOppositeLedgerTransactionAuditOutboxAndRestoresBalances() throws Exception {
        var source = createAccount("REV-SRC", "USD", 1_000);
        var destination = createAccount("REV-DST", "USD", 250);
        var transferId = createCompletedTransfer(source.getId(), destination.getId(), "transfer-reversal-success", "idem-reversal-success");
        var originalTransfer = transferRequestRepository.findById(transferId).orElseThrow();
        var originalLedgerTransactionId = originalTransfer.getLedgerTransaction().getId();
        ledgerTransactionIds.add(originalLedgerTransactionId);

        var response = reverseTransferUseCase.handle(reverseCommand(transferId, AuditActorRole.OPS_ADMIN, RequestedByActorType.OPS_ADMIN, "corr-reversal-success"));
        reversalIds.add(response.id());
        ledgerTransactionIds.add(response.reversalLedgerTransactionId());

        assertThat(response.status()).isEqualTo(ReversalStatus.COMPLETED);
        assertThat(response.originalTransferId()).isEqualTo(transferId);
        assertThat(response.originalLedgerTransactionId()).isEqualTo(originalLedgerTransactionId);
        assertThat(response.reversalLedgerTransactionId()).isNotEqualTo(originalLedgerTransactionId);

        var transfer = transferRequestRepository.findById(transferId).orElseThrow();
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.REVERSED);
        assertThat(transfer.getLedgerTransaction().getId()).isEqualTo(originalLedgerTransactionId);

        assertThat(ledgerTransactionRepository.findById(response.reversalLedgerTransactionId()).orElseThrow().getTransactionType())
                .isEqualTo(LedgerTransactionType.REVERSAL);
        assertThat(postingRepository.countByJournalEntry_LedgerTransaction_Id(originalLedgerTransactionId)).isEqualTo(2);
        assertThat(postingRepository.countByJournalEntry_LedgerTransaction_Id(response.reversalLedgerTransactionId())).isEqualTo(2);

        assertThat(accountRepository.findById(source.getId()).orElseThrow().getAvailableBalanceMinor()).isEqualTo(1_000);
        assertThat(accountRepository.findById(destination.getId()).orElseThrow().getAvailableBalanceMinor()).isEqualTo(250);

        assertThat(auditEventRepository.findAll())
                .filteredOn(event -> transferId.equals(event.getEntityId()))
                .anySatisfy(event -> {
                    assertThat(event.getEventType()).isEqualTo("TRANSFER_REVERSED");
                    assertThat(event.getEntityType()).isEqualTo("TRANSFER");
                    assertThat(event.getCorrelationId()).isEqualTo("corr-reversal-success");
                    assertThat(event.getEventPayload()).contains(response.reversalLedgerTransactionId().toString());
                    assertThat(event.getEventPayload()).contains("DUPLICATE_TRANSFER");
                });

        assertThat(outboxEventRepository.findAll())
                .filteredOn(event -> transferId.equals(event.getAggregateId()))
                .anySatisfy(event -> {
                    assertThat(event.getAggregateType()).isEqualTo("TRANSFER");
                    assertThat(event.getEventType()).isEqualTo("LedgerTransactionReversed");
                    assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
                    assertThat(event.getEventPayload()).contains(response.reversalLedgerTransactionId().toString());
                });
    }

    @Test
    void duplicateReversalIsRejectedWithoutCreatingAnotherLedgerTransaction() throws Exception {
        var source = createAccount("REV-SRC", "USD", 1_000);
        var destination = createAccount("REV-DST", "USD", 250);
        var transferId = createCompletedTransfer(source.getId(), destination.getId(), "transfer-reversal-dup", "idem-reversal-dup");
        ledgerTransactionIds.add(transferRequestRepository.findById(transferId).orElseThrow().getLedgerTransaction().getId());

        var first = reverseTransferUseCase.handle(reverseCommand(transferId, AuditActorRole.OPS_ADMIN, RequestedByActorType.OPS_ADMIN, "corr-reversal-dup-1"));
        reversalIds.add(first.id());
        ledgerTransactionIds.add(first.reversalLedgerTransactionId());

        assertThatThrownBy(() -> reverseTransferUseCase.handle(reverseCommand(
                transferId,
                AuditActorRole.OPS_ADMIN,
                RequestedByActorType.OPS_ADMIN,
                "corr-reversal-dup-2"
        )))
                .isInstanceOfSatisfying(ConflictException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Business.REVERSAL_ALREADY_EXISTS));

        assertThat(reversalRepository.findAll())
                .filteredOn(reversal -> transferId.equals(reversal.getOriginalTransfer().getId()))
                .hasSize(1);
        assertThat(outboxEventRepository.findAll())
                .noneSatisfy(event -> assertThat(event.getCorrelationId()).isEqualTo("corr-reversal-dup-2"));
    }

    @Test
    void tellerRoleIsRejectedAndTransferRemainsCompleted() throws Exception {
        var source = createAccount("REV-SRC", "USD", 1_000);
        var destination = createAccount("REV-DST", "USD", 250);
        var transferId = createCompletedTransfer(source.getId(), destination.getId(), "transfer-reversal-forbidden", "idem-reversal-forbidden");
        ledgerTransactionIds.add(transferRequestRepository.findById(transferId).orElseThrow().getLedgerTransaction().getId());

        assertThatThrownBy(() -> reverseTransferUseCase.handle(reverseCommand(
                transferId,
                AuditActorRole.TELLER,
                RequestedByActorType.TELLER,
                "corr-reversal-forbidden"
        )))
                .isInstanceOfSatisfying(SecurityDomainException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ApiErrorCode.Security.FORBIDDEN_RESOURCE));

        assertThat(transferRequestRepository.findById(transferId).orElseThrow().getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(reversalRepository.existsByOriginalTransfer_Id(transferId)).isFalse();
        assertThat(outboxEventRepository.findAll())
                .noneSatisfy(event -> assertThat(event.getCorrelationId()).isEqualTo("corr-reversal-forbidden"));
    }

    private UUID createCompletedTransfer(
            UUID sourceAccountId,
            UUID destinationAccountId,
            String externalReference,
            String idempotencyKey
    ) throws Exception {
        idempotencyKeys.add(idempotencyKey);
        var result = createTransferUseCase.handle(new CreateTransferCommand(
                sourceAccountId,
                destinationAccountId,
                CurrencyCode.of("USD"),
                100,
                externalReference + "-" + shortSuffix(),
                "test transfer",
                idempotencyKey,
                RequestedByActorType.OPS_ADMIN,
                "corr-" + externalReference
        ));

        var response = objectMapper.readTree(result.responseBody());
        var transferId = UUID.fromString(response.get("id").asText());
        transferIds.add(transferId);
        return transferId;
    }

    private ReverseTransferCommand reverseCommand(
            UUID transferId,
            AuditActorRole actorRole,
            RequestedByActorType actorType,
            String correlationId
    ) {
        return new ReverseTransferCommand(
                transferId,
                ReversalReasonCode.DUPLICATE_TRANSFER,
                "duplicate transfer",
                actorType,
                actorRole,
                "ops-1",
                correlationId
        );
    }

    private AccountEntity createAccount(String prefix, String currencyCode, long balanceMinor) {
        var customer = customerRepository.save(CustomerEntity.builder()
                .externalCustomerReference("cust-" + shortSuffix())
                .fullName("Reversal Test Customer")
                .email("reversal-" + shortSuffix() + "@example.com")
                .status(CustomerStatus.ACTIVE)
                .build());
        customerIds.add(customer.getId());

        var account = accountRepository.save(AccountEntity.builder()
                .customer(customer)
                .accountNumber(prefix + "-" + shortSuffix())
                .accountType(AccountType.CURRENT)
                .accountCategory(AccountCategory.CUSTOMER)
                .status(AccountStatus.ACTIVE)
                .currencyCode(currencyCode)
                .availableBalanceMinor(balanceMinor)
                .ledgerBalanceMinor(balanceMinor)
                .build());
        accountIds.add(account.getId());
        return account;
    }
}
