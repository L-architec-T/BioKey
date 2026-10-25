package org.pcbu.libreclient.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Android Keystore has no meaningful simulation under Robolectric (it
 * requires actual Keymaster/HAL support), so these run as instrumented
 * tests against a real device or emulator — `./gradlew :app:connectedAndroidTest`.
 * See SecretsStore's KDoc for the Phase 2 default (no biometric gate).
 */
@RunWith(AndroidJUnit4::class)
class SecretsStoreInstrumentedTest {

    private val store = SecretsStore(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test
    fun storesAndReadsBackEncKeyAndPasswordKeyIndependentlyPerDevice() {
        store.storeEncKey("dev-1", "enc-key-value")
        store.storePasswordKey("dev-1", "password-key-value")

        assertEquals("enc-key-value", store.readEncKey("dev-1"))
        assertEquals("password-key-value", store.readPasswordKey("dev-1"))

        store.deleteDevice("dev-1")
    }

    @Test
    fun unknownDeviceReturnsNullRatherThanThrowing() {
        assertNull(store.readEncKey("never-paired"))
        assertNull(store.readPasswordKey("never-paired"))
    }

    @Test
    fun secretsForDifferentDevicesDoNotCollide() {
        store.storeEncKey("dev-a", "key-a")
        store.storeEncKey("dev-b", "key-b")

        assertEquals("key-a", store.readEncKey("dev-a"))
        assertEquals("key-b", store.readEncKey("dev-b"))

        store.deleteDevice("dev-a")
        store.deleteDevice("dev-b")
    }
}
