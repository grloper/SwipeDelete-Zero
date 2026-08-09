package com.swipedelete.zero

import com.swipedelete.zero.domain.setup.AuthDiagnostic
import com.swipedelete.zero.domain.setup.SetupStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthDiagnosticTest {

    @Test
    fun `code 10 blames the app registration step`() {
        val d = AuthDiagnostic.decode(AuthDiagnostic.DEVELOPER_ERROR)
        assertEquals(SetupStep.REGISTER_APP, d.blamedStep)
        assertTrue(d.isMisconfiguration)
        // The whole point is that the user never sees a bare number.
        assertFalse(d.headline.contains("10"))
        assertTrue(d.fix.isNotBlank())
    }

    @Test
    fun `cancelling is not reported as a misconfiguration`() {
        assertFalse(AuthDiagnostic.decode(AuthDiagnostic.SIGN_IN_CANCELLED).isMisconfiguration)
        assertFalse(AuthDiagnostic.decode(AuthDiagnostic.CANCELED).isMisconfiguration)
        assertFalse(AuthDiagnostic.decode(AuthDiagnostic.NETWORK_ERROR).isMisconfiguration)
    }

    @Test
    fun `invalid account points at the test-user list`() {
        assertEquals(
            SetupStep.CONSENT_AND_TESTER,
            AuthDiagnostic.decode(AuthDiagnostic.INVALID_ACCOUNT).blamedStep,
        )
    }

    @Test
    fun `api not connected points at enabling the APIs`() {
        assertEquals(
            SetupStep.ENABLE_APIS,
            AuthDiagnostic.decode(AuthDiagnostic.API_NOT_CONNECTED).blamedStep,
        )
    }

    @Test
    fun `unknown codes still produce actionable guidance`() {
        val d = AuthDiagnostic.decode(4242)
        assertEquals(4242, d.code)
        assertTrue(d.headline.contains("4242"))
        assertTrue(d.fix.isNotBlank())
        assertEquals(SetupStep.REGISTER_APP, d.blamedStep)
    }

    @Test
    fun `every decoded diagnostic carries a cause and a fix`() {
        val codes = listOf(
            AuthDiagnostic.DEVELOPER_ERROR, AuthDiagnostic.NETWORK_ERROR,
            AuthDiagnostic.INTERNAL_ERROR, AuthDiagnostic.INVALID_ACCOUNT,
            AuthDiagnostic.SIGN_IN_REQUIRED, AuthDiagnostic.API_NOT_CONNECTED,
            AuthDiagnostic.CANCELED, AuthDiagnostic.SIGN_IN_FAILED,
            AuthDiagnostic.SIGN_IN_CANCELLED, AuthDiagnostic.SIGN_IN_CURRENTLY_IN_PROGRESS,
        )
        codes.forEach { code ->
            val d = AuthDiagnostic.decode(code)
            assertTrue("cause missing for $code", d.cause.isNotBlank())
            assertTrue("fix missing for $code", d.fix.isNotBlank())
            assertEquals(code, d.code)
        }
    }
}
