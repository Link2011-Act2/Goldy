package jp.linkserver.goldy

import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Untrusted HTTP response metadata; never grants APK or installation authority. */
internal object ResumeHttp {
    data class Validator(val header: String, val value: String, val observedDate: String? = null)
    data class Range(val start: Long, val end: Long, val total: Long)

    /** If-Range requires a strong validator. Date is strong only under RFC 9110's 60-second rule. */
    fun validator(etag: String?, lastModified: String?, date: String?): Validator? {
        if ((etag?.length ?: 0) > 4096 || (lastModified?.length ?: 0) > 128 || (date?.length ?: 0) > 128) return null
        if (etag != null && etag.matches(Regex("\"[\\x21\\x23-\\x7E]*\"")))
            return Validator("ETag", etag)
        // A client must not substitute a date when the server supplied an entity-tag.
        if (etag != null) return null
        val modified = parseDate(lastModified) ?: return null
        val observed = parseDate(date) ?: return null
        if (Duration.between(modified, observed).seconds < 60) return null
        return Validator("Last-Modified", lastModified!!, date)
    }

    private fun parseDate(value: String?): ZonedDateTime? = value?.let {
        runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME) }.getOrNull()
    }

    /** Accept only an exact single range, with bounded and consistent total and payload length. */
    fun partial(contentRange: String?, contentLength: String?, encoding: String?,
                expectedStart: Long, expectedTotal: Long?, maxBytes: Long,
                expectedValidator: Validator, responseValidator: String?): Range? {
        if (expectedStart <= 0 || maxBytes <= 0 || responseValidator != expectedValidator.value ||
            encoding?.equals("identity", ignoreCase = true) == false) return null
        val match = contentRange?.let { Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matchEntire(it) } ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        val total = match.groupValues[3].toLongOrNull() ?: return null
        val length = contentLength?.takeIf { it.matches(Regex("[0-9]+")) }?.toLongOrNull() ?: return null
        if (start != expectedStart || total <= 0 || total > maxBytes || end < start || end >= total ||
            expectedTotal != null && expectedTotal != total || length != end - start + 1) return null
        return Range(start, end, total)
    }

    /** A 416 is never proof of completion; caller must discard/restart or separately verify. */
    fun unsatisfiedTotal(value: String?): Long? {
        val match = value?.let { Regex("bytes \\*/([0-9]+)").matchEntire(it) } ?: return null
        return match.groupValues[1].toLongOrNull()?.takeIf { it > 0 }
    }
}
