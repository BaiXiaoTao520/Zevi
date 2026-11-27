package com.zex.zexbrowse.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    private val checker = UpdateChecker()

    @Test fun ordersPrereleasesWithEqualNumericVersions() {
        assertNewer("12.5.0-beta", "12.5.0-alpha9")
        assertNewer("v12.5.0-beta2", "12.5.0-beta")
        assertNewer("12.5.0-BETA10", "12.5.0-beta2")
        assertNewer("12.5.0-rc", "12.5.0-beta10")
    }

    @Test fun preservesNumericAndFinalReleasePrecedence() {
        assertNewer("12.5.1-alpha", "12.5.0")
        assertNewer("12.10.0-beta", "12.9.0-rc")
        assertNewer("V12.5+build-info", "12.5.0-rc")
        assertFalse(checker.isNewer("12.5.0-beta02+other", "12.5.0-beta2+build"))
        assertFalse(checker.isNewer("12.5.0", "12.5"))
    }

    private fun assertNewer(newer: String, older: String) {
        assertTrue("$newer should outrank $older", checker.isNewer(newer, older))
        assertFalse("$older should not outrank $newer", checker.isNewer(older, newer))
    }
}
