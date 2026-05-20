package dev.kavrin.banking_ledger.shared.error;

public class OutboxPayloadSerializationException extends RuntimeException {

    public OutboxPayloadSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}