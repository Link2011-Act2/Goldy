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
    val install: String = "Install",
    val allowInstall: String = "Allow installation in settings",
    val permissionRequired: String = "Allow installation in settings.",
    val checking: String = "Checking for updates…",
    val noUpdate: String = "No update is available.",
    val noApk: String = "No compatible APK is available. View this release on GitHub for details.",
    val downloading: String = "Downloading…",
    val verifying: String = "Verifying APK…",
    val preparing: String = "Preparing installer…",
    val ready: String = "Ready to install.",
    val handedOff: String = "Installer opened.",
    val cancelled: String = "Download cancelled.",
    val incompleteHistory: String = "Some earlier releases may be missing."
)
