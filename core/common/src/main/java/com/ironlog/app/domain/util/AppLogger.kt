package com.ironlog.app.domain.util

import android.util.Log

object AppLogger {

    private const val MAX_LOG_LENGTH = 4000

    fun d(tag: String, message: String) {
        if (Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, sanitize(message).take(MAX_LOG_LENGTH))
        }
    }

    fun i(tag: String, message: String) {
        Log.i(tag, sanitize(message).take(MAX_LOG_LENGTH))
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, sanitize(message).take(MAX_LOG_LENGTH), throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, sanitize(message).take(MAX_LOG_LENGTH), throwable)
    }

    private fun sanitize(message: String): String {
        val idPattern = Regex("(sessionId|exerciseId|planId)=\\d+")
        return message.replace(idPattern) { matchResult ->
            val key = matchResult.value.substringBefore('=')
            "$key=<redacted>"
        }
    }
}
