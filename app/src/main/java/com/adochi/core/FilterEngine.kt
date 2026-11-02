package com.adochi.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Verdict { ALLOWED, BLOCKED, THREAT }

/** The compiled lists the shield and the browser check every domain against. */
class CompiledLists(
    val ads: DomainSet,
    val threats: DomainSet,
    val exceptions: DomainSet,
) {
    companion object {
        val EMPTY = CompiledLists(DomainSet.EMPTY, DomainSet.EMPTY, DomainSet.EMPTY)
    }
}

data class FilterTotals(val ads: Int = 0, val threats: Int = 0, val loaded: Boolean = false) {
    val total: Int get() = ads + threats
}

object FilterEngine {

    @Volatile private var lists = CompiledLists.EMPTY
    @Volatile private var userAllow: Set<String> = emptySet()
    @Volatile private var userBlock: Set<String> = emptySet()

    private val _totals = MutableStateFlow(FilterTotals())
    val totals: StateFlow<FilterTotals> = _totals.asStateFlow()

    fun setLists(compiled: CompiledLists) {
        lists = compiled
        _totals.value = FilterTotals(compiled.ads.size, compiled.threats.size, loaded = true)
    }

    fun setUserRules(allow: Set<String>, block: Set<String>) {
        userAllow = allow
        userBlock = block
    }

    /** Order matters: the user's allowlist wins, then threats, then the user's blocks, then list exceptions. */
    fun check(rawDomain: String): Verdict {
        val domain = rawDomain.trimEnd('.').lowercase()
        if (domain.isEmpty()) return Verdict.ALLOWED
        val current = lists
        return when {
            userAllow.coversDomain(domain) -> Verdict.ALLOWED
            current.threats.matches(domain) -> Verdict.THREAT
            userBlock.coversDomain(domain) -> Verdict.BLOCKED
            current.exceptions.matches(domain) -> Verdict.ALLOWED
            current.ads.matches(domain) -> Verdict.BLOCKED
            else -> Verdict.ALLOWED
        }
    }
}
