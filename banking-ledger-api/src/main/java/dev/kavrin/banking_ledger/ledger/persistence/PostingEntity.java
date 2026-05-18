package dev.kavrin.banking_ledger.ledger.persistence;

import dev.kavrin.banking_ledger.account.persistence.AccountEntity;
import dev.kavrin.banking_ledger.ledger.domain.model.PostingDirection;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "postings")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PostingEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "journal_entry_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_postings_journal_entry"
            )
    )
    private JournalEntryEntity journalEntry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "account_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_postings_account"
            )
    )
    private AccountEntity account;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private PostingDirection direction;

    @Column(name = "currency_code", nullable = false, length = 3)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currencyCode;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "posted_at", nullable = false)
    private OffsetDateTime postedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

}
