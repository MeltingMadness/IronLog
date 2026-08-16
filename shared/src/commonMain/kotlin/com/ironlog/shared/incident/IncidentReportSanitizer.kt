package com.ironlog.shared.incident

object IncidentReportSanitizer {
    private val idPattern = Regex("(sessionId|exerciseId|planId)=\\d+")
    private val wordNumberIdPattern = Regex(
        "\\b(Training plan|Workout set|Plan exercise|Workout session|Meta plan|Personal record)\\s+\\d+\\b"
    )
    private val keyedEmailPattern = Regex("([A-Za-z0-9_]+)=([A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+)")
    private val emailPattern = Regex("[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+")

    fun sanitizeText(input: String): String {
        val redactedIds = input.replace(idPattern) { matchResult ->
            val key = matchResult.value.substringBefore('=')
            "$key=<redacted>"
        }

        val redactedWordNumbers = redactedIds.replace(wordNumberIdPattern) { matchResult ->
            "${matchResult.value.substringBeforeLast(' ')} <redacted>"
        }

        val redactedKeyedEmails = redactedWordNumbers.replace(keyedEmailPattern) { matchResult ->
            val key = matchResult.groupValues[1]
            "$key=<redacted-email>"
        }

        return emailPattern.replace(redactedKeyedEmails, "<redacted-email>")
    }
}
