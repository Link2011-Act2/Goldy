package jp.linkserver.goldy

object ReleaseSelector {
    fun select(
        catalog: ReleaseCatalog,
        installed: InstalledVersion,
        policy: UpdatePolicy = UpdatePolicies.Standard,
        options: RuntimeOptions = RuntimeOptions()
    ): CheckOutcome {
        val developer = policy.effectiveDeveloper(installed, options)
        val current = developer.currentVersionOverride?.trim()?.takeIf { it.isNotEmpty() } ?: installed.name
        val published = catalog.releases.filter(policy.releaseFilter)
        if (catalog.complete && catalog.releases.isEmpty()) return CheckOutcome.NoPublishedReleases
        if (!developer.ignoreVersionComparison && policy.versions.compare(current, current) == null) {
            return CheckOutcome.UnsupportedVersion
        }
        val eligible = published.filter { accepts(it, installed.name, policy) }
        val candidates = eligible.filter {
            developer.ignoreVersionComparison || (policy.versions.compare(it.tag, current, it.prerelease) ?: -1) > 0
        }
        val comparator = Comparator<GitHubRelease> { left, right ->
            val order = policy.versions.compare(left.tag, right.tag, left.prerelease, right.prerelease) ?: 0
            if (order != 0 || !policy.publicationTieBreak) order
            else compareValues(left.publishedAt, right.publishedAt).takeIf { it != 0 }
                ?: left.id.compareTo(right.id)
        }
        val selected = when {
            policy.feed == ReleaseFeed.Latest -> candidates.firstOrNull()
            developer.ignoreVersionComparison && policy.developerSelection == SelectionMode.MostRecentlyPublished ->
                candidates.maxByOrNull { it.publishedAt?.toEpochMilli() ?: 0 }
            else -> candidates.maxWithOrNull(comparator)
        }
        val notes = if (!policy.intermediateNotes || selected == null ||
            (developer.ignoreVersionComparison && policy.developerSelection == SelectionMode.MostRecentlyPublished)
        ) emptyList() else if (policy.intermediateNotesIncludeEquivalentVersions) {
            candidates.sortedWith { a, b -> comparator.compare(b, a) }.drop(1)
        } else eligible.filter {
            !it.tag.equals(selected.tag, true) &&
                (policy.versions.compare(it.tag, current, it.prerelease) ?: -1) > 0 &&
                (policy.versions.compare(it.tag, selected.tag, it.prerelease, selected.prerelease) ?: 0) < 0
        }.sortedWith { a, b -> comparator.compare(b, a) }
        val candidate = selected?.let { UpdateCandidate(it, notes, catalog.complete) }
        if (!catalog.complete) return CheckOutcome.Incomplete(candidate)
        return candidate?.let { CheckOutcome.Available(it) }
            ?: if (eligible.isEmpty()) CheckOutcome.NoEligibleRelease else CheckOutcome.UpToDate
    }

    internal fun accepts(release: GitHubRelease, installed: String, policy: UpdatePolicy): Boolean {
        if (policy.channels == ChannelPolicy.AllPublished) return true
        // ShutUp's suffix acceptance differs from its display channel detection.
        val suffix = release.tag.substringAfter('-', "").substringBefore('+').lowercase(java.util.Locale.ROOT)
        val stable = !release.prerelease && (suffix.isEmpty() || suffix == "stable" || suffix == "release")
        if (policy.channels == ChannelPolicy.StableOnly) return stable
        return when (policy.versions.channel(installed)) {
            ReleaseChannel.IntDev -> true
            ReleaseChannel.Beta -> !suffix.startsWith("intdev") && !suffix.startsWith("internal") &&
                (suffix.startsWith("beta") || stable)
            else -> stable
        }
    }
}
