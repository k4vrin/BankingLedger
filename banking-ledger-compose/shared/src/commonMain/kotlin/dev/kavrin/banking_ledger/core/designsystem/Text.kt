package dev.kavrin.banking_ledger.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun AmountText(
    amount: String,
    modifier: Modifier = Modifier,
    positive: Boolean? = null,
) {
    val colors = MaterialTheme.bankingColors
    val color = when (positive) {
        true -> colors.success
        false -> colors.textPrimary
        null -> colors.textPrimary
    }
    Text(
        text = amount,
        modifier = modifier,
        style = MaterialTheme.bankingTypography.mobileSectionTitle,
        color = color,
    )
}

@Composable
fun MaskedIdentifierText(
    value: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    val colors = MaterialTheme.bankingColors

    Text(
        text = value,
        modifier = modifier,
        style = MaterialTheme.bankingTypography.tableBody,
        color = color ?: colors.textSecondary,
    )
}
