package jp.linkserver.goldy

import android.content.Context

/** Access the same durable store used by Goldy.create, for idempotent host migration before loading. */
object UpdateStateStores {
    fun preferences(context: Context, repository: GitHubRepository): UpdateStateStore =
        PreferencesUpdateStateStore(context.applicationContext, repository)
}
