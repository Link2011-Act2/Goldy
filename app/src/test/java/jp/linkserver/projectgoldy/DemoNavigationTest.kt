package jp.linkserver.projectgoldy

import jp.linkserver.pyrite.screen.PyriteNavigationDirection
import org.junit.Assert.*
import org.junit.Test

class DemoNavigationTest {
    @Test fun savedKeysAreUniqueAndRestoreEveryDestination() {
        assertEquals(6, DemoDestination.entries.map { it.key }.toSet().size)
        DemoDestination.entries.forEach { assertEquals(it, DemoDestination.fromKey(it.key)) }
        assertEquals(DemoDestination.Showcase, DemoDestination.fromKey("unknown-future-screen"))
        assertEquals(DemoDestination.Notifications.tab, DemoDestination.NotificationDetails.tab)
    }

    @Test fun topLevelDirectionFollowsTheVisibleTabOrder() {
        val tabs = listOf(DemoDestination.Showcase, DemoDestination.Live, DemoDestination.Update,
            DemoDestination.StyleReference, DemoDestination.Notifications)
        tabs.zipWithNext().forEach { (first, second) ->
            assertEquals(PyriteNavigationDirection.Forward, demoNavigationDirection(first, second))
            assertEquals(PyriteNavigationDirection.Backward, demoNavigationDirection(second, first))
        }
    }

    @Test fun detailsAndInterruptedNavigationUseTheirActualEndpoints() {
        assertEquals(PyriteNavigationDirection.Forward,
            demoNavigationDirection(DemoDestination.Notifications, DemoDestination.NotificationDetails))
        assertEquals(PyriteNavigationDirection.Backward,
            demoNavigationDirection(DemoDestination.NotificationDetails, DemoDestination.Notifications))
        assertEquals(PyriteNavigationDirection.Backward,
            demoNavigationDirection(DemoDestination.NotificationDetails, DemoDestination.Showcase))
        assertEquals(PyriteNavigationDirection.Forward,
            demoNavigationDirection(DemoDestination.Live, DemoDestination.Notifications))
        assertEquals(PyriteNavigationDirection.Backward,
            demoNavigationDirection(DemoDestination.StyleReference, DemoDestination.Live))
    }
}
