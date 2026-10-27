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
    /** LAUNCH_APP: desktop file name on the PC, e.g. "org.gnome.Nautilus.desktop". */
    val app: String? = null,
    /** LAUNCH_APP: the app's display name, for the editor only (pcbu-lockd ignores it). */
    val label: String? = null,
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
    const val LAUNCH_APP = "LAUNCH_APP"
}

/** Plaintext of the "listApps" command: where the phone waits for the PC's TCP connection,
 * and a random nonce the PC echoes inside the encrypted list. */
@Serializable
data class ListAppsRequest(val port: Int, val nonce: String)

/** First packet ([org.pcbu.libreclient.protocol.PacketIds.APP_LIST_HEADER]) of the reply:
 * total size of the encrypted list and how many chunk packets follow. */
@Serializable
data class AppListHeader(val size: Int, val chunks: Int)

/** An application installed on the PC; [icon] is a base64 PNG, absent when the PC only has
 * an SVG icon for it. */
@Serializable
data class PcAppDto(val id: String, val name: String, val icon: String? = null)

@Serializable
data class AppListDto(val nonce: String, val apps: List<PcAppDto> = emptyList())

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
