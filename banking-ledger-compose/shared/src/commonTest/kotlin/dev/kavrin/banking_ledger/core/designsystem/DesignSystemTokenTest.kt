package dev.kavrin.banking_ledger.core.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class DesignSystemTokenTest {
    @Test
    fun statusKindsExposeStableLabels() {
        assertEquals("Completed", LedgerStatusKind.Completed.label())
        assertEquals("Mismatch", LedgerStatusKind.Mismatch.label())
        assertEquals("Failed", LedgerStatusKind.Failed.label())
    }

    @Test
    fun sentReusesCompletedStatusColors() {
        assertSame(BankingLedgerStatusDefaults.Completed, BankingLedgerStatusDefaults.Sent)
    }

    @Test
    fun darkBankingColorsMatchLegacyTokenAlias() {
        assertEquals(BankingLedgerColors.BackgroundApp, DarkBankingColors.backgroundApp)
        assertEquals(BankingLedgerColors.Primary, DarkBankingColors.primary)
        assertEquals(BankingLedgerColors.TextPrimary, DarkBankingColors.textPrimary)
    }

}
