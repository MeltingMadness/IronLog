package com.ironlog.app.data.local.entity

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Zentraler Konverter für Epoch-Millis ↔ LocalDateTime.
 *
 * T-02: Eliminiert doppelten Converter-Code in Entities.
 * T-03: Nutzt System-Zeitzone statt hartcodiert UTC.
 */
object EpochConverter {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun toLocalDateTime(epochMillis: Long): LocalDateTime =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDateTime()

    fun toLong(dt: LocalDateTime): Long =
        dt.atZone(zone).toInstant().toEpochMilli()
}
