package com.ironlog.app.domain.model

/** Revision 2 counts NORMAL and FAILURE for progression; other roles stay separate. */
enum class SetType {
    NORMAL,
    WARMUP,
    DROP_SET,
    FAILURE,
    BACKOFF
}
