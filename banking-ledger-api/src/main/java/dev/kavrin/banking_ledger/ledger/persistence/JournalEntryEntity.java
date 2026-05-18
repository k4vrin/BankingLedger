package dev.kavrin.banking_ledger.ledger.persistence;

import dev.kavrin.banking_ledger.ledger.domain.model.JournalEntryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "journal_entries",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_journal_entries_id_currency",
                        columnNames = {"id", "currency_code"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class JournalEntryEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "ledger_transaction_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_journal_entries_transaction"
            )
    )
    private LedgerTransactionEntity ledgerTransaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 50)
    private JournalEntryType entryType;

    @Column(name = "currency_code", nullable = false, length = 3)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currencyCode;

    @Column(name = "total_debit_minor", nullable = false)
    private long totalDebitMinor;

    @Column(name = "total_credit_minor", nullable = false)
    private long totalCreditMinor;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "posted_at", nullable = false)
    private OffsetDateTime postedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
