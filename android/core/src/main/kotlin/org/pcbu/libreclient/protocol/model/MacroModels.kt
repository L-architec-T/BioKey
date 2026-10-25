package org.pcbu.libreclient.protocol.model

import kotlinx.serialization.Serializable

/**
 * The post-unlock keyboard macro contract shared with pcbu-lockd (see PlatformHelper::PlayMacro
 * and its kKeyNameToKeycode table). Sent as the plaintext of a [LockRequest] with cmd="setMacro"
 * (so it rides the same AES-GCM-encrypted channel as every other remote command) and persisted
 * locally so [MacroDto] round-trips through Room as a single JSON column. Field names/casing are
 * load-bearing on both sides, same rule as every other type in this file's sibling Models.kt.
 */

@Serializable
data class MacroStepDto(
    val type: String,
    val keys: List<String>? = null,
    val ms: Int? = null,
    val text: String? = null,
)

@Serializable
data class MacroDto(
    val version: Int = 1,
    val steps: List<MacroStepDto> = emptyList(),
)

/** [MacroStepDto.type] values. */
object MacroStepTypes {
    const val KEY_COMBO = "KEY_COMBO"
    const val TIMEOUT = "TIMEOUT"
    const val TYPE_TEXT = "TYPE_TEXT"
}

/** Canonical key-name vocabulary for [MacroStepDto.keys] — every string here must have a
 * matching entry in pcbu-lockd's kKeyNameToKeycode table (PlatformHelper.Linux.cpp). An
 * unrecognized name causes pcbu-lockd to reject the whole macro at setMacro time rather than
 * silently drop the step, so this list is a hard contract, not just a UI suggestion. */
object MacroKeys {
    const val CTRL = "CTRL"
    const val ALT = "ALT"
    const val SHIFT = "SHIFT"
    const val WIN = "WIN"
    const val ENTER = "ENTER"
    const val TAB = "TAB"
    const val ESC = "ESC"
    const val SPACE = "SPACE"
    const val BACKSPACE = "BACKSPACE"
    const val DELETE = "DELETE"
    const val HOME = "HOME"
    const val END = "END"
    const val UP = "UP"
    const val DOWN = "DOWN"
    const val LEFT = "LEFT"
    const val RIGHT = "RIGHT"

    val MODIFIERS = listOf(CTRL, ALT, SHIFT, WIN)
    val LETTERS = ('A'..'Z').map { it.toString() }
    val DIGITS = ('0'..'9').map { it.toString() }
    val FUNCTION_KEYS = (1..12).map { "F$it" }
    val NAVIGATION = listOf(HOME, END, UP, DOWN, LEFT, RIGHT)
    val SPECIAL = listOf(ENTER, TAB, ESC, SPACE, BACKSPACE, DELETE)

    /** Every valid key name, for client-side validation before a macro is ever sent. */
    val ALL: Set<String> = (MODIFIERS + LETTERS + DIGITS + FUNCTION_KEYS + NAVIGATION + SPECIAL).toSet()
}
