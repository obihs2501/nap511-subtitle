package github.zerorooot.nap511.util

import org.junit.Assert.*
import org.junit.Test

class DownloadUtilTest {
    @Test fun retainsExtensionAndDoesNotOverwriteDuplicateNames() {
        assertEquals("字幕-a1.ass", DownloadUtil.destinationName("字幕.ass", "a1"))
        assertNotEquals(DownloadUtil.destinationName("movie.mkv", "a1"), DownloadUtil.destinationName("movie.mkv", "a2"))
    }
    @Test fun stripsPathSeparatorsAndControlCharacters() {
        val name = DownloadUtil.destinationName("../../folder\\bad:\nname?.mp4", "a1")
        assertFalse(name.startsWith('.'))
        assertFalse(name.any { it in "\\/:*?\"<>|" || it.code < 32 })
        assertTrue(name.endsWith("-a1.mp4"))
    }
    @Test fun emptyOrDotNamesHaveASafeFallback() {
        for (name in listOf("", " ", ".", "..")) assertEquals("download-a1", DownloadUtil.destinationName(name, "a1"))
    }
    @Test fun limitsUtf8ByteLengthWithoutBreakingUnicode() {
        val name = DownloadUtil.destinationName("中字😀".repeat(100) + ".mkv", "abcd".repeat(8))
        assertTrue(name.toByteArray(Charsets.UTF_8).size < 255)
        assertTrue(name.endsWith(".mkv"))
        assertFalse(name.contains('\uFFFD'))
    }
    @Test fun oversizedExtensionsCannotBypassFilenameLimit() {
        val name = DownloadUtil.destinationName("file." + "a".repeat(500), "1")
        assertTrue(name.toByteArray(Charsets.UTF_8).size < 255)
        assertTrue(name.endsWith("-1"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsEmptyUniqueSuffix() {
        DownloadUtil.destinationName("file.mp4", "../")
    }
    @Test fun handlesUnknownAndInvalidProgress() {
        assertNull(DownloadUtil.progress(1, -1))
        assertNull(DownloadUtil.progress(1, 0))
        assertEquals(0f, DownloadUtil.progress(-1, 100))
        assertEquals(1f, DownloadUtil.progress(200, 100))
        assertEquals(0.5f, DownloadUtil.progress(50, 100))
    }
    @Test fun formatsLargeSizesWithoutOverflow() {
        assertEquals("0 B", DownloadUtil.size(0))
        assertEquals("1.0 KB", DownloadUtil.size(1024))
        assertEquals("1.0 GB", DownloadUtil.size(1024L * 1024 * 1024))
        assertTrue(DownloadUtil.size(Long.MAX_VALUE).endsWith("EB"))
        assertEquals(1f, DownloadUtil.progress(Long.MAX_VALUE, Long.MAX_VALUE))
    }
}
