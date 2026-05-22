package dev.kavrin.banking_ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.kavrin.banking_ledger.core.designsystem.AccountCard
import dev.kavrin.banking_ledger.core.designsystem.BalanceSummaryCard
import dev.kavrin.banking_ledger.core.designsystem.BankingLedgerTheme
import dev.kavrin.banking_ledger.core.designsystem.LedgerMobileTopBar
import dev.kavrin.banking_ledger.core.designsystem.LedgerStatusKind
import dev.kavrin.banking_ledger.core.designsystem.MobilePrimaryActionButton
import dev.kavrin.banking_ledger.core.designsystem.StatusBadge
import dev.kavrin.banking_ledger.core.designsystem.bankingColors

@Composable
@Preview
fun App() {
    BankingLedgerTheme {
        Column(
            modifier = Modifier
                .background(MaterialTheme.bankingColors.backgroundApp)
                .safeContentPadding()
                .fillMaxSize()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            LedgerMobileTopBar(title = "Banking Ledger")
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BalanceSummaryCard(
                    label = "Available balance",
                    amount = "$12,458.75",
                    trend = "+$1,234.50 (9.85%)",
                )
                AccountCard(
                    title = "Checking",
                    maskedIdentifier = ".... 4578",
                    amount = "$7,256.34",
                    selected = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBadge(status = LedgerStatusKind.Completed)
                    StatusBadge(status = LedgerStatusKind.Pending)
                    StatusBadge(status = LedgerStatusKind.Failed)
                }
                MobilePrimaryActionButton(
                    text = "New transfer",
                    onClick = {},
                )
            }
        }
    }
}
