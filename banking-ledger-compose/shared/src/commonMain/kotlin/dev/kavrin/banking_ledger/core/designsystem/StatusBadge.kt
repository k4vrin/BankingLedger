package dev.kavrin.banking_ledger.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class LedgerStatusKind {
    Completed,
    Sent,
    Pending,
    Mismatch,
    Failed,
    Error,
    Info,
}

fun LedgerStatusKind.label(): String = when (this) {
    LedgerStatusKind.Completed -> "Completed"
    LedgerStatusKind.Sent -> "Sent"
    LedgerStatusKind.Pending -> "Pending"
    LedgerStatusKind.Mismatch -> "Mismatch"
    LedgerStatusKind.Failed -> "Failed"
    LedgerStatusKind.Error -> "Error"
    LedgerStatusKind.Info -> "Info"
}

@Composable
fun LedgerStatusKind.colors(): BankingLedgerStatusColors = when (this) {
    LedgerStatusKind.Completed -> BankingLedgerStatusDefaults.Completed
    LedgerStatusKind.Sent -> BankingLedgerStatusDefaults.Sent
    LedgerStatusKind.Pending -> BankingLedgerStatusDefaults.Pending
    LedgerStatusKind.Mismatch -> BankingLedgerStatusDefaults.Mismatch
    LedgerStatusKind.Failed -> BankingLedgerStatusDefaults.Failed
    LedgerStatusKind.Error -> BankingLedgerStatusDefaults.Error
    LedgerStatusKind.Info -> BankingLedgerStatusDefaults.Info
}

@Composable
fun StatusBadge(
    status: LedgerStatusKind,
    modifier: Modifier = Modifier,
    label: String = status.label(),
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = status.colors()

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = colors.container,
        contentColor = colors.content,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                leadingIcon()
            }
            Text(
                text = label,
                style = MaterialTheme.bankingTypography.label,
                color = colors.content,
            )
        }
    }
}
