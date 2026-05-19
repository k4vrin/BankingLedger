package dev.kavrin.banking_ledger.transfer.application.query;

public record GetTransferByExternalReferenceQuery(
        String externalReference
) {
}
