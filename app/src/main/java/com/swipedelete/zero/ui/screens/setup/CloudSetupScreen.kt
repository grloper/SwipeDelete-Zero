package com.swipedelete.zero.ui.screens.setup

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swipedelete.zero.domain.backup.BackupState
import com.swipedelete.zero.domain.setup.AuthDiagnostic
import com.swipedelete.zero.domain.setup.SetupStep
import com.swipedelete.zero.domain.setup.SetupStepContent
import com.swipedelete.zero.domain.setup.SigningIdentity
import com.swipedelete.zero.ui.theme.SdzColors

/**
 * The one-time Google connection wizard.
 *
 * Two things make this different from following a setup document: the package
 * name and SHA-1 shown in step 3 are read from the APK *running on this
 * device*, so they can never be stale, and every failure is decoded into the
 * specific step that fixes it instead of a bare status code.
 */
@Composable
fun CloudSetupScreen(
    onBack: () -> Unit,
    viewModel: CloudSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> viewModel.onSignInResult(result.data) }

    Box(
        Modifier
            .fillMaxSize()
            .background(SdzColors.PitchBlack),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = SdzColors.PureWhite,
                        modifier = Modifier
                            .size(26.dp)
                            .clickable(onClick = onBack),
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        "Setup Wizard",
                        color = SdzColors.MutedGray,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Connect Google Photos",
                    color = SdzColors.PureWhite,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    "A one-time setup on your own Google account. " +
                        "Nothing is uploaded until you swipe a card up.",
                    color = SdzColors.MutedGray,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            state.diagnostic?.let { diagnostic ->
                item("diagnostic") { DiagnosticBanner(diagnostic) }
            }

            if (state.isConnected) {
                item("connected") {
                    ConnectedBanner(
                        email = (state.backupState as BackupState.Ready).accountEmail,
                        onDisconnect = viewModel::signOut,
                    )
                }
            }

            items(state.steps, key = { it.step.name }) { content ->
                StepCard(
                    content = content,
                    identity = state.identity,
                    expanded = state.expandedStep == content.step,
                    complete = state.isComplete(content.step),
                    blamed = state.diagnostic?.blamedStep == content.step,
                    isConnected = state.isConnected,
                    onToggleExpand = { viewModel.expand(content.step) },
                    onToggleComplete = { viewModel.toggleComplete(content.step) },
                    onOpenLink = { url -> context.openUrl(url) },
                    onConnect = {
                        viewModel.signInIntent()?.let { signInLauncher.launch(it) }
                    },
                )
            }

            state.lastCheck?.let { check ->
                item("check") { VerificationResultCard(check) }
            }
        }

        VerifyBar(
            verifying = state.verifying,
            onVerify = viewModel::verify,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(20.dp),
        )
    }
}

@Composable
private fun DiagnosticBanner(diagnostic: AuthDiagnostic) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SdzColors.HyperCoral.copy(alpha = 0.10f))
            .border(1.dp, SdzColors.HyperCoral.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = SdzColors.HyperCoral,
                modifier = Modifier.size(20.dp),
            )
            Text(
                diagnostic.headline,
                color = SdzColors.HyperCoral,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(diagnostic.cause, color = SdzColors.PureWhite, style = MaterialTheme.typography.bodyMedium)
        Text(diagnostic.fix, color = SdzColors.MutedGray, style = MaterialTheme.typography.bodyMedium)
        Text(
            "error code ${diagnostic.code}",
            color = SdzColors.MutedGray,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun ConnectedBanner(email: String, onDisconnect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SdzColors.ElectricEmerald.copy(alpha = 0.10f))
            .border(1.dp, SdzColors.ElectricEmerald.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Rounded.Verified,
            contentDescription = null,
            tint = SdzColors.ElectricEmerald,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                "Connected",
                color = SdzColors.ElectricEmerald,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(email, color = SdzColors.MutedGray, style = MaterialTheme.typography.labelMedium)
        }
        Text(
            "Disconnect",
            color = SdzColors.MutedGray,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .clickable(onClick = onDisconnect)
                .padding(6.dp),
        )
    }
}

@Composable
private fun StepCard(
    content: SetupStepContent,
    identity: SigningIdentity,
    expanded: Boolean,
    complete: Boolean,
    blamed: Boolean,
    isConnected: Boolean,
    onToggleExpand: () -> Unit,
    onToggleComplete: () -> Unit,
    onOpenLink: (String) -> Unit,
    onConnect: () -> Unit,
) {
    val accent = when {
        blamed -> SdzColors.HyperCoral
        complete -> SdzColors.ElectricEmerald
        expanded -> SdzColors.CrispCyan
        else -> SdzColors.Hairline
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SdzColors.Obsidian)
            .border(1.dp, accent.copy(alpha = if (expanded || blamed) 0.6f else 0.25f), RoundedCornerShape(20.dp))
            .clickable(onClick = onToggleExpand)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StepBadge(number = content.step.number, complete = complete, accent = accent)
            Text(
                content.step.title,
                color = if (complete) SdzColors.MutedGray else SdzColors.PureWhite,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    content.summary,
                    color = SdzColors.MutedGray,
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (content.showsCredentials) {
                    CredentialRow("Package name", identity.packageName)
                    CredentialRow("SHA-1 fingerprint", identity.sha1 ?: "unavailable on this device")
                    Text(
                        "Read from the app installed on THIS device, so it always matches " +
                            "what Google sees. No keytool needed.",
                        color = SdzColors.CrispCyan,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                content.instructions.forEach { instruction ->
                    Text(
                        instruction.text,
                        color = SdzColors.PureWhite,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (instruction.linkUrl != null && instruction.linkLabel != null) {
                        LinkButton(instruction.linkLabel) { onOpenLink(instruction.linkUrl) }
                    }
                }

                if (content.step == SetupStep.SIGN_IN && !isConnected) {
                    PrimaryButton("Connect Google account", onConnect)
                }

                if (content.step != SetupStep.SIGN_IN) {
                    Text(
                        if (complete) "✓ Marked done — tap to undo" else "Mark this step done",
                        color = if (complete) SdzColors.ElectricEmerald else SdzColors.MutedGray,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .clickable(onClick = onToggleComplete)
                            .padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StepBadge(number: Int, complete: Boolean, accent: Color) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (complete) SdzColors.ElectricEmerald else Color.Transparent)
            .border(1.5.dp, if (complete) SdzColors.ElectricEmerald else accent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (complete) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = SdzColors.PitchBlack,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(
                "$number",
                color = accent,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** A read-only value with one-tap copy — the whole point of the wizard. */
@Composable
private fun CredentialRow(label: String, value: String) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SdzColors.PitchBlack)
            .border(1.dp, SdzColors.Hairline, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label.uppercase(),
            color = SdzColors.MutedGray,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value,
                color = SdzColors.ElectricEmerald,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Rounded.ContentCopy,
                contentDescription = "Copy $label",
                tint = SdzColors.CrispCyan,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { context.copyToClipboard(label, value) }
                    .padding(7.dp),
            )
        }
    }
}

@Composable
private fun LinkButton(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SdzColors.CrispCyan.copy(alpha = 0.10f))
            .border(1.dp, SdzColors.CrispCyan.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            color = SdzColors.CrispCyan,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Rounded.OpenInNew,
            contentDescription = null,
            tint = SdzColors.CrispCyan,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SdzColors.ElectricEmerald)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = SdzColors.PitchBlack,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun VerificationResultCard(check: com.swipedelete.zero.domain.backup.ConnectionCheck) {
    val accent = if (check.allGood) SdzColors.ElectricEmerald else SdzColors.HyperCoral
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (check.allGood) "Everything works" else "Verification report",
            color = accent,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
        CheckLine("Signed in", check.signedIn)
        check.driveOk?.let { CheckLine("Google Drive API", it) }
        check.photosOk?.let { CheckLine("Google Photos API", it) }
        Text(check.message, color = SdzColors.MutedGray, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CheckLine(label: String, ok: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = if (ok) SdzColors.ElectricEmerald else SdzColors.HyperCoral,
            modifier = Modifier.size(16.dp),
        )
        Text(label, color = SdzColors.PureWhite, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun VerifyBar(
    verifying: Boolean,
    onVerify: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SdzColors.Obsidian)
            .border(1.5.dp, SdzColors.CrispCyan, RoundedCornerShape(18.dp))
            .clickable(enabled = !verifying, onClick = onVerify)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (verifying) {
            CircularProgressIndicator(
                color = SdzColors.CrispCyan,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(10.dp))
        }
        Text(
            if (verifying) "Verifying…" else "Verify connection",
            color = SdzColors.CrispCyan,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private fun Context.copyToClipboard(label: String, value: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    // Android 13+ shows its own copy confirmation; a second toast would double up.
    if (Build.VERSION.SDK_INT < 33) {
        Toast.makeText(this, "$label copied", Toast.LENGTH_SHORT).show()
    }
}

private fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "No browser available to open $url", Toast.LENGTH_LONG).show()
    }
}
