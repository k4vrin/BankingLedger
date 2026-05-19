package dev.kavrin.banking_ledger.outbox;

public enum OutboxDestination {
    LEDGER_EVENTS("ledger-events");

    private final String destinationName;

    OutboxDestination(String destinationName) {
        this.destinationName = destinationName;
    }

    public String destinationName() {
        return destinationName;
    }
}
