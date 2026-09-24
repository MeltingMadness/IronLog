package com.ironlog.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row store for the readiness side channel (dated check-ins and per-set
 * intentions).
 *
 * The canonical document is `com.ironlog.shared.readinessdata.ReadinessData`,
 * serialized as JSON into [payload]. Storing it as one row matches how it is
 * exported/imported (wholesale) and keeps the Room schema independent from the
 * portable contract's field additions.
 *
 * The single-row invariant is enforced by the adapter: only [SINGLETON_ID] is
 * ever written.
 */
@Entity(tableName = "readiness_data")
data class ReadinessDataEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val payload: String
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
