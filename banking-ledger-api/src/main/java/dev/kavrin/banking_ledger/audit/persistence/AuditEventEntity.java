package dev.kavrin.banking_ledger.audit.persistence;

import dev.kavrin.banking_ledger.audit.domain.model.AuditActorRole;
import dev.kavrin.banking_ledger.audit.domain.model.AuditActorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AuditEventEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "actor_id", length = 100)
    private String actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", length = 50)
    private AuditActorRole actorRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 30)
    private AuditActorType actorType;

    @Column(name = "channel", length = 50)
    private String channel;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Lob
    @Column(name = "event_payload")
    private String eventPayload;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
