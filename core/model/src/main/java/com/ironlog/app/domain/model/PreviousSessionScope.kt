package com.ironlog.app.domain.model

sealed interface PreviousSessionScope {
    data object Global : PreviousSessionScope
    data class NormalPlan(val planId: Long) : PreviousSessionScope
    data class MetaPlan(val planId: Long, val metaPlanId: Long) : PreviousSessionScope
    data class SharedPlan(val planId: Long) : PreviousSessionScope
}
