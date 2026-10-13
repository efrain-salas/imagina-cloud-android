/*
 * Imagina Cloud (IMAGINA.md). New file: updates of Nextcloud never conflict with it.
 */
package build.imagina.cloud

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * What the app says to Imagina's servers (identity and devices API): plain HTTPS requests with JSON answers, shared by
 * the sign-in ([ImaginaSignIn]) and the care of the device afterwards ([ImaginaDevice]).
 */
internal object ImaginaApi {
    private const val TIMEOUT_MS = 20_000

    fun form(vararg fields: Pair<String, String>): String = fields.joinToString("&") { (key, value) ->
        URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8")
    }

    /**
     * The status and JSON answer of a request; the answer is null when it is not JSON (a 204 has no body). Blocking,
     * and throws when there is no connection.
     */
    fun request(
        method: String,
        url: String,
        body: String? = null,
        contentType: String? = null,
        token: String? = null
    ): Pair<Int, JSONObject?> {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = method
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag())
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.doOutput = true
                contentType?.let { connection.setRequestProperty("Content-Type", it) }
                connection.outputStream.use { it.write(body.toByteArray()) }
            }

            val status = connection.responseCode
            val failed = status >= HttpURLConnection.HTTP_BAD_REQUEST
            val stream = if (failed) connection.errorStream else connection.inputStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            status to runCatching { JSONObject(text) }.getOrNull()
        } finally {
            connection.disconnect()
        }
    }
}

/** A string of a JSON object, or null when it is missing, null or blank. */
internal fun JSONObject.stringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

/**
 * The tokens Imagina's identity server gives: the access token (15 minutes) and the refresh token (7 days, new on every
 * refresh), with the moment the access token stops working.
 */
internal data class ImaginaTokens(val accessToken: String, val accessExpiresAt: Long, val refreshToken: String?) {

    fun toJson(): JSONObject = JSONObject()
        .put("accessToken", accessToken)
        .put("accessExpiresAt", accessExpiresAt)
        .put("refreshToken", refreshToken ?: JSONObject.NULL)

    companion object {
        private const val DEFAULT_LIFETIME_SECONDS = 900L

        /** From the answer of the token endpoint; a refresh that gives no new refresh token keeps the one it used. */
        fun parse(answer: JSONObject?, previousRefreshToken: String? = null): ImaginaTokens? {
            val access = answer?.stringOrNull("access_token") ?: return null
            val lifetime = answer.optLong("expires_in").takeIf { it > 0 } ?: DEFAULT_LIFETIME_SECONDS

            return ImaginaTokens(
                accessToken = access,
                accessExpiresAt = System.currentTimeMillis() + lifetime * 1000,
                refreshToken = answer.stringOrNull("refresh_token") ?: previousRefreshToken
            )
        }

        fun fromJson(json: JSONObject): ImaginaTokens? {
            val access = json.stringOrNull("accessToken") ?: return null

            return ImaginaTokens(access, json.optLong("accessExpiresAt"), json.stringOrNull("refreshToken"))
        }
    }
}
