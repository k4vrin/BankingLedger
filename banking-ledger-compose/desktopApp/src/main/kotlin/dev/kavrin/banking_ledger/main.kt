package dev.kavrin.banking_ledger

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "bankingLedger",
    ) {
        App()
    }
}