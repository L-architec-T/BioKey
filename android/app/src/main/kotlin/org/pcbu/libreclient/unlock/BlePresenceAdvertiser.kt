package org.pcbu.libreclient.unlock

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Broadcasts the [BlePresenceBeacon] as one long-lived, non-connectable legacy advertising
 * set whose data is swapped in place (no restart per slot). Bluetooth turned off kills the
 * set silently, so [advertise] restarts it once the adapter is back.
 */
class BlePresenceAdvertiser(context: Context) {
    private val appContext = context.applicationContext
    @Volatile private var set: AdvertisingSet? = null
    @Volatile private var starting = false

    private val callback = object : AdvertisingSetCallback() {
        override fun onAdvertisingSetStarted(advertisingSet: AdvertisingSet?, txPower: Int, status: Int) {
            starting = false
            set = if (status == ADVERTISE_SUCCESS) advertisingSet else null
        }

        override fun onAdvertisingSetStopped(advertisingSet: AdvertisingSet?) {
            set = null
        }
    }

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // hasPermission() checked first
    fun advertise(payload: ByteArray) {
        if (!hasPermission()) return
        val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
        val advertiser = adapter?.takeIf { it.isEnabled }?.bluetoothLeAdvertiser
        if (advertiser == null) {
            set = null
            starting = false
            return
        }
        val data = AdvertiseData.Builder().addManufacturerData(BlePresenceBeacon.COMPANY_ID, payload).build()
        val current = set
        try {
            if (current != null) {
                current.setAdvertisingData(data)
            } else if (!starting) {
                starting = true
                val params = AdvertisingSetParameters.Builder()
                    .setLegacyMode(true)
                    .setConnectable(false)
                    .setScannable(false)
                    .setInterval(AdvertisingSetParameters.INTERVAL_MEDIUM)
                    .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MEDIUM)
                    .build()
                advertiser.startAdvertisingSet(params, data, null, null, null, callback)
            }
        } catch (e: Exception) {
            set = null
            starting = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!hasPermission()) return
        runCatching {
            appContext.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeAdvertiser?.stopAdvertisingSet(callback)
        }
        set = null
        starting = false
    }
}
