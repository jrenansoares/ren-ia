package me.rerere.workspace

import org.junit.Assert.*
import org.junit.Test

class ProgrammingInstallProgressTest {
    @Test
    fun databasePercentIsNeverTreatedAsInstallationProgress() {
        val tracker = ProgrammingInstallTracker()
        val progress = tracker.accept("(Reading database ... 100%\r", false, 10)
        assertNull(progress.fraction)
        assertTrue(progress.log.contains("100%"))
    }

    @Test
    fun aptRecordsCanBeSplitAcrossChunksAndStreams() {
        val tracker = ProgrammingInstallTracker()
        tracker.accept("PROJETO_IA_SETUP:PACKAGES\ndlsta", false, 10)
        tracker.accept("debconf: delaying package configuration\n", true, 11)
        val downloading = tracker.accept("tus:1:12.5000:Retrieving file 1 of 3\n", false, 12)
        assertEquals(ProgrammingInstallStage.DOWNLOAD, downloading.stage)
        assertEquals(0.125f, downloading.fraction!!, 0.0001f)
        val installing = tracker.accept("pmstatus:libpython:arm64:33.3333:Unpacking libpython (arm64)\n", false, 13)
        assertEquals(ProgrammingInstallStage.INSTALL, installing.stage)
        assertEquals("libpython:arm64", installing.packageName)
        assertEquals(0.333333f, installing.fraction!!, 0.0001f)
    }

    @Test
    fun repeatedDownloadPulseDoesNotPretendProgressContinues() {
        val tracker = ProgrammingInstallTracker()
        tracker.accept("PROJETO_IA_SETUP:PACKAGES\ndlstatus:1:10.0000:Retrieving file 1 of 3\n", false, 100)
        val stalled = tracker.accept("dlstatus:1:10.0000:Retrieving file 1 of 3 (2m remaining)\n", false, 200_000)
        assertEquals(100L, stalled.lastProgressMillis)
    }

    @Test
    fun indexEstimatesAndMalformedPercentagesDoNotBecomeMeasuredBars() {
        val tracker = ProgrammingInstallTracker()
        val index = tracker.accept("PROJETO_IA_SETUP:INDEX\ndlstatus:1:37.25:Retrieving lists\n", false, 10)
        assertNull(index.fraction)
        val bad = tracker.accept("PROJETO_IA_SETUP:PACKAGES\npmstatus:git:150.0:bad\n", false, 20)
        assertNull(bad.fraction)
    }

    @Test
    fun indexActivityAdvancesEvenThoughItsUnknownTotalHasNoBar() {
        val tracker = ProgrammingInstallTracker()
        tracker.accept("PROJETO_IA_SETUP:INDEX\ndlstatus:1:10:Retrieving file 1\n", false, 10)
        val next = tracker.accept("dlstatus:2:10:Retrieving file 2\n", false, 20)
        assertNull(next.fraction)
        assertEquals(20L, next.lastProgressMillis)
    }

    @Test
    fun phaseChangesResetPercentAndDoNotDeclareOverallSuccess() {
        val tracker = ProgrammingInstallTracker()
        tracker.accept("PROJETO_IA_SETUP:PACKAGES\ndlstatus:3:100:Downloaded\n", false, 10)
        val installing = tracker.accept("pmstatus:dpkg-exec:0:Running dpkg\n", false, 20)
        assertEquals(0f, installing.fraction!!, 0f)
        val done = tracker.accept("PROJETO_IA_SETUP:DONE\n", false, 30)
        assertEquals(ProgrammingInstallStage.DONE, done.stage)
        assertNull(done.fraction)
    }

    @Test
    fun boundedLogRetainsFinalErrorIncludingAnUnterminatedLine() {
        val tracker = ProgrammingInstallTracker()
        repeat(200) { tracker.accept("Selecting a package " + "x".repeat(100) + "\n", false, it.toLong()) }
        tracker.accept("E: No space left on device", true, 500)
        val final = tracker.finish(501)
        assertTrue(final.log.endsWith("E: No space left on device\n"))
        assertTrue(final.log.length <= ProgrammingInstallTracker.MAX_LOG_CHARS)
    }
}
