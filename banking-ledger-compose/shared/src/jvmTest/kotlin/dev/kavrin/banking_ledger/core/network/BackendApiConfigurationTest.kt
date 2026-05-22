package dev.kavrin.banking_ledger.core.network

import kotlin.test.Test
import kotlin.test.assertTrue

class BackendApiConfigurationTest {
    @Test
    fun baseUrlIsAvailableForDesktopTarget() {
        assertTrue(backendApiBaseUrl.startsWith("http"))
    }
}
