package org.pcbu.libreclient.storage.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Non-secret metadata for a paired PC. The two actual secrets (encKey,
 * passwordKey) are NOT stored here — they live wrapped by Android Keystore
 * in [org.pcbu.libreclient.storage.SecretsStore], referenced by [deviceId].
 */
@Entity(tableName = "paired_devices")
data class PairedDeviceEntity(
    @PrimaryKey val deviceId: String,
    val deviceName: String,
    val deviceOS: String,
    val userName: String,
    val unlockServerPort: Int,
    /** Comma-joined MAC addresses, for Wake-on-LAN. */
    val macAddressesCsv: String,
    val pairingMethod: String,
    val pairedAtEpochMs: Long,
    val lastUnlockAtEpochMs: Long? = null,
    val lastUnlockResult: String? = null,
    /** Serialized [org.pcbu.libreclient.protocol.model.MacroDto] JSON, or null if no
     * post-unlock macro is configured. Pushed to pcbu-lockd via LockBroadcastSender.sendMacro
     * on save; this column is the local copy used to repopulate the editor UI. */
    val macroStepsJson: String? = null,
    /** Name chosen on the phone ("Renommer"); null keeps the default, see [displayName]. */
    val customName: String? = null,
)

/** What the app calls this PC everywhere (lists, screens, notifications): the name chosen on
 * the phone, else the paired OS user — [PairedDeviceEntity.deviceName] is only the PC's
 * hostname at pairing time ("debian"), which says little and goes stale on a rename. */
val PairedDeviceEntity.displayName: String
    get() = customName?.takeIf { it.isNotBlank() } ?: userName.ifBlank { deviceName }
