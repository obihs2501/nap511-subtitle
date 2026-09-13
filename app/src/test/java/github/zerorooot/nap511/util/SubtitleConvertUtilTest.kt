package github.zerorooot.nap511.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.Charset
import java.util.Locale

class SubtitleConvertUtilTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun dialogue(
        text: String,
        start: String = "0:00:01.00",
        end: String = "0:00:03.00"
    ) = "Dialogue: 0,$start,$end,Default,,0,0,0,,$text"

    private fun ass(vararg records: String) = "[Events]\n" + records.joinToString("\n")

    private fun source(
        text: String,
        name: String = "test.ass",
        charset: Charset = Charsets.UTF_8,
        bom: ByteArray = byteArrayOf()
    ): File = File(temporaryFolder.root, name).apply {
        writeBytes(bom + text.toByteArray(charset))
    }

    private fun prepare(file: File): File = checkNotNull(
        SubtitleConvertUtil.prepareForPlayback(file, File(temporaryFolder.root, "converted"))
    ) { "Conversion failed for ${file.name}" }

    private fun cue(
        text: String,
        start: String = "00:00:01,000",
        end: String = "00:00:03,000",
        index: Int = 1
    ) = "$index\n$start --> $end\n$text\n\n"

    @Test
    fun convertsOverrideTagsLineBreaksHardSpacesAndCommas() {
        val text = ass(
            "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text",
            dialogue("""{\i1}你好\hWorld{\i0}\N第二行\n第三行, with comma""")
        )
        assertEquals(cue("你好 World\n第二行\n第三行, with comma"), prepare(source(text)).readText())
    }

    @Test
    fun acceptsUtf8BomBeforeEventsHeader() {
        val file = source(ass(dialogue("中文")), bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        assertEquals(cue("中文"), prepare(file).readText())
    }

    @Test
    fun acceptsUtf16LittleEndianWithBom() {
        val file = source(ass(dialogue("中文字幕")), charset = Charsets.UTF_16LE,
            bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
        assertEquals(Charsets.UTF_16LE, SubtitleConvertUtil.detectCharset(file))
        assertEquals(cue("中文字幕"), prepare(file).readText())
    }

    @Test
    fun acceptsUtf16BigEndianWithBom() {
        val file = source(ass(dialogue("中文字幕")), charset = Charsets.UTF_16BE,
            bom = byteArrayOf(0xFE.toByte(), 0xFF.toByte()))
        assertEquals(Charsets.UTF_16BE, SubtitleConvertUtil.detectCharset(file))
        assertEquals(cue("中文字幕"), prepare(file).readText())
    }

    @Test
    fun checksUtf32BomBeforeUtf16Prefix() {
        val encodings = listOf(
            "UTF-32LE" to byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0, 0),
            "UTF-32BE" to byteArrayOf(0, 0, 0xFE.toByte(), 0xFF.toByte())
        )
        for ((name, bom) in encodings) {
            val charset = Charset.forName(name)
            val file = source(ass(dialogue("中文")), "$name.ass", charset, bom)
            assertEquals(charset, SubtitleConvertUtil.detectCharset(file))
            assertEquals(name, cue("中文"), prepare(file).readText())
        }
    }

    @Test
    fun supportsGbkAndGb18030ChineseSubtitles() {
        for ((name, text) in listOf("GBK" to "简体字幕", "GB18030" to "中文𠮷")) {
            val file = source(ass(dialogue(text)), "$name.ass", Charset.forName(name))
            assertEquals(name, cue(text), prepare(file).readText())
        }
    }

    @Test
    fun acceptsMixedCaseRecordsAndIgnoresOtherSectionsAndComments() {
        val text = """
            [Script Info]
            Dialogue: 0,0:00:00.00,0:00:10.00,Default,,0,0,0,,Not an event
            [eVeNtS]
            fOrMaT: layer, START, end, style, name, marginl, marginr, marginv, effect, TEXT
            Comment: 0,0:00:00.00,0:00:10.00,Default,,0,0,0,,Not dialogue
            dIaLoGuE: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,台词
            [Fonts]
            Dialogue: 0,0:00:00.00,0:00:10.00,Default,,0,0,0,,Not an event either
        """.trimIndent()
        assertEquals(cue("台词"), prepare(source(text)).readText())
    }

    @Test
    fun acceptsSsaMarkedFieldWithoutFormatRecord() {
        val text = ass("Dialogue: Marked=0,0:00:01.00,0:00:03.00,Default,,0,0,0,,SSA字幕")
        assertEquals(cue("SSA字幕"), prepare(source(text, "test.SSA")).readText())
    }

    @Test
    fun honorsReorderedFieldsAndPreservesCommasWhenTextIsNotLast() {
        val text = ass("Format: End, Text, Start", "Dialogue: 0:00:03.25,Hello, world,0:00:01.10")
        assertEquals(cue("Hello, world", "00:00:01,100", "00:00:03,250"), prepare(source(text)).readText())
    }

    @Test
    fun skipsBrokenRecordsWithoutLosingValidDialogue() {
        val text = ass(
            "Dialogue: too,few,fields",
            dialogue("bad start", start = "bad"),
            dialogue("bad minute", start = "0:60:00.00"),
            dialogue("bad second", start = "0:00:60.00"),
            dialogue("overflow", start = "999999999999999999999:00:00.00"),
            dialogue("reversed", start = "0:00:04.00"),
            dialogue("zero duration", end = "0:00:01.00"),
            dialogue("仍能显示")
        )
        assertEquals(cue("仍能显示"), prepare(source(text)).readText())
    }

    @Test
    fun preservesTimePrecisionAndNormalizesHours() {
        val text = ass(
            dialogue("precision", "1:02:03.456", "1:02:04.5"),
            dialogue("whole seconds", "0:00:00", "0:00:01.01")
        )
        val expected = cue("whole seconds", "00:00:00,000", "00:00:01,010") +
            cue("precision", "01:02:03,456", "01:02:04,500", index = 2)
        assertEquals(expected, prepare(source(text)).readText())
    }

    @Test
    fun sortsEventsAndMergesSimultaneousBilingualDialogue() {
        val text = ass(
            dialogue("later", "0:00:04.00", "0:00:05.00"),
            dialogue("中文"), dialogue("English"), dialogue("English")
        )
        val expected = cue("中文\nEnglish") + cue("later", "00:00:04,000", "00:00:05,000", 2)
        assertEquals(expected, prepare(source(text)).readText())
    }

    @Test
    fun omitsVectorDrawingsAndResumesTextAfterDrawingModeEnds() {
        val text = ass(
            dialogue("""{\p1}m 0 0 l 10 10{\p0}"""),
            dialogue("""{\p1}m 0 0 l 10 10{\p0}真正的台词{\pos(20,30)}""")
        )
        assertEquals(cue("真正的台词"), prepare(source(text)).readText())
    }

    @Test
    fun dropsEmptyLinesThatWouldTerminateAnSrtCue() {
        assertEquals(cue("one\ntwo"), prepare(source(ass(dialogue("""one\N\Ntwo\N""")))).readText())
    }

    @Test
    fun recoversAfterAFormatWithoutRequiredFields() {
        val text = ass(
            "Format: Layer, Text", "Dialogue: 0,ignored",
            "Format: Start, End, Text", "Dialogue: 0:00:01.00,0:00:03.00,valid"
        )
        assertEquals(cue("valid"), prepare(source(text)).readText())
    }

    @Test
    fun detectsAssContentWithoutOrWithIncorrectExtension() {
        for (name in listOf("字幕下载", "download.srt", "download.sub")) {
            val result = prepare(source(ass(dialogue("正文")), name))
            assertEquals("srt", result.extension)
            assertEquals(cue("正文"), result.readText())
        }
    }

    @Test
    fun keepsWebVttFormatAndExtensionInsteadOfRenamingItToSrt() {
        val vtt = "WEBVTT\n\n00:00:01.000 --> 00:00:03.000\n字幕\n"
        for (name in listOf("test.webvtt", "test.vtt", "wrong.srt")) {
            val result = prepare(source("\uFEFF$vtt", name))
            assertEquals("vtt", result.extension)
            assertEquals(vtt, result.readText())
        }
    }

    @Test
    fun reencodesUtf16SrtAndStripsTheBom() {
        val expected = cue("中文")
        val file = source(expected, "test.srt", Charsets.UTF_16LE, byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
        assertEquals(expected, prepare(file).readText())
    }

    @Test
    fun detectsExtensionlessSrtAndNormalizesLineEndings() {
        val expected = cue("subtitle")
        assertEquals(expected, prepare(source(expected.replace("\n", "\r\n"), "download")).readText())
        assertEquals(cue("ASS"), prepare(source(ass(dialogue("ASS")).replace('\n', '\r'))).readText())
    }

    @Test
    fun neverOverwritesTheSourceOrAnotherPreparedSubtitle() {
        val original = source(ass(dialogue("ASS")))
        val existingSrt = source(cue("original SRT"), "test.srt")
        val originalBytes = original.readBytes()
        val first = checkNotNull(SubtitleConvertUtil.prepareForPlayback(original, temporaryFolder.root))
        val second = checkNotNull(SubtitleConvertUtil.prepareForPlayback(original, temporaryFolder.root))
        val reencoded = checkNotNull(SubtitleConvertUtil.prepareForPlayback(existingSrt, temporaryFolder.root))
        assertNotEquals(first, second)
        assertNotEquals(existingSrt, reencoded)
        assertTrue(originalBytes.contentEquals(original.readBytes()))
        assertEquals(cue("original SRT"), existingSrt.readText())
        assertEquals(cue("ASS"), first.readText())
        assertEquals(cue("ASS"), second.readText())
    }

    @Test
    fun returnsNullForEmptyUnsupportedOrNonDialogueContent() {
        val outputDir = File(temporaryFolder.root, "converted")
        for ((name, text) in listOf(
            "empty.ass" to "",
            "html.txt" to "<html>Not a subtitle</html>",
            "no-events.ass" to "[Script Info]\nTitle: no events",
            "drawing.ass" to ass(dialogue("""{\p1}m 0 0 l 10 10"""))
        )) {
            assertNull(name, SubtitleConvertUtil.prepareForPlayback(source(text, name), outputDir))
        }
        assertFalse(outputDir.exists())
    }

    @Test
    fun alwaysWritesAsciiTimestampsRegardlessOfDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            assertEquals(cue("text"), prepare(source(ass(dialogue("text")))).readText())
        } finally {
            Locale.setDefault(previous)
        }
    }
}
