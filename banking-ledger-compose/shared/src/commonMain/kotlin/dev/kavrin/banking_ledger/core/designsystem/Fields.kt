package dev.kavrin.banking_ledger.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LedgerOutlinedField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    helperText: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.bankingTypography.body,
            color = MaterialTheme.bankingColors.textSecondary,
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 58.dp),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.bankingColors.surfaceDefault,
            contentColor = MaterialTheme.bankingColors.textPrimary,
            border = BorderStroke(1.dp, MaterialTheme.bankingColors.borderStrong),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    leading()
                }
                Text(
                    text = value,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.bankingTypography.body,
                    color = MaterialTheme.bankingColors.textPrimary,
                )
                if (trailing != null) {
                    trailing()
                }
            }
        }
        if (helperText != null) {
            Text(
                text = helperText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.bankingColors.textMuted,
            )
        }
    }
}

@Composable
fun LedgerSelectField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    LedgerOutlinedField(
        label = label,
        value = value,
        modifier = modifier,
        helperText = supportingText,
        leading = leading,
        trailing = trailing,
    )
}
