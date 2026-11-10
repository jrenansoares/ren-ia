package me.rerere.workspace

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class WorkspaceOutputProgressTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun outputReachesTheObserverWhileTheCommandIsStillRunning() {
        val manager = WorkspaceManager(temporaryFolder.newFolder(), shellRunner = HostShellRunner())
        manager.ensureWorkspace("stream")
        val received = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val running = executor.submit<WorkspaceCommandResult> {
                manager.executeCommand("stream",
                    "printf 'started\\n'; while [ ! -f release ]; do sleep 0.01; done; printf 'finished\\n'",
                    timeoutMillis = 5_000,
                    onOutput = { text, _ -> if (text.contains("started")) received.countDown() })
            }
            assertTrue("No live output before process completion", received.await(3, TimeUnit.SECONDS))
            assertFalse(running.isDone)
            File(manager.filesDir("stream"), "release").writeText("continue")
            assertEquals(0, running.get(3, TimeUnit.SECONDS).exitCode)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun observerStillReceivesFinalErrorsAfterRetainedOutputIsTruncated() {
        val observed = StringBuilder()
        val stream = StreamCollector(ByteArrayInputStream(("x".repeat(MAX_OUTPUT_CHARS + 4_096) + "FINAL_ERROR").toByteArray()),
            onChunk = { observed.append(it) })
        stream.join(3_000)
        assertTrue(stream.truncated)
        assertEquals(MAX_OUTPUT_CHARS, stream.text().length)
        assertTrue(observed.endsWith("FINAL_ERROR"))
    }
}
