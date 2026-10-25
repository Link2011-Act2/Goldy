package jp.linkserver.goldy.internal

import jp.linkserver.goldy.ReleaseChannel

/** Retains the JVM entry point used by previously compiled clients. */
internal fun compareShutUpVersions(remoteTag: String, installedVersion: String): Int? =
    compareReleaseVersions(remoteTag, installedVersion)

/** Keep ShutUpVersionsKt.commonReleaseChannel without duplicating its implementation. */
@JvmName("commonReleaseChannel")
internal fun legacyCommonReleaseChannel(value: String): ReleaseChannel =
    commonReleaseChannel(value)
