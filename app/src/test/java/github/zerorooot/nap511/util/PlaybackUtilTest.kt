package github.zerorooot.nap511.util

import github.zerorooot.nap511.bean.FileBean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackUtilTest {
    @Test
    fun sortsEpisodeNumbersNaturally() {
        val names = listOf("Show.S01E10.mkv", "Show.S02E01.mkv", "Show.S01E2.mkv", "Show.S01E1.mkv")
        assertEquals(listOf("Show.S01E1.mkv", "Show.S01E2.mkv", "Show.S01E10.mkv", "Show.S02E01.mkv"),
            names.sortedWith(PlaybackUtil::compareNames))
    }

    @Test
    fun handlesLargeNumbersAndLeadingZerosWithoutOverflow() {
        assertTrue(PlaybackUtil.compareNames("episode99999999999999999999", "episode100000000000000000000") < 0)
        assertTrue(PlaybackUtil.compareNames("episode02", "episode10") < 0)
        assertEquals(0, PlaybackUtil.compareNames("episode02", "episode02"))
        assertTrue(PlaybackUtil.compareNames("episode", "episode1") < 0)
    }

    @Test
    fun onlyOffersPlayableFilesAndDeduplicatesByPickCode() {
        val files = listOf(
            FileBean(fileId = "2", pickCode = "pc2", name = "E2.MKV"),
            FileBean(fileId = "1", pickCode = "pc1", name = "E1.mp4"),
            FileBean(fileId = "3", pickCode = "pc3", name = "E3.unknown", isVideo = 1),
            FileBean(fileId = "4", pickCode = "pc1", name = "duplicate.mp4"),
            FileBean(fileId = "5", pickCode = "subtitle", name = "E1.ass"),
            FileBean(fileId = "6", name = "no-link.mp4"),
            FileBean(categoryId = "folder", pickCode = "folder", name = "folder.mp4")
        )
        assertEquals(listOf("pc1", "pc2", "pc3"), PlaybackUtil.episodes(files).map { it.pickCode })
    }

    @Test
    fun doesNotWrapAtTheFirstOrLastEpisode() {
        assertNull(PlaybackUtil.adjacentIndex(3, 0, -1))
        assertNull(PlaybackUtil.adjacentIndex(3, 2, 1))
        assertEquals(1, PlaybackUtil.adjacentIndex(3, 0, 1))
        assertEquals(1, PlaybackUtil.adjacentIndex(3, 2, -1))
    }

    @Test
    fun doesNotGuessANextEpisodeIfCurrentVideoIsMissing() {
        assertNull(PlaybackUtil.adjacentIndex(3, -1, 1))
        assertNull(PlaybackUtil.adjacentIndex(0, 0, 1))
        assertNull(PlaybackUtil.adjacentIndex(3, 0, 2))
    }

    @Test
    fun clampsQuickSeekAtBothBoundaries() {
        assertEquals(0L, PlaybackUtil.seekPosition(2000, -15_000, 60_000))
        assertEquals(60_000L, PlaybackUtil.seekPosition(55_000, 15_000, 60_000))
        assertEquals(25_000L, PlaybackUtil.seekPosition(10_000, 15_000, 60_000))
    }

    @Test
    fun quickSeekCannotOverflowOrSeekIntoAnUnknownDuration() {
        assertEquals(60_000L, PlaybackUtil.seekPosition(10_000, Long.MAX_VALUE, 60_000))
        assertEquals(0L, PlaybackUtil.seekPosition(10_000, Long.MIN_VALUE, 60_000))
        assertEquals(0L, PlaybackUtil.seekPosition(10_000, 15_000, 0))
        assertEquals(15_000L, PlaybackUtil.seekPosition(-1, 15_000, 60_000))
    }

    @Test
    fun completedOrInvalidHistoryStartsFromTheBeginning() {
        assertEquals(0L, PlaybackUtil.resumePosition(99_000, 100_000))
        assertEquals(0L, PlaybackUtil.resumePosition(150_000, 100_000))
        assertEquals(0L, PlaybackUtil.resumePosition(-1, 100_000))
        assertEquals(0L, PlaybackUtil.resumePosition(5000, 0))
        assertEquals(30_000L, PlaybackUtil.resumePosition(30_000, 100_000))
    }

    @Test
    fun formatsPlayerLabels() {
        assertEquals("1×", PlaybackUtil.speedLabel(1f))
        assertEquals("1.25×", PlaybackUtil.speedLabel(1.25f))
        assertEquals("2.5×", PlaybackUtil.speedLabel(2.5f))
        assertEquals("01:05", PlaybackUtil.timerLabel(65))
        assertEquals("00:00", PlaybackUtil.timerLabel(-1))
        assertFalse(PlaybackUtil.SPEEDS.contains(0f))
    }
}
