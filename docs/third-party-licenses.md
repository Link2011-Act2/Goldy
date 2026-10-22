# Third-party licenses

Gold and Elief are normal AARs, not fat/shaded AARs. Dependencies and their
notices remain separate artifacts. An application distributor must retain
the applicable notices for the complete resolved application, including its
own dependencies. No proprietary font or Pyrite code is included.

| Dependency family | License | Source |
|---|---|---|
| Goldy and derived version algorithms | MIT | Root LICENSE and Gold's META-INF/goldy/LICENSE/NOTICE; source revisions in phase-2-core-report.md |
| AndroidX, Compose, Material3, annotation, collection, lifecycle, startup, profileinstaller, arch.core, tracing, versionedparcelable, concurrent | Apache-2.0 | Published Maven POMs and [AndroidX source](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt) |
| Kotlin standard library and kotlinx.coroutines | Apache-2.0 | [Kotlin license](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt), [Coroutines license](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt) |
| Markwon core / ext-tables 4.6.2 | Apache-2.0 | [Markwon license](https://github.com/noties/Markwon/blob/v4.6.2/LICENSE) |
| commonmark / commonmark-ext-gfm-tables 0.13.0 | BSD-2-Clause | Published commonmark-parent POM and artifact license |
| Guava listenablefuture / failureaccess when resolved | Apache-2.0 | Published POM and artifact license |

JUnit 4 (EPL-1.0), Hamcrest (BSD-3-Clause) and JSON-java (public domain in the
selected release) are test dependencies, not Gold/Elief production dependencies.
Android testing libraries use their respective published licenses. The sample's
Expressive prerelease is not forced onto Elief consumers.
