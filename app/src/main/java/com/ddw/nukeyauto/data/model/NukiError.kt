package com.ddw.nukeyauto.data.model

import android.content.Context
import com.ddw.nukeyauto.R

/**
 * Typed error representation for Nuki lock operations.
 * UI layers map these to localized strings via string resources.
 */
sealed class NukiError {
    data object NoCredentials : NukiError()
    data object InvalidSmartLockId : NukiError()
    data class UnknownLockState(val stateCode: Int) : NukiError()
    data object ConnectionFailed : NukiError()
    data class HttpError(val code: Int) : NukiError()
    data class ApiException(val message: String) : NukiError()

    /**
     * Whether this error is a configuration issue fixable via settings.
     * True for missing/invalid credentials and auth-related HTTP errors.
     */
    fun isConfigError(): Boolean {
        return when (this) {
            is NoCredentials -> true
            is InvalidSmartLockId -> true
            is HttpError -> code in listOf(401, 403, 404)
            else -> false
        }
    }

    /**
     * Map this error to a localized user-facing string.
     *
     * @param context any valid Context for string resource resolution
     * @return localized error description
     */
    fun toLocalizedString(context: Context): String {
        return when (this) {
            is NoCredentials -> context.getString(R.string.error_no_credentials)
            is InvalidSmartLockId -> context.getString(R.string.error_invalid_id)
            is UnknownLockState -> context.getString(R.string.error_unknown_state, stateCode)
            is ConnectionFailed -> context.getString(R.string.error_connection_failed)
            is HttpError -> context.getString(httpErrorStringRes(code))
            is ApiException -> context.getString(R.string.error_generic)
        }
    }

    companion object {
        /**
         * Map an HTTP status code to the appropriate string resource ID.
         */
        fun httpErrorStringRes(code: Int): Int {
            return when (code) {
                401, 403 -> R.string.error_http_unauthorized
                404 -> R.string.error_http_not_found
                429 -> R.string.error_http_rate_limit
                in 500..599 -> R.string.error_http_server
                else -> R.string.error_generic
            }
        }
    }
}
