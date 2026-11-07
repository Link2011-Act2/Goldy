package jp.linkserver.goldy

import org.junit.Assert.*
import org.junit.Test

class ResumeHttpTest {
    private val strong = ResumeHttp.Validator("ETag", "\"revision-1\"")
    private fun partial(range: String? = "bytes 15-29/30", length: String? = "15", encoding: String? = null,
                        start: Long = 15, total: Long? = 30, response: String? = strong.value) =
        ResumeHttp.partial(range, length, encoding, start, total, 100, strong, response)

    @Test fun acceptsExactBoundedSingleRange() {
        assertEquals(ResumeHttp.Range(15, 29, 30), partial())
        assertEquals(ResumeHttp.Range(15, 20, 30), partial("bytes 15-20/30", "6"))
    }
    @Test fun rejectsMismatchedOrMalformedRangeAndLength() {
        for (range in listOf(null, "bytes 14-29/30", "bytes 15-30/30", "bytes 29-15/30", "bytes 15-29/*",
            "bytes 15-29/31", "bytes 15-29/101", "bytes 15-29/9999999999999999999999", "bytes 15-29/30, bytes 0-1/30"))
            assertNull(range, partial(range))
        for (length in listOf(null, "14", "16", "-15", "+15", "9999999999999999999999")) assertNull(partial(length = length))
        assertNull(partial(encoding = "gzip")); assertNull(partial(response = "\"revision-2\""))
        assertNull(partial(start = 0)); assertNull(partial(response = null))
    }
    @Test fun onlyStrongValidatorsCanBeUsedForIfRange() {
        assertEquals(strong, ResumeHttp.validator(strong.value, null, null))
        assertNull(ResumeHttp.validator("W/\"revision-1\"", null, null))
        assertNull(ResumeHttp.validator("unquoted", null, null))
        val last = "Sun, 06 Nov 1994 08:49:37 GMT"
        assertNull(ResumeHttp.validator(null, last, "Sun, 06 Nov 1994 08:50:36 GMT"))
        assertEquals(ResumeHttp.Validator("Last-Modified", last, "Sun, 06 Nov 1994 08:50:37 GMT"),
            ResumeHttp.validator(null, last, "Sun, 06 Nov 1994 08:50:37 GMT"))
        assertNull(ResumeHttp.validator("W/\"weak\"", last, "Sun, 06 Nov 1994 08:50:37 GMT"))
        assertNull(ResumeHttp.validator(null, last, "invalid"))
    }
    @Test fun unsatisfiedRangeIsNotCompletionEvidence() {
        assertEquals(30L, ResumeHttp.unsatisfiedTotal("bytes */30"))
        for (header in listOf(null, "bytes */0", "bytes */*", "bytes 0-1/2", "bytes */999999999999999999999"))
            assertNull(ResumeHttp.unsatisfiedTotal(header))
    }
}
