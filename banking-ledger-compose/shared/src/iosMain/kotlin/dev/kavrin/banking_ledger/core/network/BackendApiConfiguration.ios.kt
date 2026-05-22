package dev.kavrin.banking_ledger.core.network

import platform.Foundation.NSBundle

actual val backendApiBaseUrl: String
    get() = NSBundle.mainBundle
        .objectForInfoDictionaryKey("BANKING_LEDGER_API_BASE_URL")
        ?.toString()
        ?.takeIf { it.isNotBlank() && !it.startsWith("$") }
        ?: "http://localhost:8080"
