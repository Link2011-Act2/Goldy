package jp.linkserver.goldy.compose

import jp.linkserver.goldy.*
import org.junit.Assert.*
import org.junit.Test

class UpdateNotificationTest {
    private val strings = UpdateNotificationStrings()
    @Test fun anEmptyStateIsNotProofOfSuccessfulCheck() {
        assertEquals(strings.idle, notificationStatus(GoldyState(), strings))
        assertEquals(strings.upToDate, notificationStatus(GoldyState(lastOutcome = CheckOutcome.UpToDate), strings))
    }
    @Test fun failureAndRateLimitTakePriorityOverCachedAvailability() {
        val candidate = UpdateCandidate(GitHubRelease(1, "v2.0", url = "https://github.com/test/repo/releases/tag/v2.0"))
        assertEquals(strings.failure, notificationStatus(GoldyState(candidate, fromCache = true,
            lastOutcome = CheckOutcome.Failure(GoldyError.Network)), strings))
        assertEquals(strings.throttled, notificationStatus(GoldyState(candidate,
            lastOutcome = CheckOutcome.Throttled(10)), strings))
    }
    @Test fun checkingHasPriorityOverPreviousFailure() {
        assertEquals(strings.checking, notificationStatus(GoldyState(checking = true,
            lastOutcome = CheckOutcome.Failure(GoldyError.Network)), strings))
    }
    @Test fun distinctEmptyOutcomesAndPartialCatalogRemainDistinct() {
        for ((outcome, text) in listOf(CheckOutcome.NoPublishedReleases to strings.noReleases,
            CheckOutcome.NoEligibleRelease to strings.noEligible, CheckOutcome.UnsupportedVersion to strings.unsupported,
            CheckOutcome.Disabled to strings.disabled, CheckOutcome.Incomplete(null) to strings.incomplete))
            assertEquals(text, notificationStatus(GoldyState(lastOutcome = outcome), strings))
    }
    @Test fun stringsCanBeLocalizedByTheHost() {
        assertEquals("通信失敗", notificationStatus(GoldyState(lastOutcome = CheckOutcome.Failure(GoldyError.Network)),
            UpdateNotificationStrings(failure = "通信失敗")))
    }
}
