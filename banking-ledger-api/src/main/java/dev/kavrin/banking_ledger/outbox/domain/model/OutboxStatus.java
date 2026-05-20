package dev.kavrin.banking_ledger.outbox.domain.model;

public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED,
    DEAD_LETTER;

    public boolean canTransitionTo(OutboxStatus next) {
        return switch (this) {
            case PENDING -> next == PUBLISHED || next == FAILED || next == DEAD_LETTER;
            case FAILED -> next == PENDING || next == PUBLISHED || next == DEAD_LETTER;
            case PUBLISHED, DEAD_LETTER -> false;
        };
    }

    public void requireCanTransitionTo(OutboxStatus next) {
        if (!canTransitionTo(next)) {
            throw new IllegalStateException(
                    "Invalid outbox status transition: " + this + " -> " + next
            );
        }
    }
}
