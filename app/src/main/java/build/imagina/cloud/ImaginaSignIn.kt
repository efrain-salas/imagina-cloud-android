/*
 * Imagina Cloud (IMAGINA.md). New file: updates of Nextcloud never conflict with it.
 */
package build.imagina.cloud

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Base64
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.browser.auth.AuthTabIntent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.owncloud.android.R
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.function.Consumer
import kotlin.concurrent.thread

/**
 * «Entrar con Imagina»: instead of Nextcloud's login, the person signs in with Imagina (mobile number and SMS code)
 * in an Auth Tab that comes back to the app through its own scheme, and Imagina's devices API gives this device the
 * Nextcloud address and an app password of its own. Off unless the imagina_auth_host resource is set (the "imagina"
 * flavor sets it).
 *
 * OAuth 2.0 authorization code with PKCE against Imagina's OIDC server, public client, scope cloud.devices.
 */
object ImaginaSignIn {
    private const val PREFERENCES = "imagina_sign_in"
    private const val TIMEOUT_MS = 20_000
    private const val DEVICE_NAME_MAX = 80

    data class Credentials(val server: String, val user: String, val appPassword: String)

    /** What the sign-in came to: the credentials, or a sentence for the person (Imagina's, in their language). */
    private sealed interface Outcome {
        data class SignedIn(val credentials: Credentials) : Outcome
        data class Refused(val message: String?) : Outcome
    }

    private var problem: WeakReference<AlertDialog>? = null

    @JvmStatic
    fun isEnabled(context: Context): Boolean = context.getString(R.string.imagina_auth_host).isNotBlank()

    /** Opens Imagina's sign-in in an Auth Tab. The verifier and state survive the activity being recreated. */
    @JvmStatic
    fun launch(context: Context, launcher: ActivityResultLauncher<Intent>) {
        val verifier = randomToken()
        val state = randomToken()
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString("verifier", verifier)
            .putString("state", state)
            .apply()

        val uri = Uri.Builder()
            .scheme("https")
            .authority(context.getString(R.string.imagina_auth_host))
            .path("/oauth/authorize")
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", context.getString(R.string.imagina_oauth_client_id))
            .appendQueryParameter("redirect_uri", redirectUri(context))
            .appendQueryParameter("scope", "openid cloud.devices")
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challengeOf(verifier))
            .appendQueryParameter("code_challenge_method", "S256")
            .build()

        AuthTabIntent.Builder().build().launch(launcher, uri, context.packageName)
    }

    /**
     * Whether an intent brings Imagina's answer through the app's scheme: what happens when the browser has no
     * Auth Tab and opens a Custom Tab instead.
     */
    @JvmStatic
    fun isRedirect(context: Context, data: Uri?): Boolean =
        isEnabled(context) && data != null && data.toString().startsWith(redirectUri(context))

    /**
     * Finishes the sign-in from the Auth Tab result or the redirect: exchanges the code for a token, connects this
     * device with Imagina's devices API and hands its credentials to [onSignedIn] on the main thread. When the person
     * cancelled, or Imagina refused (no place in a Cloud, a Cloud not ready yet…), it says so with a button to try
     * again; leaving closes the app when there is no account yet ([leaveApp]) or only the sign-in otherwise.
     */
    @JvmStatic
    fun handleResult(
        activity: Activity,
        resultCode: Int,
        resultUri: Uri?,
        launcher: ActivityResultLauncher<Intent>,
        leaveApp: Boolean,
        onSignedIn: Consumer<Credentials>
    ) {
        val preferences = activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val waiting = preferences.getString("state", null)

        if (resultCode != AuthTabIntent.RESULT_OK || resultUri == null) {
            // Without an Auth Tab, closing the Custom Tab reports a cancel even when the redirect already came
            if (waiting != null) {
                showProblem(activity, null, cancelled = true, launcher, leaveApp)
            }
            return
        }

        problem?.get()?.dismiss()
        val code = resultUri.getQueryParameter("code")
        val verifier = preferences.getString("verifier", null)
        preferences.edit().clear().apply()
        if (code == null || verifier == null || resultUri.getQueryParameter("state") != waiting) {
            showProblem(activity, null, cancelled = false, launcher, leaveApp)
            return
        }

        thread {
            val outcome = try {
                connect(activity, code, verifier)
            } catch (exception: Exception) {
                Outcome.Refused(null)
            }
            activity.runOnUiThread {
                when (outcome) {
                    is Outcome.SignedIn -> onSignedIn.accept(outcome.credentials)
                    is Outcome.Refused -> showProblem(activity, outcome.message, cancelled = false, launcher, leaveApp)
                }
            }
        }
    }

    /** Blocking: the token for the code, then this device's credentials from Imagina. */
    private fun connect(context: Context, code: String, verifier: String): Outcome {
        val host = context.getString(R.string.imagina_auth_host)
        val (tokenStatus, tokenAnswer) = request(
            "https://$host/oauth/token",
            body = form(
                "grant_type" to "authorization_code",
                "client_id" to context.getString(R.string.imagina_oauth_client_id),
                "redirect_uri" to redirectUri(context),
                "code" to code,
                "code_verifier" to verifier,
            ),
            contentType = "application/x-www-form-urlencoded",
        )
        val token = tokenAnswer?.optString("access_token")?.takeIf { tokenStatus == HttpURLConnection.HTTP_OK && it.isNotBlank() }
            ?: return Outcome.Refused(null)

        val (status, answer) = request(
            "https://$host/api/v1/cloud/devices",
            body = JSONObject().put("name", deviceName(context)).put("platform", "ANDROID").toString(),
            contentType = "application/json",
            token = token,
        )
        val cloud = answer?.optJSONObject("cloud")
        if (status != HttpURLConnection.HTTP_CREATED || cloud == null) {
            return Outcome.Refused(answer?.optString("message")?.takeIf { it.isNotBlank() })
        }

        return Outcome.SignedIn(
            Credentials(
                server = cloud.getString("url"),
                user = cloud.getString("loginName"),
                appPassword = cloud.getString("appPassword"),
            )
        )
    }

    private fun showProblem(
        activity: Activity,
        message: String?,
        cancelled: Boolean,
        launcher: ActivityResultLauncher<Intent>,
        leaveApp: Boolean
    ) {
        if (activity.isFinishing) return
        problem?.get()?.dismiss()

        val text = message ?: activity.getString(if (cancelled) R.string.imagina_sign_in_cancelled else R.string.imagina_sign_in_failed)
        problem = WeakReference(
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.imagina_sign_in)
                .setMessage(text)
                .setCancelable(false)
                .setPositiveButton(R.string.imagina_sign_in) { _, _ -> launch(activity, launcher) }
                .setNegativeButton(R.string.imagina_sign_in_leave) { _, _ ->
                    if (leaveApp) activity.finishAffinity() else activity.finish()
                }
                .show()
        )
    }

    /** The name the person gave the phone, or its make and model: what «Mi cuenta» shows for this device. */
    private fun deviceName(context: Context): String {
        val given = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        val model = if (Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true)) {
            Build.MODEL
        } else {
            Build.MANUFACTURER.replaceFirstChar { it.titlecase(Locale.getDefault()) } + " " + Build.MODEL
        }

        return (given?.takeIf { it.isNotBlank() } ?: model).take(DEVICE_NAME_MAX)
    }

    private fun form(vararg fields: Pair<String, String>): String = fields.joinToString("&") { (key, value) ->
        URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8")
    }

    /** The status and JSON answer of a POST; the answer is null when it is not JSON. */
    private fun request(url: String, body: String, contentType: String, token: String? = null): Pair<Int, JSONObject?> {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", contentType)
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag())
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            connection.outputStream.use { it.write(body.toByteArray()) }

            val status = connection.responseCode
            val stream = if (status < HttpURLConnection.HTTP_BAD_REQUEST) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            status to runCatching { JSONObject(text) }.getOrNull()
        } finally {
            connection.disconnect()
        }
    }

    private fun redirectUri(context: Context) = "${context.packageName}://oauth2redirect"

    private fun randomToken(): String {
        val bytes = ByteArray(48).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun challengeOf(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
