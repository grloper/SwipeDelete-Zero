package com.swipedelete.zero.domain.setup

/** The step of the connect wizard that can actually resolve a given failure. */
enum class SetupStep(val number: Int, val title: String) {
    CREATE_PROJECT(1, "Create a Google Cloud project"),
    ENABLE_APIS(2, "Enable the Photos & Drive APIs"),
    REGISTER_APP(3, "Register this app"),
    CONSENT_AND_TESTER(4, "Add yourself as a test user"),
    SIGN_IN(5, "Sign in & verify"),
}

/**
 * A decoded Google Sign-In failure: what went wrong, in the user's terms, and
 * which wizard step fixes it.
 *
 * The raw SDK surfaces bare integers ("code 10"), which tell a user nothing and
 * send them searching. Every code the flow can realistically produce is mapped
 * here to a plain-language cause, a concrete fix, and the step to jump to.
 */
data class AuthDiagnostic(
    val code: Int,
    val headline: String,
    val cause: String,
    val fix: String,
    val blamedStep: SetupStep?,
    /** False for user-initiated outcomes like cancelling — not a real error. */
    val isMisconfiguration: Boolean,
) {
    companion object {
        // Mirrors com.google.android.gms.common.api.CommonStatusCodes and
        // GoogleSignInStatusCodes, duplicated as plain ints so this mapping stays
        // in the main source set and unit-testable without Play Services.
        const val DEVELOPER_ERROR = 10
        const val NETWORK_ERROR = 7
        const val INTERNAL_ERROR = 8
        const val INVALID_ACCOUNT = 5
        const val SIGN_IN_REQUIRED = 4
        const val API_NOT_CONNECTED = 17
        const val CANCELED = 16
        const val SIGN_IN_FAILED = 12500
        const val SIGN_IN_CANCELLED = 12501
        const val SIGN_IN_CURRENTLY_IN_PROGRESS = 12502

        fun decode(code: Int): AuthDiagnostic = when (code) {
            DEVELOPER_ERROR -> AuthDiagnostic(
                code = code,
                headline = "OAuth client doesn't match this app",
                cause = "Google rejected the sign-in because no Android OAuth client is " +
                    "registered with BOTH this app's package name and the exact " +
                    "certificate it was signed with. This is the most common setup " +
                    "failure, and it is always a console-side mismatch — never a bug " +
                    "in the app or a wrong password.",
                fix = "Open step 3 and copy the package name and SHA-1 shown there — they " +
                    "are read from the app installed on this device, so they are " +
                    "always the values Google will see. Paste them into an Android " +
                    "OAuth client in the Google Cloud Console. If you already made " +
                    "one, compare it character by character: a fingerprint from a " +
                    "different build will not match.",
                blamedStep = SetupStep.REGISTER_APP,
                isMisconfiguration = true,
            )
            SIGN_IN_FAILED -> AuthDiagnostic(
                code = code,
                headline = "Sign-in failed",
                cause = "Google Play services could not complete the sign-in. This usually " +
                    "means the OAuth consent screen is incomplete, or the requested " +
                    "scopes have not been added to it.",
                fix = "Check that the consent screen in step 2 lists both the Photos " +
                    "Library and Drive scopes, then try again.",
                blamedStep = SetupStep.ENABLE_APIS,
                isMisconfiguration = true,
            )
            SIGN_IN_CANCELLED, CANCELED -> AuthDiagnostic(
                code = code,
                headline = "Sign-in cancelled",
                cause = "The account picker was dismissed before an account was chosen.",
                fix = "Tap Connect again and pick the Google account you added as a test user.",
                blamedStep = SetupStep.SIGN_IN,
                isMisconfiguration = false,
            )
            SIGN_IN_CURRENTLY_IN_PROGRESS -> AuthDiagnostic(
                code = code,
                headline = "Sign-in already in progress",
                cause = "A previous sign-in attempt has not finished.",
                fix = "Wait a moment for the account picker to appear, or reopen this screen.",
                blamedStep = SetupStep.SIGN_IN,
                isMisconfiguration = false,
            )
            NETWORK_ERROR -> AuthDiagnostic(
                code = code,
                headline = "No connection to Google",
                cause = "The device could not reach Google's servers.",
                fix = "Check your internet connection and try again. Nothing was uploaded.",
                blamedStep = null,
                isMisconfiguration = false,
            )
            INVALID_ACCOUNT -> AuthDiagnostic(
                code = code,
                headline = "Account not accepted",
                cause = "The chosen account is not permitted to use this OAuth client — " +
                    "while the consent screen is in Testing mode, only accounts listed " +
                    "as test users may sign in.",
                fix = "Add this Google account under Audience → Test users in the Cloud " +
                    "Console (step 4), or pick an account already on that list.",
                blamedStep = SetupStep.CONSENT_AND_TESTER,
                isMisconfiguration = true,
            )
            SIGN_IN_REQUIRED -> AuthDiagnostic(
                code = code,
                headline = "Not signed in",
                cause = "There is no active Google session for this app yet.",
                fix = "Tap Connect to sign in.",
                blamedStep = SetupStep.SIGN_IN,
                isMisconfiguration = false,
            )
            API_NOT_CONNECTED -> AuthDiagnostic(
                code = code,
                headline = "API not enabled",
                cause = "The Google API this app needs is not enabled on your Cloud project, " +
                    "or Play services could not connect to it.",
                fix = "Open step 2 and enable both the Photos Library API and the Drive API, " +
                    "then retry. Newly enabled APIs can take a minute to propagate.",
                blamedStep = SetupStep.ENABLE_APIS,
                isMisconfiguration = true,
            )
            INTERNAL_ERROR -> AuthDiagnostic(
                code = code,
                headline = "Google Play services error",
                cause = "Play services hit an internal error completing the request.",
                fix = "Retry. If it persists, update Google Play services and restart the device.",
                blamedStep = null,
                isMisconfiguration = false,
            )
            else -> AuthDiagnostic(
                code = code,
                headline = "Sign-in failed (code $code)",
                cause = "Google returned an error this app does not have specific guidance for.",
                fix = "Re-check steps 2 and 3 — the API being enabled and the package/SHA-1 " +
                    "pair being registered resolve almost every sign-in failure.",
                blamedStep = SetupStep.REGISTER_APP,
                isMisconfiguration = true,
            )
        }
    }
}
