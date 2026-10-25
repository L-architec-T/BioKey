package org.pcbu.libreclient.unlock

/**
 * What asked for an unlock, from [UnlockChallenge.program]: pcbu_auth sends the
 * command line of the PAM caller (its parent process), e.g. `sudo -S -p /tmp/x.sh`,
 * `/usr/lib/polkit-1/polkit-agent-helper-1 larchitect` or `gdm-session-worker [pam/gdm-password]`,
 * falling back to `PID 1234` when it can't read it.
 */
data class AuthProgram(
    /** Short label for notification titles: "sudo", "pkexec", "polkit"… null for a plain session login. */
    val elevation: String?,
    /** The raw command line, trimmed; empty when the PC didn't send one. */
    val commandLine: String,
) {
    val isElevation: Boolean get() = elevation != null

    companion object {
        private val ELEVATION_LABELS = mapOf(
            "sudo" to "sudo",
            "sudoedit" to "sudo",
            "pkexec" to "pkexec",
            "polkit-agent-helper-1" to "polkit",
            "su" to "su",
            "run0" to "run0",
            "doas" to "doas",
        )

        fun parse(program: String): AuthProgram {
            val commandLine = program.trim()
            val executable = commandLine.substringBefore(' ').substringAfterLast('/')
            return AuthProgram(ELEVATION_LABELS[executable], commandLine)
        }
    }
}
