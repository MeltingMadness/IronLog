package com.thrym.app.data.db

import androidx.room.withTransaction
import com.thrym.app.data.local.ThrymDatabase

/**
 * Seam for composite repository mutations: production runs them in one Room
 * transaction, unit tests can pass a Room-free runner.
 */
interface TransactionRunner {
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}

class RoomTransactionRunner(
    private val database: ThrymDatabase
) : TransactionRunner {
    override suspend fun <T> runInTransaction(block: suspend () -> T): T =
        database.withTransaction { block() }
}
