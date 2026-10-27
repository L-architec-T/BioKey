package org.pcbu.libreclient.storage

import org.pcbu.libreclient.storage.db.displayName

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.pcbu.libreclient.storage.db.PairedDeviceEntity
import org.pcbu.libreclient.storage.db.PcbuDatabase
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PairedDeviceDaoTest {

    private lateinit var db: PcbuDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PcbuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun sampleDevice(id: String = "dev-1") = PairedDeviceEntity(
        deviceId = id,
        deviceName = "debian",
        deviceOS = "Linux",
        userName = "larchitect",
        unlockServerPort = 43296,
        macAddressesCsv = "aa:bb:cc:dd:ee:ff",
        pairingMethod = "UDP",
        pairedAtEpochMs = 1_700_000_000_000,
    )

    @Test
    fun `insert then findById round-trips all fields`() = runBlocking {
        val dao = db.pairedDeviceDao()
        dao.insert(sampleDevice())

        val found = dao.findById("dev-1")
        assertEquals("debian", found?.deviceName)
        assertEquals("Linux", found?.deviceOS)
        assertEquals("larchitect", found?.userName)
        assertEquals(43296, found?.unlockServerPort)
        assertEquals("aa:bb:cc:dd:ee:ff", found?.macAddressesCsv)
    }

    @Test
    fun `insert with same deviceId replaces (does not duplicate)`() = runBlocking {
        val dao = db.pairedDeviceDao()
        dao.insert(sampleDevice())
        dao.insert(sampleDevice().copy(deviceName = "debian-renamed"))

        assertEquals(1, dao.getAll().size)
        assertEquals("debian-renamed", dao.findById("dev-1")?.deviceName)
    }

    @Test
    fun `deleteById removes the row`() = runBlocking {
        val dao = db.pairedDeviceDao()
        dao.insert(sampleDevice())
        dao.deleteById("dev-1")
        assertNull(dao.findById("dev-1"))
    }

    @Test
    fun `getAll returns multiple devices ordered by name`() = runBlocking {
        val dao = db.pairedDeviceDao()
        dao.insert(sampleDevice("dev-2").copy(deviceName = "zeta"))
        dao.insert(sampleDevice("dev-1").copy(deviceName = "alpha"))

        val all = dao.getAll()
        assertEquals(listOf("alpha", "zeta"), all.map { it.deviceName })
    }

    @Test
    fun `getAll orders by displayed name - phone-side name, else user`() = runBlocking {
        val dao = db.pairedDeviceDao()
        dao.insert(sampleDevice("dev-1").copy(deviceName = "debian", userName = "kevin"))
        dao.insert(sampleDevice("dev-2").copy(deviceName = "debian", userName = "zoe", customName = "Bureau"))
        dao.insert(sampleDevice("dev-3").copy(deviceName = "debian", userName = "anna"))

        assertEquals(listOf("anna", "Bureau", "kevin"), dao.getAll().map { it.displayName })
    }

    @Test
    fun `updateCustomName sets then clears the phone-side name`() = runBlocking {
        val dao = db.pairedDeviceDao()
        dao.insert(sampleDevice("dev-1"))
        dao.updateCustomName("dev-1", "PC bureau")
        assertEquals("PC bureau", dao.findById("dev-1")?.displayName)
        dao.updateCustomName("dev-1", null)
        assertEquals("larchitect", dao.findById("dev-1")?.displayName)
    }

    @Test
    fun `updateMacroSteps sets and clears the macro column without touching other fields`() = runBlocking {
        val dao = db.pairedDeviceDao()
        dao.insert(sampleDevice())

        dao.updateMacroSteps("dev-1", """{"version":1,"steps":[]}""")
        assertEquals("""{"version":1,"steps":[]}""", dao.findById("dev-1")?.macroStepsJson)
        assertEquals("debian", dao.findById("dev-1")?.deviceName)

        dao.updateMacroSteps("dev-1", null)
        assertNull(dao.findById("dev-1")?.macroStepsJson)
    }
}
