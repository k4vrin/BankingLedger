package dev.kavrin.banking_ledger.core.network

actual val backendApiBaseUrl: String
    get() = System.getProperty("banking.ledger.apiBaseUrl")
        ?: System.getenv("BANKING_LEDGER_API_BASE_URL")
        ?: "http://localhost:8080"
