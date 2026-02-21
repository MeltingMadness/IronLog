package com.ironlog.app.data.incident

object IncidentReportSanitizer {
    private val idPattern = Regex("(sessionId|exerciseId|planId)=\\d+")
    private val keyedEmailPattern = Regex("([A-Za-z0-9_]+)=([A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+)")
    private val emailPattern = Regex("[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+")

    fun sanitizeText(input: String): String {
        val redactedIds = input.replace(idPattern) { matchResult ->
            val key = matchResult.value.substringBefore('=')
            "$key=<redacted>"
        }

        val redactedKeyedEmails = redactedIds.replace(keyedEmailPattern) { matchResult ->
            val key = matchResult.groupValues[1]
            "$key=<redacted-email>"
        }

        return emailPattern.replace(redactedKeyedEmails, "<redacted-email>")
    }
}
