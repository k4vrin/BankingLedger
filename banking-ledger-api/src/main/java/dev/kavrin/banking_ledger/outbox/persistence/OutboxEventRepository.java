package dev.kavrin.banking_ledger.outbox.persistence;

import dev.kavrin.banking_ledger.outbox.domain.model.OutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {
    List<OutboxEventEntity> findByAggregateIdIn(Collection<UUID> aggregateIds);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select e
    from OutboxEventEntity e
    where e.status = 'PENDING'
       or (e.status = 'FAILED' and e.nextRetryAt <= :now)
    order by e.createdAt asc, e.id asc
""")
    List<OutboxEventEntity> findPublishableEventsForUpdate(
            @Param("now") OffsetDateTime now,
            Pageable pageable
    );

    Page<OutboxEventEntity> findByStatusOrderByCreatedAtDescIdDesc(
            OutboxStatus status,
            Pageable pageable
    );

    long countByStatus(OutboxStatus status);

    @Query("""
    select min(e.createdAt)
    from OutboxEventEntity e
    where e.status = 'PENDING'
""")
    Optional<OffsetDateTime> findOldestPendingCreatedAt();
}
