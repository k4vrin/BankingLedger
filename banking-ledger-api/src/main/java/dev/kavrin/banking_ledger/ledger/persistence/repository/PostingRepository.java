package dev.kavrin.banking_ledger.ledger.persistence.repository;

import dev.kavrin.banking_ledger.ledger.persistence.entity.PostingEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface PostingRepository extends JpaRepository<PostingEntity, UUID> {
    long countByJournalEntry_LedgerTransaction_Id(UUID ledgerTransactionId);

    @Query(
            value = """
                    select p
                    from PostingEntity p
                    join fetch p.journalEntry je
                    join fetch je.ledgerTransaction lt
                    where p.account.id = :accountId
                      and (:from is null or p.postedAt >= :from)
                      and (:to is null or p.postedAt <= :to)
                    order by p.postedAt desc
                    """,
            countQuery = """
                    select count(p)
                    from PostingEntity p
                    where p.account.id = :accountId
                      and (:from is null or p.postedAt >= :from)
                      and (:to is null or p.postedAt <= :to)
                    """
    )
    Page<PostingEntity> findAccountTransactionHistory(
            @Param("accountId") UUID accountId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable
    );

    List<PostingEntity> findByJournalEntry_LedgerTransaction_Id(UUID ledgerTransactionId);
}
