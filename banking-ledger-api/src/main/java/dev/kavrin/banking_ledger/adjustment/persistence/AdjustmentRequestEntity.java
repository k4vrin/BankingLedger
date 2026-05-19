package dev.kavrin.banking_ledger.adjustment.persistence;

import dev.kavrin.banking_ledger.adjustment.domain.model.AdjustmentReasonCode;
import dev.kavrin.banking_ledger.adjustment.domain.model.AdjustmentStatus;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorRole;
import dev.kavrin.banking_ledger.ledger.persistence.entity.LedgerTransactionEntity;
import dev.kavrin.banking_ledger.transfer.domain.model.RequestedByActorType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "adjustment_requests",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_adjustment_requests_ledger_tx",
                columnNames = "ledger_transaction_id"
        )
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AdjustmentRequestEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "ledger_transaction_id",
            foreignKey = @ForeignKey(name = "fk_adjustment_requests_ledger_tx")
    )
    private LedgerTransactionEntity ledgerTransaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 100)
    private AdjustmentReasonCode reasonCode;

    @Column(name = "reason_detail", length = 1000)
    private String reasonDetail;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_by_actor_type", nullable = false, length = 30)
    private RequestedByActorType requestedByActorType;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_by_actor_role", length = 50)
    private AuditActorRole requestedByActorRole;

    @Column(name = "requested_by_actor_id", length = 100)
    private String requestedByActorId;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AdjustmentStatus status;

    @Column(name = "failure_reason_code", length = 100)
    private String failureReasonCode;

    @Column(name = "failure_reason_detail", length = 1000)
    private String failureReasonDetail;

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
