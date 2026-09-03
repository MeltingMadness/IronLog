package com.ironlog.app.domain.model

/**
 * Classification of a logged set.
 *
 * [NORMAL] sets are the sets that count as training evidence; only they are
 * considered by progression evaluation. [WARMUP], [DROP_SET] and [FAILURE]
 * sets are logged for the record but never count towards progression or the
 * planned set slots.
 */
enum class SetType {
    NORMAL,
    WARMUP,
    DROP_SET,
    FAILURE
}