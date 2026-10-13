/*
 * Imagina Cloud (IMAGINA.md). New file: updates of Nextcloud never conflict with it.
 */
package build.imagina.cloud

import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.owncloud.android.R
import com.owncloud.android.lib.common.utils.Log_OC
import org.json.JSONObject
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit

/**
 * This phone as a device in Imagina («Mi cuenta»). «Entrar con Imagina» creates it; this keeps what is needed to
 * disconnect it when the person removes the account in the app, or signs in again.
 *
 * Per Imagina login name (Nextcloud's user), in the app's private preferences (the app does not allow backups, so
 * nothing leaves the phone with them): the device id and the OAuth tokens of the sign-in. The access token lasts 15
 * minutes and the refresh token 7 days, renewed with every refresh, so a periodic job ([ImaginaDeviceWork]) refreshes
 * them while the account lives, and a device whose account was removed some other way (Android's settings) is
 * disconnected by the same job.
 *
 * Everything here is best effort and blocking: call it off the main thread. Off unless the imagina_auth_host resource
 * is set.
 */
object ImaginaDevice {
    private const val TAG = "ImaginaDevice"
    private const val PREFERENCES = "imagina_device"
    private const val KEEP_ALIVE_WORK = "imagina_device_keep_alive"
    private const val KEEP_ALIVE_DAYS = 2L
    private const val KEEP_ALIVE_FLEX_HOURS = 12L
    private const val ACCESS_MARGIN_MS = 60_000L
    private const val NEW_DEVICE_GRACE_MS = 10 * 60_000L

    /** One at a time: the refresh token is renewed with every refresh, so two refreshes at once would kill it. */
    private val lock = Any()

    private data class Record(
        val loginName: String,
        val deviceId: String,
        val createdAt: Long,
        val tokens: ImaginaTokens
    )

    /** What Imagina answered to deleting a device: done, not one of this token's person, token refused, or nothing. */
    private enum class Deleted { GONE, MISSING, REFUSED, FAILED }

    private sealed interface Access {
        data class Token(val value: String) : Access
        data object Dead : Access
        data object Unreachable : Access
    }

    /** Keeps the device just created by the sign-in, with the tokens it was given, and starts caring for them. */
    internal fun remember(context: Context, loginName: String, deviceId: String, tokens: ImaginaTokens) {
        runCatching {
            val record = Record(loginName, deviceId, System.currentTimeMillis(), tokens)
            synchronized(lock) { save(context, record) }
            schedule(context)
        }.onFailure { Log_OC.w(TAG, "Could not keep the device: ${it.message}") }
    }

    /**
     * Before a new sign-in creates its device: disconnects the ones of earlier sign-ins (the same account signing in
     * again, or an account removed some other way). [newToken] is the one the person just got.
     */
    internal fun disconnectPrevious(context: Context, newToken: String) {
        for (record in records(context)) {
            runCatching {
                if (disconnect(context, record, newToken)) {
                    forget(context, record)
                }
            }.onFailure { Log_OC.w(TAG, "Could not disconnect an earlier device: ${it.message}") }
        }
    }

    /**
     * The person removed the account [accountName] in the app: disconnects its device in Imagina and forgets what was
     * kept. If Imagina cannot be reached, [maintain] tries again later.
     */
    @JvmStatic
    fun accountRemoved(context: Context, accountName: String) {
        if (!ImaginaSignIn.isEnabled(context) || accountExists(context, accountName)) return

        for (record in records(context).filter { accountName.startsWith(it.loginName + "@") }) {
            runCatching {
                if (disconnect(context, record)) {
                    forget(context, record)
                }
            }.onFailure { Log_OC.w(TAG, "Could not disconnect the device of a removed account: ${it.message}") }
        }

        cancelKeepAliveWhenIdle(context)
    }

    /**
     * What the periodic job does: renews the tokens of the accounts that live, so the refresh token never expires, and
     * disconnects the devices of the accounts that are gone.
     */
    internal fun maintain(context: Context) {
        for (record in records(context)) {
            runCatching {
                if (hasAccount(context, record.loginName)) {
                    freshAccess(context, record.loginName)
                } else if (isOlderThanSignIn(record) && disconnect(context, record)) {
                    forget(context, record)
                }
            }.onFailure { Log_OC.w(TAG, "Could not look after a device: ${it.message}") }
        }

        cancelKeepAliveWhenIdle(context)
    }

    private fun cancelKeepAliveWhenIdle(context: Context) {
        if (records(context).isEmpty()) {
            WorkManager.getInstance(context).cancelUniqueWork(KEEP_ALIVE_WORK)
        }
    }

    private fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ImaginaDeviceWork>(
            KEEP_ALIVE_DAYS,
            TimeUnit.DAYS,
            KEEP_ALIVE_FLEX_HOURS,
            TimeUnit.HOURS
        )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(KEEP_ALIVE_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Deletes the device in Imagina. True when there is nothing more to do with the record: the device is gone, or it
     * can never be deleted from here (its tokens are dead; the person can disconnect it in «Mi cuenta»). False when
     * Imagina could not be reached.
     *
     * [newToken] is tried first when given: the person's token of a sign-in that is happening.
     */
    private fun disconnect(context: Context, record: Record, newToken: String? = null): Boolean {
        if (newToken != null) {
            // Another person's token does not find this device: only its own tokens can say it is gone
            when (delete(context, record.deviceId, newToken)) {
                Deleted.GONE -> return true
                Deleted.FAILED -> return false
                Deleted.MISSING, Deleted.REFUSED -> Unit
            }
        }

        return when (val access = freshAccess(context, record.loginName)) {
            is Access.Token -> delete(context, record.deviceId, access.value) != Deleted.FAILED
            Access.Dead -> true
            Access.Unreachable -> false
        }
    }

    private fun delete(context: Context, deviceId: String, token: String): Deleted = try {
        val host = context.getString(R.string.imagina_auth_host)
        val (status, _) = ImaginaApi.request("DELETE", "https://$host/api/v1/cloud/devices/$deviceId", token = token)

        when {
            status in 200..299 || status == HttpURLConnection.HTTP_GONE -> Deleted.GONE

            status == HttpURLConnection.HTTP_NOT_FOUND -> Deleted.MISSING

            status == HttpURLConnection.HTTP_UNAUTHORIZED ||
                status == HttpURLConnection.HTTP_FORBIDDEN -> Deleted.REFUSED

            else -> Deleted.FAILED
        }
    } catch (exception: Exception) {
        Deleted.FAILED
    }

    /** An access token that works, renewing the stored one with the refresh token when it is about to expire. */
    private fun freshAccess(context: Context, loginName: String): Access = synchronized(lock) {
        val record = read(context, loginName) ?: return Access.Dead
        if (record.tokens.accessExpiresAt - System.currentTimeMillis() > ACCESS_MARGIN_MS) {
            return Access.Token(record.tokens.accessToken)
        }

        val refreshToken = record.tokens.refreshToken ?: return Access.Dead
        val (status, answer) = try {
            ImaginaApi.request(
                "POST",
                "https://${context.getString(R.string.imagina_auth_host)}/oauth/token",
                body = ImaginaApi.form(
                    "grant_type" to "refresh_token",
                    "client_id" to context.getString(R.string.imagina_oauth_client_id),
                    "refresh_token" to refreshToken
                ),
                contentType = "application/x-www-form-urlencoded"
            )
        } catch (exception: Exception) {
            return Access.Unreachable
        }

        val renewed = ImaginaTokens.parse(answer, refreshToken)
        when {
            status == HttpURLConnection.HTTP_OK && renewed != null -> {
                save(context, record.copy(tokens = renewed))
                Access.Token(renewed.accessToken)
            }

            status == HttpURLConnection.HTTP_BAD_REQUEST || status == HttpURLConnection.HTTP_UNAUTHORIZED -> Access.Dead

            else -> Access.Unreachable
        }
    }

    /** Whether an account of this login name is still in the phone. */
    private fun hasAccount(context: Context, loginName: String): Boolean =
        accounts(context).any { it.startsWith("$loginName@") }

    /** A sign-in keeps its device before the account exists: for a few minutes, no account is no orphan. */
    private fun isOlderThanSignIn(record: Record): Boolean =
        System.currentTimeMillis() - record.createdAt > NEW_DEVICE_GRACE_MS

    private fun accountExists(context: Context, accountName: String): Boolean = accounts(context).contains(accountName)

    private fun accounts(context: Context): List<String> = AccountManager.get(context)
        .getAccountsByType(context.getString(R.string.account_type))
        .map { it.name }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    /** Written at once (not in the background): the tokens are what disconnects the device later. */
    @SuppressLint("ApplySharedPref")
    private fun save(context: Context, record: Record) {
        val json = JSONObject()
            .put("deviceId", record.deviceId)
            .put("createdAt", record.createdAt)
            .put("tokens", record.tokens.toJson())

        preferences(context).edit().putString(record.loginName, json.toString()).commit()
    }

    private fun read(context: Context, loginName: String): Record? =
        preferences(context).getString(loginName, null)?.let { parse(loginName, it) }

    private fun records(context: Context): List<Record> = synchronized(lock) {
        preferences(context).all.mapNotNull { (loginName, value) -> (value as? String)?.let { parse(loginName, it) } }
    }

    private fun parse(loginName: String, text: String): Record? = runCatching {
        val json = JSONObject(text)
        val tokens = ImaginaTokens.fromJson(json.getJSONObject("tokens")) ?: return null

        Record(loginName, json.getString("deviceId"), json.optLong("createdAt"), tokens)
    }.getOrNull()

    /** Forgets the record, unless a newer sign-in already replaced it. */
    @SuppressLint("ApplySharedPref")
    private fun forget(context: Context, record: Record) {
        synchronized(lock) {
            if (read(context, record.loginName)?.deviceId == record.deviceId) {
                preferences(context).edit().remove(record.loginName).commit()
            }
        }
    }
}

/**
 * Every couple of days while the phone has a network: [ImaginaDevice.maintain]. Nextcloud's job factory does not know
 * this class and leaves it to WorkManager's default one.
 */
class ImaginaDeviceWork(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        ImaginaDevice.maintain(applicationContext)

        return Result.success()
    }
}
