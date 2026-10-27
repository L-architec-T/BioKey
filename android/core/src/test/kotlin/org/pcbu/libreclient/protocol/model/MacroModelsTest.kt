package org.pcbu.libreclient.protocol.model

import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Round-trip + key-shape guards for the macro contract shared with pcbu-lockd's
 * HandleSetMacroCommand/kKeyNameToKeycode — see ModelsTest for why this matters. */
class MacroModelsTest {

    private fun keysOf(json: String) = PcbuJson.parseToJsonElement(json).jsonObject.keys

    @Test
    fun `MacroDto round-trips the user's example sequence`() {
        val macro = MacroDto(
            steps = listOf(
                MacroStepDto(type = MacroStepTypes.KEY_COMBO, keys = listOf(MacroKeys.CTRL, "T")),
                MacroStepDto(type = MacroStepTypes.TIMEOUT, ms = 2000),
                MacroStepDto(type = MacroStepTypes.KEY_COMBO, keys = listOf(MacroKeys.ENTER)),
                MacroStepDto(type = MacroStepTypes.TYPE_TEXT, text = "je suis un test"),
            ),
        )
        val json = PcbuJson.encodeToString(MacroDto.serializer(), macro)
        val parsed = PcbuJson.decodeFromString(MacroDto.serializer(), json)

        assertEquals(1, parsed.version)
        assertEquals(4, parsed.steps.size)
        assertEquals(listOf("CTRL", "T"), parsed.steps[0].keys)
        assertEquals(2000, parsed.steps[1].ms)
        assertEquals(listOf("ENTER"), parsed.steps[2].keys)
        assertEquals("je suis un test", parsed.steps[3].text)
    }

    @Test
    fun `MacroStepDto serializes exactly type, keys, ms, text, app, label`() {
        val json = PcbuJson.encodeToString(
            MacroStepDto.serializer(),
            MacroStepDto(type = MacroStepTypes.TIMEOUT, ms = 500),
        )
        assertEquals(setOf("type", "keys", "ms", "text", "app", "label"), keysOf(json))
    }

    @Test
    fun `MacroKeys ALL contains every key used by the example macro`() {
        assertTrue(MacroKeys.CTRL in MacroKeys.ALL)
        assertTrue("T" in MacroKeys.ALL)
        assertTrue(MacroKeys.ENTER in MacroKeys.ALL)
        assertEquals(4 + 26 + 10 + 12 + 6 + 6, MacroKeys.ALL.size)
    }

    @Test
    fun `empty macro defaults version to 1 and steps to empty`() {
        val json = """{"steps":[]}"""
        val parsed = PcbuJson.decodeFromString(MacroDto.serializer(), json)
        assertEquals(1, parsed.version)
        assertTrue(parsed.steps.isEmpty())
    }
}
