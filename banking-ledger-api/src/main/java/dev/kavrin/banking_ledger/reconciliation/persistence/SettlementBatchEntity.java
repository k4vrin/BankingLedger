package dev.kavrin.banking_ledger.reconciliation.persistence;

import dev.kavrin.banking_ledger.reconciliation.domain.model.SettlementBatchStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "settlement_batches")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SettlementBatchEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "source", nullable = false, length = 100)
    private String source;

    @Column(name = "reference_name", nullable = false, length = 255)
    private String referenceName;

    @Column(name = "imported_by_actor", nullable = false, length = 100)
    private String importedByActor;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SettlementBatchStatus status;

    @CreationTimestamp
    @Column(name = "imported_at", nullable = false)
    private OffsetDateTime importedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    @Column(name = "matched_count", nullable = false)
    private int matchedCount;

    @Column(name = "mismatch_count", nullable = false)
    private int mismatchCount;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
