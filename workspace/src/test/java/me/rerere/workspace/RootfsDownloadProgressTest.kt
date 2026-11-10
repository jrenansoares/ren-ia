package me.rerere.workspace

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.InetSocketAddress

class RootfsDownloadProgressTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun smallDownloadsReportTheirExactFinalSize() = checkDownload(chunked = false)
    @Test fun unknownTotalStillReportsReceivedBytes() = checkDownload(chunked = true)

    private fun checkDownload(chunked: Boolean) {
        val body = ByteArray(123) { it.toByte() }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/rootfs") { exchange ->
            exchange.sendResponseHeaders(200, if (chunked) 0L else body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val output = tmp.newFile()
            val events = mutableListOf<RootfsInstallProgress>()
            RootfsInstaller(WorkspaceManager(tmp.newFolder())).download(
                "http://127.0.0.1:${server.address.port}/rootfs", output, events::add)
            assertArrayEquals(body, output.readBytes())
            assertEquals(123L, events.last().bytesRead)
            assertEquals(if (chunked) null else 123L, events.last().totalBytes)
            assertEquals(0L, events.first().bytesRead)
            assertTrue(events.all { it.stage == RootfsInstallStage.DOWNLOADING })
        } finally {
            server.stop(0)
        }
    }
}
