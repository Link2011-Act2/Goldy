package jp.linkserver.goldy.compose

import jp.linkserver.goldy.ApkProgress
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class DownloadProgressFormatterTest {
    @Test fun decimalUnitsAndTwoDecimalPlaces() {
        val cases = mapOf(0L to "0 B", 999L to "999 B", 1000L to "1.00 KB", 1234L to "1.23 KB",
            999_999L to "1000.00 KB", 1_000_000L to "1.00 MB", 12_340_000L to "12.34 MB",
            Long.MAX_VALUE to "9223372036854.78 MB", Long.MIN_VALUE to "0 B")
        cases.forEach { (bytes, expected) -> assertEquals(expected, DownloadProgressFormatter.formatBytes(bytes, Locale.US)) }
    }
    @Test fun actualRatioAndLocales() {
        val progress = ApkProgress(12_340_000, 25_670_000, 2)
        assertEquals("12.34 MB / 25.67 MB · 48.07%", DownloadProgressFormatter.format(progress, Locale.US))
        assertEquals("12.34 MB / 25.67 MB · 48.07%", DownloadProgressFormatter.format(progress, Locale.JAPAN))
        assertEquals("12,34 MB / 25,67 MB · 48,07\u00a0%", DownloadProgressFormatter.format(progress, Locale.GERMANY))
        assertEquals(12_340_000f / 25_670_000f, DownloadProgressFormatter.fraction(progress)!!, 0.000001f)
    }
    @Test fun unknownAndInvalidTotalsNeverInventPercentages() {
        for (total in listOf(null, 0L, -1L, Long.MIN_VALUE)) {
            val progress = ApkProgress(1234, total, 1)
            assertEquals("1.23 KB", DownloadProgressFormatter.format(progress, Locale.US))
            assertNull(DownloadProgressFormatter.fraction(progress))
        }
    }
    @Test fun roundingDoesNotAnnouncePrematureCompletion() {
        for (progress in listOf(ApkProgress(999_999, 1_000_000, 1), ApkProgress(Long.MAX_VALUE-1, Long.MAX_VALUE, 1))) {
            assertTrue(DownloadProgressFormatter.format(progress, Locale.US).endsWith("99.99%"))
            assertTrue(DownloadProgressFormatter.fraction(progress)!! < 1f)
        }
        assertTrue(DownloadProgressFormatter.format(ApkProgress(Long.MAX_VALUE, Long.MAX_VALUE, 1), Locale.US).endsWith("100.00%"))
    }
    @Test fun invalidByteCountsAndOvershootAreBoundedWithoutOverflow() {
        assertEquals(0f, DownloadProgressFormatter.fraction(ApkProgress(Long.MIN_VALUE, 1, 1))!!, 0f)
        assertEquals(1f, DownloadProgressFormatter.fraction(ApkProgress(Long.MAX_VALUE, 1, 1))!!, 0f)
        assertEquals("0 B / 1 B · 0.00%", DownloadProgressFormatter.format(ApkProgress(-1, 1, 1), Locale.US))
    }
}
