package com.swipedelete.zero.domain.setup

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The two values a Google OAuth **Android** client is matched on: the running
 * app's package name and the SHA-1 of the certificate it was actually signed
 * with.
 */
data class SigningIdentity(
    val packageName: String,
    /** Colon-separated uppercase SHA-1, e.g. "BB:3D:21:…:DB". Null if unreadable. */
    val sha1: String?,
    /** Colon-separated uppercase SHA-256 — some consoles ask for this instead. */
    val sha256: String?,
) {
    val isComplete: Boolean get() = sha1 != null
}

/**
 * Reads the identity of the APK **installed on this device**, at runtime.
 *
 * This exists because a hardcoded fingerprint in a setup doc is only correct
 * for one specific build. Anyone who rebuilds locally, or installs a
 * differently-signed APK, gets a certificate the doc never mentions — and
 * Google Sign-In then fails with DEVELOPER_ERROR (code 10) while the doc
 * insists everything is configured. Reading the signature from
 * [PackageManager] instead means the wizard always shows the fingerprint that
 * this exact install will present to Google, so it can never drift from
 * reality and the user never needs keytool.
 */
@Singleton
class SigningIdentityReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun read(): SigningIdentity {
        val signature = firstSignature()
        return SigningIdentity(
            packageName = context.packageName,
            sha1 = signature?.let { fingerprint(it, "SHA-1") },
            sha256 = signature?.let { fingerprint(it, "SHA-256") },
        )
    }

    private fun firstSignature(): Signature? = try {
        // minSdk is 29, so the modern signing-certificate API is always present.
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )
        val signingInfo = info.signingInfo
        when {
            signingInfo == null -> null
            // Rotated keys: the CURRENT signer is the last history entry, and it
            // is the one Google matches against.
            signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners?.firstOrNull()
            else -> signingInfo.signingCertificateHistory?.lastOrNull()
        }
    } catch (_: Exception) {
        null
    }

    private fun fingerprint(signature: Signature, algorithm: String): String? = try {
        MessageDigest.getInstance(algorithm)
            .digest(signature.toByteArray())
            .joinToString(":") { byte -> "%02X".format(byte) }
    } catch (_: Exception) {
        null
    }
}
