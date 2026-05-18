package dev.kavrin.banking_ledger.account.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {
}
