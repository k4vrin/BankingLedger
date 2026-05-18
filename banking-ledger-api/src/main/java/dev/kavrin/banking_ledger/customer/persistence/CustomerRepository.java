package dev.kavrin.banking_ledger.customer.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomerRepository extends JpaRepository<CustomerEntity, UUID> {
}
