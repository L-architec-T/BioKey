package org.pcbu.libreclient.ui.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import org.pcbu.libreclient.R
import org.pcbu.libreclient.pairing.PairingRepository
import org.pcbu.libreclient.protocol.model.MacroDto
import org.pcbu.libreclient.protocol.model.PcbuJson
import org.pcbu.libreclient.storage.PreferencesStore
import org.pcbu.libreclient.storage.db.PairedDeviceEntity
import org.pcbu.libreclient.ui.components.GlassFab
import org.pcbu.libreclient.ui.components.rememberGlassFabBackdrop
import org.pcbu.libreclient.ui.theme.Accent
import org.pcbu.libreclient.ui.theme.GlassCard
import org.pcbu.libreclient.ui.theme.TextPrimary
import org.pcbu.libreclient.ui.theme.TextSecondary
import org.pcbu.libreclient.ui.theme.verticalEdgeFade

/** "Appareils" tab of the home shell: paired PCs as glass cards, glass + button to pair a new one. */
@Composable
fun DeviceListScreen(
    pairingRepository: PairingRepository,
    preferencesStore: PreferencesStore,
    onDeviceClick: (String) -> Unit,
    onAddDeviceClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val devices by pairingRepository.observeDevices().collectAsState(initial = emptyList())
    val unlockCount by preferencesStore.unlockSuccessCount.collectAsState(initial = 0)
    val deniedCount by preferencesStore.unlockDeniedCount.collectAsState(initial = 0)
    val macroCount = remember(devices) {
        devices.count { d ->
            d.macroStepsJson?.let { json ->
                runCatching { PcbuJson.decodeFromString(MacroDto.serializer(), json).steps.isNotEmpty() }.getOrNull()
            } == true
        }
    }
    val fabBackdrop = rememberGlassFabBackdrop()

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().layerBackdrop(fabBackdrop).verticalEdgeFade(),
            // top ≥ verticalEdgeFade (14dp): the first item must sit fully clear of the fade at rest.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                StatsCard(devices.size, unlockCount, macroCount, deniedCount)
            }
            item {
                Text(
                    stringResource(R.string.devices_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            if (devices.isEmpty()) {
                item {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.devices_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            items(devices, key = PairedDeviceEntity::deviceId) { device ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onDeviceClick(device.deviceId) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(Icons.Outlined.Computer, contentDescription = null, tint = Accent)
                        Column(Modifier.weight(1f)) {
                            Text(device.deviceName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(
                                "${device.userName} · ${device.deviceOS}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextSecondary)
                    }
                }
            }
        }
        GlassFab(
            onClick = onAddDeviceClick,
            icon = Icons.Filled.Add,
            contentDescription = stringResource(R.string.devices_add),
            backdrop = fabBackdrop,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

/** Stats container (design-system §7): icon + big bold accent number + small secondary label,
 * tiles split evenly in one Row inside a GlassCard. Connexions/Refusées are counted by
 * UnlockForegroundService; Macros = paired PCs with a non-empty post-unlock macro. */
@Composable
private fun StatsCard(devices: Int, unlocks: Int, macros: Int, denied: Int) {
    GlassCard(modifier = Modifier.fillMaxWidth(), radius = 22.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatTile(Icons.Outlined.Computer, stringResource(R.string.stats_devices), devices, Modifier.weight(1f))
            StatTile(Icons.Outlined.LockOpen, stringResource(R.string.stats_unlocks), unlocks, Modifier.weight(1f))
            StatTile(Icons.Outlined.AutoFixHigh, stringResource(R.string.stats_macros), macros, Modifier.weight(1f))
            StatTile(Icons.Outlined.Block, stringResource(R.string.stats_denied), denied, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(icon: ImageVector, label: String, count: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(6.dp))
        Text("$count", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = Accent)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, textAlign = TextAlign.Center)
    }
}
