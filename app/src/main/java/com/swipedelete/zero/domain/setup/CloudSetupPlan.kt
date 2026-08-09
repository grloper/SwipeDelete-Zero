package com.swipedelete.zero.domain.setup

/** A single instruction line inside a wizard step. */
data class SetupInstruction(
    val text: String,
    /** Optional deep link opened by a button on this line. */
    val linkLabel: String? = null,
    val linkUrl: String? = null,
)

/** Everything the UI needs to render one step; no Android types, so it is testable. */
data class SetupStepContent(
    val step: SetupStep,
    val summary: String,
    val instructions: List<SetupInstruction>,
    /** True when this step shows the copyable package name + SHA-1 rows. */
    val showsCredentials: Boolean = false,
)

/**
 * The one-time Google Cloud setup, written out as data.
 *
 * Every link goes straight to the exact console page for that step, so the user
 * never has to navigate Google's console by hand — which is where this process
 * normally goes wrong.
 */
object CloudSetupPlan {

    const val CONSOLE_CREATE_PROJECT = "https://console.cloud.google.com/projectcreate"
    const val CONSOLE_ENABLE_PHOTOS =
        "https://console.cloud.google.com/apis/library/photoslibrary.googleapis.com"
    const val CONSOLE_ENABLE_DRIVE =
        "https://console.cloud.google.com/apis/library/drive.googleapis.com"
    const val CONSOLE_CREATE_OAUTH_CLIENT =
        "https://console.cloud.google.com/apis/credentials/oauthclient"
    const val CONSOLE_CREDENTIALS = "https://console.cloud.google.com/apis/credentials"
    const val CONSOLE_AUDIENCE = "https://console.cloud.google.com/auth/audience"

    val steps: List<SetupStepContent> = listOf(
        SetupStepContent(
            step = SetupStep.CREATE_PROJECT,
            summary = "A free Google Cloud project owns the credentials. Takes about a minute.",
            instructions = listOf(
                SetupInstruction(
                    text = "Create a project — any name works, e.g. \"swipedelete\".",
                    linkLabel = "Create project",
                    linkUrl = CONSOLE_CREATE_PROJECT,
                ),
            ),
        ),
        SetupStepContent(
            step = SetupStep.ENABLE_APIS,
            summary = "Turn on the two APIs this app talks to. Nothing is billed.",
            instructions = listOf(
                SetupInstruction(
                    text = "Enable the Photos Library API — needed to archive photos and videos.",
                    linkLabel = "Enable Photos API",
                    linkUrl = CONSOLE_ENABLE_PHOTOS,
                ),
                SetupInstruction(
                    text = "Enable the Drive API — needed for backing up kept files.",
                    linkLabel = "Enable Drive API",
                    linkUrl = CONSOLE_ENABLE_DRIVE,
                ),
            ),
        ),
        SetupStepContent(
            step = SetupStep.REGISTER_APP,
            summary = "Register this exact install so Google will trust it. " +
                "Copy both values below — they are read from the app on this device.",
            showsCredentials = true,
            instructions = listOf(
                SetupInstruction(
                    text = "Create an OAuth client, choose application type \"Android\", " +
                        "then paste the package name and SHA-1 from above.",
                    linkLabel = "Create OAuth client",
                    linkUrl = CONSOLE_CREATE_OAUTH_CLIENT,
                ),
                SetupInstruction(
                    text = "Already made one? Open it and check the two values match " +
                        "exactly — a fingerprint from a different build is the usual cause " +
                        "of error code 10.",
                    linkLabel = "Review credentials",
                    linkUrl = CONSOLE_CREDENTIALS,
                ),
            ),
        ),
        SetupStepContent(
            step = SetupStep.CONSENT_AND_TESTER,
            summary = "While the consent screen is in Testing mode, only accounts you " +
                "list may sign in. Add the account you plan to use.",
            instructions = listOf(
                SetupInstruction(
                    text = "Add your Google account under Test users.",
                    linkLabel = "Open Audience settings",
                    linkUrl = CONSOLE_AUDIENCE,
                ),
            ),
        ),
        SetupStepContent(
            step = SetupStep.SIGN_IN,
            summary = "Sign in, then let the app prove the connection actually works.",
            instructions = listOf(
                SetupInstruction(
                    text = "Connect your Google account, then run Verify — it calls both " +
                        "APIs for real and reports exactly what responded.",
                ),
            ),
        ),
    )

    fun contentFor(step: SetupStep): SetupStepContent = steps.first { it.step == step }
}
