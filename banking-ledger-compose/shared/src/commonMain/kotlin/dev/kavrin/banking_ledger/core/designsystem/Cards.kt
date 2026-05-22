package dev.kavrin.banking_ledger.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun IconContainer(
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    contentColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.bankingColors

    Surface(
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = containerColor ?: colors.primaryContainer,
        contentColor = contentColor ?: colors.primary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
fun BalanceSummaryCard(
    label: String,
    amount: String,
    modifier: Modifier = Modifier,
    trend: String? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.bankingColors

    Surface(
        modifier = modifier.defaultMinSize(minHeight = 132.dp),
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceDefault,
        contentColor = colors.textPrimary,
        border = BorderStroke(1.dp, colors.borderSubtle),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Text(
                    text = amount,
                    style = MaterialTheme.bankingTypography.metricNumber,
                    color = colors.textPrimary,
                )
                if (trend != null) {
                    Text(
                        text = trend,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.success,
                    )
                }
            }
            if (icon != null) {
                IconContainer(content = icon)
            }
        }
    }
}

@Composable
fun AccountCard(
    title: String,
    maskedIdentifier: String,
    amount: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.bankingColors
    val borderColor = if (selected) colors.primary else colors.borderSubtle
    val containerColor = if (selected) colors.surfaceSelected else colors.surfaceDefault

    Surface(
        modifier = modifier.defaultMinSize(minHeight = 116.dp),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        contentColor = colors.textPrimary,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                IconContainer(content = icon)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.bankingTypography.body,
                    color = colors.textPrimary,
                )
                Text(
                    text = maskedIdentifier,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = amount,
                    style = MaterialTheme.bankingTypography.mobileSectionTitle,
                    color = colors.textPrimary,
                )
            }
            if (trailing != null) {
                trailing()
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    statusColor: Color? = null,
    trend: String? = null,
    icon: (@Composable () -> Unit)? = null,
    sparkline: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.bankingColors
    val resolvedStatusColor = statusColor ?: colors.primary

    Surface(
        modifier = modifier.defaultMinSize(minHeight = 120.dp),
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceDefault,
        contentColor = colors.textPrimary,
        border = BorderStroke(1.dp, colors.borderSubtle),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    IconContainer(
                        modifier = Modifier.size(40.dp),
                        containerColor = resolvedStatusColor.copy(alpha = 0.14f),
                        contentColor = resolvedStatusColor,
                        content = icon,
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = value,
                        style = MaterialTheme.bankingTypography.metricNumber,
                        color = colors.textPrimary,
                    )
                    if (trend != null) {
                        Text(
                            text = trend,
                            style = MaterialTheme.typography.labelLarge,
                            color = resolvedStatusColor,
                        )
                    }
                }
                if (sparkline != null) {
                    sparkline()
                }
            }
        }
    }
}
