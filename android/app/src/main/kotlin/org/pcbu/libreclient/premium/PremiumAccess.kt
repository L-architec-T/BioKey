package org.pcbu.libreclient.premium

import android.app.Activity
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.pcbu.libreclient.BuildConfig

private val Context.premiumDataStore by preferencesDataStore(name = "pcbu_premium")

/**
 * BioKey Premium (one-time purchase, 2,99 € for life): several paired PCs, the proximity lock
 * (Wi-Fi and Bluetooth) and post-unlock macros. Always checked on the phone, never on the PC:
 * the desktop side is GPL, anyone can rebuild it without a check.
 *
 * The backend comes from BuildConfig.PREMIUM_MODE: [FakePremiumAccess] simulates the purchase
 * until the Play Console product exists, [UnlockedPremiumAccess] keeps the GitHub build fully
 * free. A Play Billing backend will slot in as a third implementation.
 */
interface PremiumAccess {
    val isPremium: Flow<Boolean>

    /** True when the backend has a purchase to offer (false: everything is already unlocked). */
    val canPurchase: Boolean

    /** True for a simulated backend, which also gets a reset switch in the settings. */
    val isSimulated: Boolean

    suspend fun purchase(activity: Activity): PurchaseResult

    /** Simulated backends only: drops the purchase so the free limits can be tried again. */
    suspend fun setSimulatedPremium(enabled: Boolean) {}

    companion object {
        const val PRICE_LABEL = "2,99 €"
        const val FREE_DEVICE_LIMIT = 1

        @Volatile private var instance: PremiumAccess? = null

        fun get(context: Context): PremiumAccess = instance ?: synchronized(this) {
            instance ?: when (BuildConfig.PREMIUM_MODE) {
                "fake" -> FakePremiumAccess(context.applicationContext)
                else -> UnlockedPremiumAccess
            }.also { instance = it }
        }
    }
}

sealed class PurchaseResult {
    data object Purchased : PurchaseResult()
    data object Cancelled : PurchaseResult()
    data class Failed(val message: String) : PurchaseResult()
}

/** Stores the "purchase" locally, so the whole flow (limits, dialog, unlock) can be tested
 * before Google Play Billing is wired in. */
private class FakePremiumAccess(context: Context) : PremiumAccess {
    private val dataStore = context.premiumDataStore

    override val isPremium: Flow<Boolean> = dataStore.data.map { it[KEY_SIMULATED_PREMIUM] ?: false }
    override val canPurchase = true
    override val isSimulated = true

    override suspend fun purchase(activity: Activity): PurchaseResult {
        setSimulatedPremium(true)
        return PurchaseResult.Purchased
    }

    override suspend fun setSimulatedPremium(enabled: Boolean) {
        dataStore.edit { it[KEY_SIMULATED_PREMIUM] = enabled }
    }

    companion object {
        private val KEY_SIMULATED_PREMIUM = booleanPreferencesKey("simulated_premium")
    }
}

private object UnlockedPremiumAccess : PremiumAccess {
    override val isPremium: Flow<Boolean> = flowOf(true)
    override val canPurchase = false
    override val isSimulated = false
    override suspend fun purchase(activity: Activity): PurchaseResult = PurchaseResult.Purchased
}
