package dev.kavrin.banking_ledger

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform