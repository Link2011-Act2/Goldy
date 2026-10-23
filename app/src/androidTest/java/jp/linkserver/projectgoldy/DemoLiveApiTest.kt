package jp.linkserver.projectgoldy

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.linkserver.goldy.CheckOutcome
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opt in explicitly with instrumentation -e class ...DemoLiveApiTest; real unauthenticated requests. */
@RunWith(AndroidJUnit4::class)
class DemoLiveApiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun fetchThreePublicRepositoriesAndCheckDemoDistribution() {
        org.junit.Assume.assumeTrue("Real GitHub API checks require -e goldyLive true",
            androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("goldyLive") == "true")
        val model = ViewModelProvider(rule.activity)[LiveDemoViewModel::class.java]
        rule.onNodeWithText("Live Demo").performScrollTo().performClick()
        LiveTarget.entries.forEach { target ->
            rule.runOnIdle { model.select(target); model.fetch(); model.fetch() }
            rule.waitUntil(120_000) { !model.snapshot.checking }
            rule.runOnIdle {
                assertNotNull("${target.title}: ${model.snapshot.message}", model.snapshot.catalog)
                assertTrue(model.snapshot.catalog!!.releases.isNotEmpty())
                assertTrue(model.comparison() is CheckOutcome.Available || model.comparison() is CheckOutcome.Incomplete)
                android.util.Log.i("GoldyDemoVerification", "${target.repository}: releases=${model.snapshot.catalog!!.releases.size}, tag=${model.selectedRelease()?.tag}, comparison=${model.comparison()?.javaClass?.simpleName}")
            }
        }
        rule.activityRule.scenario.recreate()
        rule.runOnIdle {
            val restored = ViewModelProvider(rule.activity)[LiveDemoViewModel::class.java]
            assertSame(model, restored)
            assertNotNull(restored.snapshot.catalog)
        }
        rule.onNodeWithText("Update Demo").performScrollTo().performClick()
        val update = ViewModelProvider(rule.activity)[DemoUpdateViewModel::class.java]
        rule.waitUntil(15_000) { !update.restoring }
        rule.runOnIdle { update.check(); update.check() }
        rule.waitUntil(120_000) { !update.busy }
        rule.runOnIdle {
            assertFalse(update.metadata.lastOutcome is CheckOutcome.Failure)
            assertNotNull(update.metadata.lastOutcome)
            android.util.Log.i("GoldyDemoVerification", "Self distribution: ${update.metadata.lastOutcome}; ${update.notice}")
        }
    }
}
