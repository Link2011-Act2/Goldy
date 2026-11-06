package jp.linkserver.projectgoldy

import jp.linkserver.pyrite.screen.PyriteNavigationDirection

/** Order matches the top-level tabs. Detail is one level after Notifications. */
internal enum class DemoDestination(val key: String, val tab: Int) {
    Showcase("showcase", 0), Live("live", 1), Update("update", 2),
    StyleReference("style-reference", 3), Notifications("notifications", 4),
    NotificationDetails("notification-details", 4);

    companion object {
        fun fromKey(key: String): DemoDestination = entries.firstOrNull { it.key == key } ?: Showcase
    }
}

/** Uses Pyrite's actual initial/target pair, including interrupted transitions. */
internal fun demoNavigationDirection(initial: DemoDestination, target: DemoDestination): PyriteNavigationDirection =
    if (target.ordinal >= initial.ordinal) PyriteNavigationDirection.Forward else PyriteNavigationDirection.Backward
