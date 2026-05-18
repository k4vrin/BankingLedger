package dev.kavrin.banking_ledger.transfer.persistence;

import dev.kavrin.banking_ledger.account.persistence.AccountEntity;
import dev.kavrin.banking_ledger.customer.persistence.CustomerEntity;
import dev.kavrin.banking_ledger.ledger.persistence.LedgerTransactionEntity;
import dev.kavrin.banking_ledger.transfer.domain.model.RequestedByActorType;
import dev.kavrin.banking_ledger.transfer.domain.model.TransferStatus;
import dev.kavrin.banking_ledger.transfer.domain.model.TransferType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "transfer_requests",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_transfer_requests_external_ref",
                        columnNames = "external_reference"
                ),
                @UniqueConstraint(
                        name = "uk_transfer_requests_ledger_tx",
                        columnNames = "ledger_transaction_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TransferRequestEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "source_account_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_transfer_source_account")
    )
    private AccountEntity sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "destination_account_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_transfer_destination_account")
    )
    private AccountEntity destinationAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "requested_by_customer_id",
            foreignKey = @ForeignKey(name = "fk_transfer_requested_by_customer")
    )
    private CustomerEntity requestedByCustomer;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "ledger_transaction_id",
            foreignKey = @ForeignKey(name = "fk_transfer_ledger_transaction")
    )
    private LedgerTransactionEntity ledgerTransaction;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_type", nullable = false, length = 50)
    private TransferType transferType;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_by_actor_type", nullable = false, length = 30)
    private RequestedByActorType requestedByActorType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TransferStatus status;

    @Column(name = "currency_code", nullable = false, length = 3)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currencyCode;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "failure_reason_code", length = 100)
    private String failureReasonCode;

    @Column(name = "failure_reason_detail", length = 1000)
    private String failureReasonDetail;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
