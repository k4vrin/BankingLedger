package dev.kavrin.banking_ledger.outbox.domain.model;

public enum OutboxDestination {
    LEDGER_EVENTS("banking-ledger.ledger-events"),
    ACCOUNT_EVENTS("banking-ledger.account-events"),
    RECONCILIATION_EVENTS("banking-ledger.reconciliation-events");

    private final String destinationName;

    OutboxDestination(String destinationName) {
        this.destinationName = destinationName;
    }

    public String destinationName() {
        return destinationName;
    }
}
