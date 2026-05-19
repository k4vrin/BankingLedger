package dev.kavrin.banking_ledger.account.persistence;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {
    public boolean existsByAccountNumber(String accountNumber);

    public Optional<AccountEntity> findByAccountNumber(String accountNumber);

    // When loading this row, acquire a database write lock.
    // Assume concurrent conflicts WILL happen, so lock early.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // Wait at most 3 seconds for the lock; if the lock can't be acquired in that time, throw a PessimisticLockingFailureException
    @QueryHints({
            @QueryHint(
                    name = "jakarta.persistence.lock.timeout",
                    value = "3000"
            )
    })
    @Query(
            """
                    select a
                    from AccountEntity a
                    where a.id =:id
                    """
    )
    Optional<AccountEntity> findByIdForUpdate(UUID id);
}
