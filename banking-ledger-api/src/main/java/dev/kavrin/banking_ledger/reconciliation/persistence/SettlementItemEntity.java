package dev.kavrin.banking_ledger.reconciliation.persistence;

import dev.kavrin.banking_ledger.reconciliation.domain.model.SettlementItemStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "settlement_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_settlement_source_external_ref",
                columnNames = {"source", "external_transaction_reference"}
        )
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SettlementItemEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private SettlementBatchEntity batch;

    @Column(name = "source", nullable = false, length = 100)
    private String source;

    @Column(name = "external_transaction_reference", nullable = false, length = 100)
    private String externalTransactionReference;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "currency_code", nullable = false, length = 3)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SettlementItemStatus status;

    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    @Column(name = "raw_line_hash", nullable = false, length = 128)
    private String rawLineHash;

    @Column(name = "metadata_json", columnDefinition = "CLOB")
    private String metadataJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
