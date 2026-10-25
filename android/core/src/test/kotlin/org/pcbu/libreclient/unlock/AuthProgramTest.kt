package org.pcbu.libreclient.unlock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AuthProgramTest {

    @Test
    fun `sudo command line is an elevation`() {
        val program = AuthProgram.parse("sudo -S -p /tmp/fix.sh")
        assertEquals("sudo", program.elevation)
        assertEquals("sudo -S -p /tmp/fix.sh", program.commandLine)
    }

    @Test
    fun `absolute paths are matched by basename`() {
        assertEquals("polkit", AuthProgram.parse("/usr/lib/polkit-1/polkit-agent-helper-1 larchitect").elevation)
        assertEquals("pkexec", AuthProgram.parse("/usr/bin/pkexec /usr/sbin/gparted").elevation)
    }

    @Test
    fun `session login is not an elevation`() {
        val program = AuthProgram.parse("gdm-session-worker [pam/gdm-password]")
        assertNull(program.elevation)
        assertFalse(program.isElevation)
    }

    @Test
    fun `executable merely starting with su is not su`() {
        assertNull(AuthProgram.parse("sddm-helper --socket x").elevation)
        assertNull(AuthProgram.parse("sudo-like-tool").elevation)
    }

    @Test
    fun `empty and fallback values`() {
        assertEquals(AuthProgram(null, ""), AuthProgram.parse("  "))
        assertNull(AuthProgram.parse("PID 1234").elevation)
    }
}
