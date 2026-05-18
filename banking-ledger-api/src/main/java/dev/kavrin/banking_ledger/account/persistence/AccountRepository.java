package dev.kavrin.banking_ledger.account.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {
    public boolean existsByAccountNumber(String accountNumber);
    public Optional<AccountEntity> findByAccountNumber(String accountNumber);
}
