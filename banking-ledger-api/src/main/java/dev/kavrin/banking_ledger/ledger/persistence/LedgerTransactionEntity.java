package dev.kavrin.banking_ledger.ledger.persistence;


import dev.kavrin.banking_ledger.ledger.domain.model.LedgerTransactionType;
import dev.kavrin.banking_ledger.ledger.domain.model.TransactionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "ledger_transactions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_ledger_transactions_external_ref",
                        columnNames = "external_reference"
                ),

                @UniqueConstraint(
                        name = "uk_ledger_transactions_id_currency",
                        columnNames = {"id", "currency_code"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class LedgerTransactionEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "transaction_type",
            nullable = false,
            length = 50
    )
    private LedgerTransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TransactionStatus status;

    @Column(
            name = "currency_code",
            nullable = false,
            length = 3
    )
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

    @Column(name = "posted_at")
    private OffsetDateTime postedAt;

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
