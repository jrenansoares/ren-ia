package br.com.projetoia.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceInstallRecoveryTest {
    @Test fun anInterruptedInstallCanBeRetriedOrUsedAfterRestart() {
        assertEquals("READY", recoverWorkspaceShellStatus("INSTALLING", hasRootfs = true, installationActive = false))
        assertEquals(
            "DISABLED",
            recoverWorkspaceShellStatus("INSTALLING", hasRootfs = false, installationActive = false)
        )
    }

    @Test fun aLiveInstallIsNotMarkedReadyByTheIntegrityScan() {
        assertEquals(
            "INSTALLING",
            recoverWorkspaceShellStatus("INSTALLING", hasRootfs = true, installationActive = true)
        )
        assertEquals(
            "INSTALLING",
            recoverWorkspaceShellStatus("INSTALLING", hasRootfs = false, installationActive = true)
        )
    }

    @Test fun brokenOrDisabledProjectsAreNotSilentlyEnabled() {
        assertEquals("BROKEN", recoverWorkspaceShellStatus("BROKEN", hasRootfs = true, installationActive = false))
        assertEquals("DISABLED", recoverWorkspaceShellStatus("DISABLED", hasRootfs = true, installationActive = false))
        assertEquals("DISABLED", recoverWorkspaceShellStatus("READY", hasRootfs = false, installationActive = false))
    }
}
