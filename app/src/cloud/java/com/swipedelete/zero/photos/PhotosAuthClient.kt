package com.swipedelete.zero.photos

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Abstraction for Google Account retrieval and OAuth token management.
 * Injects cleanly into [PhotosUploadWorker] so production uses the real Play Services
 * auth APIs while unit/orchestrator tests can simulate account switching, disconnects,
 * and scope-specific token invalidations without static mocking.
 */
interface PhotosAuthClient {
    fun getSignedInAccountName(context: Context): String?
    fun getSignedInAccount(context: Context): Account?
    fun getToken(context: Context, accountName: String, scope: String): String
    fun clearToken(context: Context, token: String)
}

@Singleton
class DefaultPhotosAuthClient @Inject constructor() : PhotosAuthClient {
    override fun getSignedInAccountName(context: Context): String? {
        return GoogleSignIn.getLastSignedInAccount(context)?.account?.name
    }

    override fun getSignedInAccount(context: Context): Account? {
        return GoogleSignIn.getLastSignedInAccount(context)?.account
    }

    override fun getToken(context: Context, accountName: String, scope: String): String {
        val account = getSignedInAccount(context) ?: Account(accountName, "com.google")
        return GoogleAuthUtil.getToken(context, account, scope)
    }

    override fun clearToken(context: Context, token: String) {
        GoogleAuthUtil.clearToken(context, token)
    }
}
