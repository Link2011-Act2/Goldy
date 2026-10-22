package jp.linkserver.goldy.compose

/** Replace for localization or app-specific wording; channel text is supplied separately. */
data class UpdateStrings(
    val title: String = "App update",
    val back: String = "Back",
    val releaseNotes: String = "Release notes",
    val noNotes: String = "No release notes were provided.",
    val history: String = "Earlier releases",
    val expand: String = "Show notes",
    val collapse: String = "Hide notes",
    val openRelease: String = "Open on GitHub",
    val download: String = "Download APK",
    val cancel: String = "Cancel download",
    val retry: String = "Try again",
    val install: String = "Open installer",
    val allowInstall: String = "Allow installation",
    val permissionRequired: String = "Allow this app to install unknown apps, then return and try again.",
    val checking: String = "Checking for updates…",
    val noUpdate: String = "No update is available.",
    val noApk: String = "No compatible APK is available. View this release on GitHub for details.",
    val downloading: String = "Downloading…",
    val verifying: String = "Verifying APK…",
    val preparing: String = "Preparing installer…",
    val ready: String = "The APK is verified and ready.",
    val handedOff: String = "Installer opened. Complete the update in Android’s installer. Installation has not been confirmed.",
    val cancelled: String = "Download cancelled.",
    val incompleteHistory: String = "Some earlier releases may be missing."
)
