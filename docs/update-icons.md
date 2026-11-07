# Standard update icon

Elief uses Google's Material Symbols Outlined `browser_updated` as its standard
update symbol. `UpdateNotificationIcon`, `UpdateNotificationBanner` and
`UpdateCheckDialog` all reference the same `elief_update` resource. The Demo's
notification showcase uses those defaults as well.

Its 24dp size, 960-unit viewport, translated group and path match the existing
ShutUp and NITTC Scheduler vectors. The opaque base fill is only a mask: Compose
`Icon` receives its tint from the current Material content color. No fixed
drawable tint is installed. The circular `elief_refresh` resource is unchanged.

Hosts can keep the existing icon slot for intentional customization. A host
using `browser_updated` can omit that override and its duplicate drawable once
the verified public Elief revision is adopted. There is no JVM API change.

The icon is Apache-2.0 licensed by Google. Its attribution and complete license
are bundled in `META-INF/elief/NOTICE` and
`META-INF/elief/MaterialSymbols-APACHE-2.0.txt`, separately from Goldy's MIT license.

`verification/verify-update-icon.py --reference <existing-vector.xml> --output <result.json>`
compares the actual vector geometry; repeat `--reference` for multiple hosts.
Add `--aar <downloaded-elief.aar>` to verify the public resource, refresh geometry
and bundled license. `DefaultUpdateIconTest` renders the three uncustomized slots
in the Demo Activity and checks Light/Dark Material tint; `NotificationScreenTest`
retains the existing notification, callback and restoration coverage.

Publishing and independent-consumer verification must finish before updating a
host's public dependency. Public verification results are recorded separately
from the source change; an earlier successful SHA is not evidence for a new SHA.
