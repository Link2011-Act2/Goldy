# Transfer presentation

Elief's `DownloadProgressFormatter` formats bytes using decimal B/KB/MB (1000),
with two decimal places for KB/MB and the percentage. The host configuration's
locale supplies number and percent punctuation. Unknown or nonpositive totals
show transferred bytes only and an indeterminate progress bar. Negative byte
counts are clamped to zero, overrun percentages to 100%, and incomplete transfers
to at most 99.99% (and a bar fraction below one). Calculations avoid integer
multiplication overflow. This formatter does not authenticate transfer completion.

`UpdateScreenLayout` uses the same formatter. A permission-required state presents
one action to open installation settings. The host must recheck actual permission
on returning from settings and choose Idle/Ready according to whether an APK has
been verified/prepared. Permission grant alone must not replay an uncertain prior
installer handoff. Failed actions use the actual action label instead of an
ambiguous retry label. Gold download, verification and lease behavior is unchanged.

Validation on 2026-11-07: Gold 129 and Elief 23 JVM tests; Debug/Release builds,
Demo R8, Gold/Elief lint; 20 Demo UI tests on API36 at 320x640dp, fontScale1.0.
These include the standard update icon in light/dark, permission action layout,
unknown-size progress, narrow layout and IME accessibility. Public JVM descriptors
from the prior af5f415 publication remain present. Publication is recorded
separately after the pushed commit's artifacts are retrieved.
