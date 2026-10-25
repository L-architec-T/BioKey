package org.pcbu.libreclient.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pcbu.libreclient.R
import org.pcbu.libreclient.ui.util.toUserMessage
import com.kyant.backdrop.backdrops.layerBackdrop
import org.pcbu.libreclient.ui.appearance.GlassAlertDialog
import org.pcbu.libreclient.ui.components.GlassFab
import org.pcbu.libreclient.ui.components.PillButton
import org.pcbu.libreclient.ui.components.SubScreen
import org.pcbu.libreclient.ui.components.rememberGlassFabBackdrop
import org.pcbu.libreclient.ui.theme.Accent
import org.pcbu.libreclient.ui.theme.GlassCard
import org.pcbu.libreclient.ui.theme.Red
import org.pcbu.libreclient.ui.theme.TextPrimary
import org.pcbu.libreclient.ui.theme.TextSecondary
import org.pcbu.libreclient.ui.theme.verticalEdgeFade
import org.pcbu.libreclient.pairing.PairingRepository
import org.pcbu.libreclient.protocol.model.MacroDto
import org.pcbu.libreclient.protocol.model.MacroKeys
import org.pcbu.libreclient.protocol.model.MacroStepDto
import org.pcbu.libreclient.protocol.model.MacroStepTypes
import org.pcbu.libreclient.protocol.model.PcbuJson
import org.pcbu.libreclient.unlock.LockBroadcastSender

private const val DEFAULT_TIMEOUT_MS = 1000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroEditorScreen(
    pairingRepository: PairingRepository,
    deviceId: String,
    onBack: () -> Unit,
) {
    val device by pairingRepository.observeDevice(deviceId).collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var steps by remember { mutableStateOf<List<MacroStepDto>>(emptyList()) }
    var seeded by remember { mutableStateOf(false) }
    LaunchedEffect(device) {
        val currentDevice = device ?: return@LaunchedEffect
        if (seeded) return@LaunchedEffect
        steps = currentDevice.macroStepsJson
            ?.let { json -> runCatching { PcbuJson.decodeFromString(MacroDto.serializer(), json).steps }.getOrNull() }
            ?: emptyList()
        seeded = true
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    val saveErrorTemplate = stringResource(R.string.macro_editor_save_local_only)
    val saveSentMessage = stringResource(R.string.macro_editor_save_sent)
    val saveNoKeyMessage = stringResource(R.string.macro_editor_save_no_key)
    val fabBackdrop = rememberGlassFabBackdrop()

    SubScreen(title = stringResource(R.string.macro_editor_title), onBack = onBack, fab = {
        GlassFab(
            onClick = { showAddDialog = true },
            icon = Icons.Filled.Add,
            contentDescription = stringResource(R.string.macro_editor_add_step),
            backdrop = fabBackdrop,
        )
    }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (steps.isEmpty()) {
                Text(
                    stringResource(R.string.macro_editor_empty),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).layerBackdrop(fabBackdrop).verticalEdgeFade(),
                    // vertical ≥ verticalEdgeFade (14dp): first/last step never masked at rest.
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(steps.indices.toList()) { index ->
                        MacroStepRow(
                            step = steps[index],
                            canMoveUp = index > 0,
                            canMoveDown = index < steps.lastIndex,
                            onMoveUp = { steps = steps.toMutableList().apply { add(index - 1, removeAt(index)) } },
                            onMoveDown = { steps = steps.toMutableList().apply { add(index + 1, removeAt(index)) } },
                            onDelete = { steps = steps.toMutableList().apply { removeAt(index) } },
                        )
                    }
                }
            }

            PillButton(
                onClick = {
                    val json = PcbuJson.encodeToString(MacroDto.serializer(), MacroDto(steps = steps))
                    scope.launch {
                        pairingRepository.updateMacroSteps(deviceId, json.takeIf { steps.isNotEmpty() })
                        val encKey = pairingRepository.readEncKey(deviceId)
                        status = if (encKey == null) {
                            saveNoKeyMessage
                        } else {
                            try {
                                withContext(Dispatchers.IO) { LockBroadcastSender.sendMacro(context, deviceId, encKey, json) }
                                saveSentMessage
                            } catch (e: Exception) {
                                saveErrorTemplate.format(e.toUserMessage())
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                text = stringResource(R.string.macro_editor_save),
            )

            if (status.isNotEmpty()) {
                Text(status, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }

            // Scaffold's content padding doesn't account for the floating action button, which
            // floats bottom-end over this full-width Save button — reserve its footprint
            // (56dp FAB + 16dp margin) so the two don't overlap.
            Spacer(modifier = Modifier.height(72.dp))
        }
    }

    if (showAddDialog) {
        AddMacroStepDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { step ->
                steps = steps + step
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun MacroStepRow(
    step: MacroStepDto,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    val summary = when (step.type) {
        MacroStepTypes.KEY_COMBO -> stringResource(R.string.macro_editor_step_summary_key_combo, step.keys.orEmpty().joinToString(" + "))
        MacroStepTypes.TIMEOUT -> stringResource(R.string.macro_editor_step_summary_timeout, step.ms ?: 0)
        MacroStepTypes.TYPE_TEXT -> stringResource(R.string.macro_editor_step_summary_type_text, step.text.orEmpty())
        else -> step.type
    }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(summary, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.weight(1f))
            Row {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.macro_editor_move_up))
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = stringResource(R.string.macro_editor_move_down))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.macro_editor_delete_step), tint = Red)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMacroStepDialog(onDismiss: () -> Unit, onAdd: (MacroStepDto) -> Unit) {
    var type by remember { mutableStateOf(MacroStepTypes.KEY_COMBO) }
    var selectedKeys by remember { mutableStateOf(emptySet<String>()) }
    var timeoutMs by remember { mutableStateOf(DEFAULT_TIMEOUT_MS.toString()) }
    var typeText by remember { mutableStateOf("") }

    val typeOptions = listOf(
        MacroStepTypes.KEY_COMBO to stringResource(R.string.macro_editor_step_type_key_combo),
        MacroStepTypes.TIMEOUT to stringResource(R.string.macro_editor_step_type_timeout),
        MacroStepTypes.TYPE_TEXT to stringResource(R.string.macro_editor_step_type_type_text),
    )

    val canAdd = when (type) {
        MacroStepTypes.KEY_COMBO -> selectedKeys.isNotEmpty()
        MacroStepTypes.TIMEOUT -> timeoutMs.toIntOrNull() != null
        MacroStepTypes.TYPE_TEXT -> typeText.isNotEmpty() && typeText.all { it.code in 0x20..0x7E }
        else -> false
    }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.macro_editor_add_step)) },
        text = {
            Column {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    typeOptions.forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = type == value,
                            onClick = { type = value },
                            shape = SegmentedButtonDefaults.itemShape(index, typeOptions.size),
                        ) { Text(label) }
                    }
                }
                when (type) {
                    MacroStepTypes.KEY_COMBO -> {
                        Text(
                            stringResource(R.string.macro_editor_keys_label),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        KeyChipRow(MacroKeys.MODIFIERS, selectedKeys) { key, on ->
                            selectedKeys = if (on) selectedKeys + key else selectedKeys - key
                        }
                        KeyChipRow(MacroKeys.SPECIAL + MacroKeys.NAVIGATION, selectedKeys) { key, on ->
                            selectedKeys = if (on) selectedKeys + key else selectedKeys - key
                        }
                        KeyChipRow(MacroKeys.LETTERS, selectedKeys) { key, on ->
                            selectedKeys = if (on) selectedKeys + key else selectedKeys - key
                        }
                        KeyChipRow(MacroKeys.DIGITS + MacroKeys.FUNCTION_KEYS, selectedKeys) { key, on ->
                            selectedKeys = if (on) selectedKeys + key else selectedKeys - key
                        }
                    }
                    MacroStepTypes.TIMEOUT -> {
                        TextField(
                            value = timeoutMs,
                            onValueChange = { timeoutMs = it },
                            label = { Text(stringResource(R.string.macro_editor_timeout_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        )
                    }
                    MacroStepTypes.TYPE_TEXT -> {
                        TextField(
                            value = typeText,
                            onValueChange = { typeText = it },
                            label = { Text(stringResource(R.string.macro_editor_text_label)) },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canAdd,
                onClick = {
                    val step = when (type) {
                        MacroStepTypes.KEY_COMBO -> MacroStepDto(type = type, keys = selectedKeys.toList())
                        MacroStepTypes.TIMEOUT -> MacroStepDto(type = type, ms = timeoutMs.toIntOrNull())
                        else -> MacroStepDto(type = type, text = typeText)
                    }
                    onAdd(step)
                },
            ) { Text(stringResource(R.string.macro_editor_dialog_add), color = if (canAdd) Accent else TextSecondary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = TextSecondary) }
        },
    )
}

@Composable
private fun KeyChipRow(keys: List<String>, selected: Set<String>, onToggle: (String, Boolean) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        items(keys) { key ->
            FilterChip(
                selected = key in selected,
                onClick = { onToggle(key, key !in selected) },
                label = { Text(key) },
            )
        }
    }
}
