package com.swipedelete.zero.domain.backup

import java.net.URI

/**
 * Minimum availability checks for a Google Photos metadata response.
 * AVAILABLE is NOT proof of original-byte integrity, a successful restore,
 * or an unchanged local source. Those require a separate backup proof.
 */
object PhotosMediaReadiness {
    enum class Result { AVAILABLE, WAITING, REJECTED }

    fun evaluate(mimeType: String, baseUrl: String?, videoStatus: String?): Result {
        val isVideo = mimeType.startsWith("video/")
        if (!isVideo && !mimeType.startsWith("image/")) return Result.REJECTED

        if (isVideo) {
            when (videoStatus) {
                "READY" -> Unit
                null, "", "UNSPECIFIED", "PROCESSING" -> return Result.WAITING
                else -> return Result.REJECTED // FAILED or an unknown future state.
            }
        }
        if (baseUrl.isNullOrBlank()) return Result.WAITING
        val uri = runCatching { URI(baseUrl) }.getOrNull() ?: return Result.REJECTED
        return if (uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() && uri.rawUserInfo == null) {
            Result.AVAILABLE
        } else {
            Result.REJECTED
        }
    }
}
