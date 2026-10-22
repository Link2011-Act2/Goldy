# Public API and ownership review

The published surface retains the Phase 2-1/2-2 parser, selector, version policies
and APK verification utilities used by independent hosts. It adds the installer
and optional Compose UI without a new routing framework. HTTP transports,
persistence codecs, installer records and revalidation helpers are internal.
The installer's authority is internal: hosts use InstallRequest.uri, not manual
URI construction/grants. GoldyFileProvider must remain an Android-instantiable
public class; it is not a general host storage API.

| Operation | Context, ownership and error contract |
|---|---|
| Goldy.create / ApkDownloader.create / ApkInstaller.create | Use application Context; no owned background scope or automatic network request |
| Goldy.check / restore/update/ignore | Host scope; StateFlow state; serialized checks/state updates; cancellation propagates; metadata/storage failures reported as CheckOutcome.Failure |
| GitHubReleaseClient.fetch | Serialized IO; pagination/body bounds; same-repository HTTPS only; GitHubException for typed metadata failure |
| ApkDownloader.download | Serialized per process; IO transfer and progress callback; callback must be thread-safe and may throw; ApkException for transfer/verification errors; cancellation disconnects and cleans uncommitted files |
| VerifiedApk | In-memory receipt for an app-private cache file; do not mutate/reconstruct it; host owns completed-file deletion |
| ApkInstaller.prepare | Main-safe IO; permission and file/hash/metadata/current installation rechecked; returns Ready, PermissionRequired or Failed; cancellation propagates |
| ApkInstaller.launch | Rechecks permission/provider/file/current installation on IO, then invokes host callback on caller context; invoke from Main; HandedOff is Activity handoff only |
| InstallRequest / pendingRequests | Persisted lease; restore after process death; storage/corrupt-record errors propagate without deleting potentially active files |
| ApkInstaller.release | Explicit host confirmation that URI is unused; revokes read permission and deletes lease/file; IO/storage errors propagate; no timer or lifecycle auto-release |
| UpdateOverviewScreen / ReleaseNotes | Stateless operations and host callbacks; selected-release saveable scroll/history; host retains jobs/files and handles system back |

The sample and compiled host example synchronously claim operations on Main
before launching coroutines. Updating MutableStateFlow from progress IO is safe;
View/Activity operations must be dispatched to Main. Production hosts supply
localized failures, approved URL navigation and their process-restoration policy.

Security conditions were retained: strict full current signer-set equality,
installed package equality, nondecreasing versionCode, explicit tag/APK identity,
streaming size limits and optional digest verification. No ordering comparison,
developer override, signer-history overlap or unknown URL host bypasses them.
Initial asset URLs remain bound to the selected repository/release/asset; each
HTTPS CDN redirect is allowlisted and redirects are bounded. Shared copies are
revalidated before launch and provider open, and are read-only with a single
temporary read grant. REQUEST_INSTALL_PACKAGES remains a host opt-in.

No API26/27 runtime, physical/OEM installation, multiprocess coordination,
automatic signer rotation or actual installation-success claim was added.
