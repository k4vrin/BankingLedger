package dev.kavrin.banking_ledger.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LedgerMobileTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .padding(horizontal = BankingLedgerTheme.spacing.mobileScreenX),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigationIcon != null) {
            navigationIcon()
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.bankingTypography.mobileScreenTitle,
            color = MaterialTheme.bankingColors.textPrimary,
        )
        if (action != null) {
            action()
        }
    }
}

data class AdminNavigationItem(
    val label: String,
    val selected: Boolean = false,
    val enabled: Boolean = true,
)

@Composable
fun AdminSidebar(
    title: String,
    items: List<AdminNavigationItem>,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .width(280.dp)
            .fillMaxHeight(),
        color = MaterialTheme.bankingColors.backgroundPanel,
        contentColor = MaterialTheme.bankingColors.textPrimary,
        border = BorderStroke(1.dp, MaterialTheme.bankingColors.borderSubtle),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.bankingTypography.desktopPageTitle,
                color = MaterialTheme.bankingColors.textPrimary,
            )
            items.forEach { item ->
                AdminSidebarItem(item = item)
            }
            Spacer(modifier = Modifier.weight(1f))
            if (footer != null) {
                footer()
            }
        }
    }
}

@Composable
fun AdminSidebarItem(
    item: AdminNavigationItem,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
) {
    val containerColor = if (item.selected) MaterialTheme.bankingColors.surfaceSelected else androidx.compose.ui.graphics.Color.Transparent
    val contentColor = when {
        !item.enabled -> MaterialTheme.bankingColors.textMuted
        item.selected -> MaterialTheme.bankingColors.primary
        else -> MaterialTheme.bankingColors.textPrimary
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                icon()
            }
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}
