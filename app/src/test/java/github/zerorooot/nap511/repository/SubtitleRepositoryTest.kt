package github.zerorooot.nap511.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleRepositoryTest {
    @Test
    fun appendsTheApiExtensionToTitlesWithoutASubtitleSuffix() {
        assertEquals("电影.ass", SubtitleRepository.downloadFileName("电影", " .ASS "))
        assertEquals("Show.S01E02.1080p.ssa", SubtitleRepository.downloadFileName("Show.S01E02.1080p", "ssa"))
    }

    @Test
    fun doesNotDuplicateAnExistingSubtitleExtension() {
        assertEquals("字幕.ASS", SubtitleRepository.downloadFileName("字幕.ASS", "ass"))
        assertEquals("字幕.srt", SubtitleRepository.downloadFileName("字幕.srt", "ass"))
    }

    @Test
    fun suppliesASafeNameForBlankOrRelativePathNames() {
        for (name in listOf("", " ", ".", "..")) {
            assertEquals("subtitle.ass", SubtitleRepository.downloadFileName(name, "ass"))
        }
    }

    @Test
    fun sanitizesFileNamesWithoutLosingTheFormat() {
        assertEquals("folder_subtitle_01_.ass", SubtitleRepository.downloadFileName("folder\\subtitle:01?", "ass"))
        assertEquals("folder_subtitle.ass", SubtitleRepository.downloadFileName("folder/subtitle.ass", "ass"))
    }

    @Test
    fun doesNotAppendUntrustedOrUnsupportedExtensions() {
        assertEquals("字幕", SubtitleRepository.downloadFileName("字幕", "../../ass"))
        assertEquals("字幕", SubtitleRepository.downloadFileName("字幕", "exe"))
        assertEquals("字幕", SubtitleRepository.downloadFileName("字幕", ""))
    }
}
