package dev.kavrin.banking_ledger.ledger.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PostingRepository extends JpaRepository<PostingEntity, UUID> {
}
