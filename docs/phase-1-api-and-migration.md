# Project Goldy — v0.1.0 API・安全性・移行設計案

日付: 2026-10-21（JST）  
状態: **Phase 1・未承認の提案。宣言・Kotlin例は設計説明であり、実装済みAPIやコンパイル済みサンプルではありません。**

根拠と現状比較は [調査報告](phase-1-investigation.md)、採取結果は [作業ツリー記録](phase-1-repository-state.json) を参照してください。

## 1. モジュール構成

```text
:app                 Goldy内の小さなサンプル／検証ホスト
  └─ elief   任意: Compose UI、Markdown、UIからの操作連携
       └─ gold Androidライブラリ: 確認、比較、状態、転送、検証、Intent生成

利用アプリ ────────────┘
Pyrite ← 利用アプリのUIから必要に応じて使用（Goldyからの依存なし）
```

| モジュール | 内容 | 依存方針 |
| --- | --- | --- |
| gold | 公開モデル/設定、バージョン比較、GitHub REST、Repository/StateFlow、SharedPreferences標準ストア、APK転送と検証、専用Providerとインストール準備 | Android API、Coroutines、必要最小限のAndroidX Core。ネットワークはHttpURLConnection。Compose、Pyrite、Room、WorkManager、Markwonを含めない |
| elief | 標準の詳細画面、状態を受け取るContent、バナー/バッジ/確認ダイアログ、Markdown | core、Compose Material 3、lifecycle-compose、Markwon core/ext-tables。HTML/画像ロードのプラグインは入れない |
| app | 既存のGoldyアプリ雛形を検証ホストとして使う案 | Goldyのみで既定動作と3アプリ相当の設定を試す。既存3アプリのコードは変更しない |

minSdkは両ライブラリとも26。compileSdkは現Goldy/3アプリに合わせ37を出発点とします。ライブラリが利用アプリのtargetSdkを変更する構成にはしません。現在のGoldyとGlyphVisualizerのKotlin 2.2.10／JVM 11側との互換性を維持する出力を目標とし、Scheduler/ShutUpのJVM 17でも確認します。ビルド用JDK・AGPの要件とは分けて扱います。

将来の座標案は `jp.linkserver.goldy:gold:0.1.0` と `jp.linkserver.goldy:elief:0.1.0`、名前空間案は `jp.linkserver.goldy` と `jp.linkserver.goldy.compose` です。公開先や座標の利用可能性は未確定であり、依存取得できるものとして案内しません。

分割は2本に留め、GitHub専用モジュール、download専用モジュール、純JVM版、プラグイン登録機構、汎用更新配信インターフェースは作りません。

## 2. 公開APIの骨格

標準利用はGitHubRepositoryだけを指定します。ViewModel等の所有CoroutineScopeを渡すことで、構成変更と終了時の取消を明確にします。ライブラリはApplication Contextのみを保持し、Activityを保持しません。

```kotlin
val goldy = Goldy.create(
    context = application,
    config = GoldyConfig(
        repository = GitHubRepository("Link2011-Act2", "ExampleApp")
    ),
    ownerScope = viewModelScope
)

// ホストが起動／復帰等のタイミングで呼ぶ。初期化だけでは通信しない。
viewModelScope.launch { goldy.check(CheckTrigger.Automatic) }

// 手動操作は通常の8時間制限を迂回する。
viewModelScope.launch { goldy.check(CheckTrigger.Manual) }
```

### 2.1 設定

```kotlin
data class GoldyConfig(
    val repository: GitHubRepository,
    val policyId: String = "standard-v1",
    val versionPolicy: VersionPolicy = VersionPolicies.Standard,
    val channelPolicy: ChannelPolicy = ChannelPolicy.InstalledTrack,
    val releaseFilter: (GitHubRelease) -> Boolean = { true },
    val apkSelector: ApkSelector = ApkSelectors.SingleOrUniversal,
    val selectionMode: SelectionMode = SelectionMode.HighestVersion,
    val historyMode: HistoryMode = HistoryMode.Intermediate,
    val checkInterval: Duration = 8.hours,
    val initialOptions: RuntimeOptions = RuntimeOptions(),
    val expectedVersionCode: ((GitHubRelease) -> Long?)? = null,
    val limits: GoldyLimits = GoldyLimits()
)

data class RuntimeOptions(
    val automaticChecksEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val developer: DeveloperOptions = DeveloperOptions()
)

data class DeveloperOptions(
    val ignoreVersionComparison: Boolean = false,
    val ignoreCheckInterval: Boolean = false,
    val currentVersionOverride: String? = null,
    val previewSelection: SelectionMode? = null,
    val ignoreDismissedVersions: Boolean = false
)
```

`GitHubRepository` はowner/nameを検証する値オブジェクトとします。URL入力用の `fromUrl` はhttps://github.com/{owner}/{repo} とその/releases等の既知の形だけを解析し、任意文字列中の「github.com/」を探す方法は使いません。APIの接続先は内部で構築します。

RuntimeOptionsの永続化はホスト側の責任です。Goldyは有効なオプションを状態へ反映するだけで、アプリ全体の設定を別ファイルへ二重保存しません。標準ストアが保存するのは更新キャッシュ・成功時刻・通知無視等です。GlyphVisualizerの `SettingsPreferences` と `automatic_update_check_enabled` は引き続き既存設定の正本にできます。

`updateOptions` は受け取った設定を原子的に反映し、キャッシュからの通知/候補を再評価します。それだけでは通信しません。ホストは必要なら続けてcheckを呼びます。設定変更中の古い取得結果で、非適格な通知を復活させません。

### 2.2 コントローラーと状態

```kotlin
class Goldy {
    val state: StateFlow<GoldyState>

    suspend fun check(
        trigger: CheckTrigger = CheckTrigger.Automatic
    ): CheckOutcome

    fun download(candidate: UpdateCandidate)
    suspend fun cancelDownload()
    suspend fun updateOptions(options: RuntimeOptions)

    fun hideBanner()
    suspend fun ignoreVersion(key: ReleaseKey)
    suspend fun resetIgnoredVersions()

    suspend fun prepareInstall(apk: VerifiedApk): InstallPreparation
}
```

| 型/操作 | 契約 |
| --- | --- |
| Goldy.create | context/config/ownerScope、任意のUpdateStateStoreを受ける。実インストール済みpackage/versionName/versionCode/署名はPackageManagerから取得。初期化だけで通信・ダウンロードしない |
| GitHubRelease / GitHubAsset | release/asset ID、tag、name、ノート、prerelease、公開日時、URL、asset名/サイズ/digestを保持。GitHub APIの生JSONObjectを公開しない |
| ReleaseKey | repository＋raw tagの識別子。新規データではrelease IDも保持。同じtagのasset置換はasset ID/size/digest等で識別。表示文字列と分離 |
| UpdateCandidate | 選択Release、選択APKまたはAPK選択不能理由、中間ノート、取得時刻、履歴の完全性、メタデータ再取得の必要性。APKなしでもRelease詳細は開ける |
| GoldyState | availableUpdate、notificationUpdate、showBanner、checkStatus、downloadState。成功/失敗/キャッシュ利用をUIへ明示 |
| CheckOutcome | Available、UpToDate、NoPublishedReleases、NoEligibleRelease、UnsupportedVersion、Disabled、Throttled、Incomplete、Failureを区別。ネット不通や比較不能を「最新」としない |
| checkStatus | 未確認/確認中/完了/失敗。候補のキャッシュを持ったまま失敗も表せる。lastSuccess、stale、nextAllowedCheckAt、catalogCompleteを公開 |
| downloadState | Idle / Downloading / Cancelling / Verifying / Ready / Cancelled / Failed。必ず対象Release/assetの識別子を付ける |
| VerifiedApk | 内部コンストラクターのハンドル。検証した対象、ファイル識別、サイズ、SHA-256、APKの版、署名照合結果を結び付ける。任意Fileを渡して作れない |
| InstallPreparation | PermissionRequired(settingsIntent) / Ready(installerIntent) / Failure(error)。Intentを作る前に最新の検査を行う |
| エラー | 安定した種類と必要なパラメーターを持つGoldyError。ネットワーク、HTTP/レート制限、URL、解析、APK選択、サイズ、digest、package、署名、版、容量、ファイル消失、installer起動を区別。UI文言をcoreへ埋め込まない |

check/download/prepareInstallのI/OはcoreがIO Dispatcherへ移します。呼出し側がwithContext(IO)を重複実装する必要はありません。CancellationExceptionは通常のFailureへ変換せず再送出します。

同じ設定で同時に行われる確認は進行中の取得を共有し、重複HTTPを抑えます。手動確認も同じHTTPが進行中ならその結果を利用できます。所有Scopeの終了は全所有処理を取り消します。複数インスタンス・複数プロセスをまたぐ転送共有は初版外とし、アプリ内で1つのGoldyを共有する使い方を標準にします。

### 2.3 差し替え箇所

| 差し替え口 | 目的と制約 |
| --- | --- |
| VersionPolicy | rawタグ/実APK版の解析と比較。解析結果は数値列、チャンネル、修飾子、同一性を示すcanonicalIdentityを持つ。比較の「同順位」とAPKの「同一版」を別扱いにできる |
| ChannelPolicy | InstalledTrack、StableOnly、AllPublished、明示的な許可集合。GitHub prereleaseの扱いもここで決める。未知のチャンネルを標準で安定版に昇格させない |
| releaseFilter | アプリ以外のReleaseを除外する純粋な述語。候補・履歴・復元・テスト時にも適用。通常は未指定 |
| ApkSelector | 検証されたRelease.assetsから1件を返す。別のURLや別Releaseのassetを返した場合は拒否。選択が曖昧なら理由を返し、勝手に先頭を採らない |
| UpdateStateStore | suspend read/writeのスナップショット保存。既定はSharedPreferences。旧スキーマの移行やアプリ独自保存が必要な場合だけ実装。Goldy側のMutex下で一貫した状態を保存 |

通信基盤、APKセキュリティ検査、任意のFileのインストールをプラグイン化しません。独自AssetSelectorを使っても、URL/package/署名/code/対象版照合は省略されません。時計・HTTPの偽物など、テスト専用の差し替えはinternalへ留めます。

`policyId` は自動シリアライズできないアプリ側ポリシーをキャッシュに対応付ける識別子です。比較・除外・チャンネル方針を変更した版では更新し、古い結果を新しい方針で再評価します。

## 3. 確認・選択・通知の動作契約

### 3.1 取得と候補選択

処理順序は固定します。

1. 実インストール版と現在オプションを読み、テスト設定の適用可否を決定。
2. キャッシュを現在のrepository/policy/版で再評価し表示。自動確認OFF、間隔内、server backoffなら通信を省略。
3. GitHubから一覧またはlatestを取得し、DTO・URL・必須フィールド・サイズ上限を検査。
4. draft除外とreleaseFilterを適用。**開発者設定でこの段階を飛ばさない。**
5. VersionPolicyとChannelPolicyを適用し、通常時は現在版より新しいものに絞る。
6. 選択モードで候補を確定し、ApkSelectorを適用。インストール対象APKがなくてもノートとReleaseリンクを残す。
7. 同じ除外/チャンネル方針で現在版 < 中間版 < 候補を降順抽出。
8. 現在設定をもう一度確認して、結果・成功時刻・キャッシュ・通知を一貫して更新。

| 選択モード | 意味 |
| --- | --- |
| HighestVersion（標準） | 解釈可能な候補のバージョン最大。意味上同順位なら公開日時、IDで安定した順序にする。公開日時で高いバージョンを逆転させない |
| MostRecentlyPublished | 適格Releaseのpublished_at最大。日時欠損を日時0の最新とみなさず、決定不能を報告。主にShutUpのIntDevプレビュー用 |
| GitHubLatest | APIの/releases/latestの選択をそのまま使う。「公開日時が最大」「SemVerが最大」と読み替えない。GlyphVisualizer移行用 |

標準一覧はper_page=100、最大5ページです。Linkのnextは同一api.github.comの同じリポジトリ・一覧パス・許可ページパラメーターに限定し、URLとして再検査します。GitHubの返却順はバージョン順とは仮定しません。現在版を見付けたという理由だけで、後のページの走査を打ち切りません。

5ページ等の上限に達したら `catalogComplete=false` とし、取得済みの最良候補やノートは表示可能ですが、全体の最大や「更新なし」を確定したと装いません。UIは履歴が一部であることとReleaseページへの導線を示します。latest＋HistoryMode.Noneは「latestという取得対象を完了した」と表し、全履歴取得済みとは表示しません。

ETagはendpoint/page単位の本文と組で保存し、304ではその本文を使います。本文がOSによって削除された等の場合は条件なしで再取得します。途中ページの失敗・不正JSONを成功した空一覧として保存しません。GitHubの条件付きリクエストとレート制限に従います。[GitHub RESTの推奨事項](https://docs.github.com/en/rest/using-the-rest-api/best-practices-for-using-the-rest-api)

### 3.2 比較とチャンネル

標準はv付き/なし、数値の不足桁を0補完、Release/Stable別名、IntDev/internal・alpha・beta・rcの順序、番号付きsuffix、_rev、+metadataを扱うShutUp寄りの規則です。これは厳密なSemVer限定ではないため「SemVer完全準拠」とは称しません。数値のオーバーフロー、空・不明な版は比較不能です。

標準InstalledTrackは次を提案します。

| 実インストール版 | 通常の候補 |
| --- | --- |
| Release/Stable/裸の数値 | 非prereleaseの安定版 |
| Beta | Betaと非prereleaseの安定版。IntDev/alpha/rcは入れない |
| IntDev/internal | 解釈可能な全開発段階と安定版 |
| Alpha/RC/その他の既知prerelease | 標準では非prereleaseの安定版のみ。別の範囲は明示的な許可集合で指定 |
| 未知/解析不能 | 自動で任意チャンネルへ拡張せず、UnsupportedVersion。ホストの明示的なポリシーで解決 |

GitHubのprerelease=trueと安定版名の食い違いは、標準では安定版として受け入れません。チャンネルの表示ラベルはUI側の関数へ渡し、Stable/Releaseの表示上の区別も維持可能にします。

Scheduler/GlyphVisualizerの互換VersionPolicyはアプリ側に置きます。旧パーサーのknown prefix/compact/番号比較の規則を特性テストで固定し、一般のGoldyにアプリ名を埋め込みません。一方、不明文字列から偶然数字を抽出して更新扱いにする挙動やオーバーフローは引き継がず、比較不能として扱う安全性の改善を明記します。

**候補比較が0でも、APKの版が同じとは限りません。** 例えばGlyphの旧比較ではBeta2とBeta10が同順位でも、canonicalIdentityは区別し、Beta10の候補にBeta2のAPKを渡せません。_rev番号も同様です。接頭辞・v・Release/Stable等の許可した同義表記だけを正規化します。+metadataは順序に使いませんが、対象APKとの照合方針は明示的に定義し、修飾子全体を無差別に捨てません。

### 3.3 間隔・キャッシュ・通知

- 標準の確認間隔は最後の**成功**から8時間。実インストールのversionName/code、repository、policyId、比較用現在版が変わった場合や時計巻戻りでは再評価する。
- Manualは通常間隔とautomaticChecksEnabledを迂回する。HTTP 403/429等が示すRetry-After/reset時刻はManualでも尊重する。
- 確認失敗/取消では最後の成功時刻を進めず、既存の適格なキャッシュを保持する。通信エラーと、正常に全対象を確認した更新なしを区別する。
- 成功した更新なしでは候補を消す。JSON破損は復元失敗として扱い、通知無視等の独立設定まで消さない。
- キャッシュにはschemaVersion、repository、ポリシー識別、実/比較版の識別、候補と中間ノート、取得範囲、成功時刻を付ける。復元時にも除外・URL・チャンネル・版を再検査する。
- availableUpdateは詳細/手動操作の対象、notificationUpdateは永続的な無視を反映した対象、showBannerは一時非表示も反映した値とする。
- hideBannerはメモリー上の一時非表示で、バッジ/手動詳細を消さない。ignoreVersionは永続化してバナーとバッジを消すが、手動詳細は残す。
- 無視状態はrepositoryごと。旧データは3アプリ共通の大文字小文字を区別しないtag照合を移行アダプターで維持する。新しい別tagの更新は再表示する。
- notificationsEnabled=falseでも手動確認とavailableUpdateは使える。resetIgnoredVersionsは通信なしでキャッシュ由来の通知を再評価できる。
- 通知を実際に出せる画面、作業中の抑止、アンインストール案内や他のダイアログとの競合はアプリ側で決める。

## 4. APK転送・検証の不変条件

### 4.1 URLとHTTP

初回URLはHTTPS、github.com、通常の443、userinfoなしとし、設定したowner/repoのreleases/download/{対象tag}/{対象asset}に対応することを確認します。ReleaseページURLも同じrepositoryの既知のReleaseパスに限定します。相対URL、別repository、HTTP、file/content/intentスキーム、偽ホスト、dot segment、二重デコードを利用する経路、区切り文字を偽装したパスを拒否します。

自動リダイレクトは無効化し、各hopでスキーム・host・portを検査します。標準はgithub.comの適切なRelease経路とGitHubのRelease配信CDN（release-assets.githubusercontent.com、必要な旧配信先objects.githubusercontent.com/github-releases.githubusercontent.com）だけを明示許可する案です。ワイルドカードで全githubusercontent.comを許可しません。最大5回。CDNの署名queryは通信時だけ使用し、保存やログに出しません。具体的な経路はPhase 2の接続テストで確認し、未知ホストへ自動的に許可を広げません。[GitHubの配信ドメイン資料](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)

TLS検証を無効化しません。初版は認証なしでありPATをAPKへ入れません。将来の認証を理由にAuthorizationを外部CDNへ引き継ぐ仕組みも作りません。API/assetに使うURL制約と、Markdownの一般外部リンクの規則は別のものとして扱います。

初期案の上限はAPI本文1ページ2MiB、1確認の本文合計8MiB、Release本文256KiB、最大5ページ、APK512MiBです。GoldyLimitsで合理的な上限を指定可能にし、初版は無制限を既定にしません。3アプリで実際に問題ない値かをPhase 2で測定します。タイムアウトは確認10秒/10秒、転送15秒/30秒を出発点にします。

### 4.2 保存・取消

転送先は `cacheDir/goldy/staging/` の生成した内部名です。外部のasset名・tagをそのままパスにしません。検証後に `cacheDir/goldy/verified/` へ同一ファイルシステム上で確定させます。清掃はGoldyが所有するファイルだけを対象にします。

転送するRelease/asset/期待ハッシュは開始時点で固定します。途中の更新確認で別候補に変わっても進捗や完了先を差し替えません。別対象の進行中転送は明示し、二重起動を拒否します。

取消ではJobの取消だけに依存せず、現在のストリーム/接続を閉じる経路、runInterruptible、ループ・EOF・検証後・確定前のensureActiveを組み合わせます。Cancelling中は次の転送を始めず、I/Oの終了と.part削除を確認してCancelledへ遷移します。ブロック読込やrename直前の競合も必須テストにします。取消後のインストール要求は発生させません。

進捗はbytesを正本とし、総量不明なら％を作りません。通常は256KiB程度または時間による間引きを行い、開始・終了を必ず通知します。容量不足、HTTPエラー、切断、rename失敗、cancelのいずれも未完成ファイルを検証済みとして残しません。OSによるcache消去はFileMissingとして再取得できるようにします。

### 4.3 検証の順序

| 順序 | 必須条件 |
| --- | --- |
| 1. 受信サイズ | 0バイト拒否。GitHub asset.sizeとHTTP Content-Lengthが分かる場合は双方と一致。ストリーム実測が上限を超えた時点で中止。長さ不明でも次の検査は必須 |
| 2. digest | 常にSHA-256を計算。提供されたsha256 digestは完全一致。未提供は「提供digestとの照合なし」であり一致済みとは表示しない。提供されている不正形式/未対応形式を黙って未提供にしない |
| 3. APK解析 | PackageManagerのarchive解析を署名情報付きで実行。null、署名欠損、破損、単体更新用でないsplit APK等を拒否。対応SDK差はガード |
| 4. package | archive.packageNameが実インストール済みの自アプリのpackageNameに完全一致。他アプリ更新を引数で許可するAPIは設けない |
| 5. signer | インストール済みと候補の現在署名者の集合全体が一致。複数署名のany一致を禁止。初版は異なる現在鍵へのローテーションを自動許可せず、理由を返す |
| 6. versionCode | 実インストール済みcode以上。同codeの再インストールやチャンネル昇格は可能。小さいcodeはテスト時も拒否 |
| 7. Releaseとの対応 | APK versionNameをVersionPolicyで解析し、選んだtagのcanonicalIdentityと照合。比較用現在版の偽装をこの検査に使わない。対応を証明できないタグは表示だけにしてインストール不能 |
| 8. 期待code | アプリからexpectedVersionCodeが指定された場合は完全一致も必須 |
| 9. 直前再検査 | installer準備時にfileのハッシュ/実在と、最新の実インストール済み情報を再検査。不一致はReadyを解除 |

Release APIにはversionCodeがないため、数字のタグからcodeを推測しません。期待codeの供給がない場合、保証できるのは下限・versionName対応・digest/署名の確認です。特定Releaseの厳密な期待codeまで保証したいアプリは、既知の対応をexpectedVersionCodeで渡します。

API 26/27はGET_SIGNATURESと互換のcode取得、28以降はSigningInfo、33以降は新しいflags APIを使います。ShutUpが行っているAPI 28/29のarchiveで旧署名flagも渡す互換経路は回帰試験します。単独署名のhistoryの存在と現在鍵の一致を混同しません。未対応API呼出し、証明書欠損、解析エラーは安全側に拒否します。

提供digestはGitHubメタデータと同じ信頼経路の情報です。独立した署名の代替ではありません。OSのarchive解析・同一署名の照合・最終インストーラー判定を残します。apksigは公式にも端末外利用を主目的としているため、初版に端末内検証器として無条件に組み込みません。テストAPK生成とホスト側検証には利用できます。[apksig](https://android.googlesource.com/platform/tools/apksig/)

## 5. インストールとライフサイクル

coreは専用クラス `GoldyFileProvider` と専用authority `${applicationId}.goldy.updates` を持つ案です。既存のandroidx.core.content.FileProviderとManifest上で同一名としてマージされる衝突を避けます。exported=false、grantUriPermissions=true、公開可能パスは検証済みディレクトリだけとし、stagingやcache全体を含めません。

INTERNETはcoreのManifestで宣言します。REQUEST_INSTALL_PACKAGESは利用アプリが明示宣言する方針です。確認のみを使うアプリへインストール権限を暗黙追加しません。今回の3アプリには既に宣言があります。

```xml
<!-- インストール機能を使うホストのManifest。Phase 1では変更しない。 -->
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

`prepareInstall` は新たな転送を開始しません。検証済みハンドルから再検査し、許可がなければ自packageのACTION_MANAGE_UNKNOWN_APP_SOURCES用Intent、許可があればcontent URIのACTION_VIEW＋APK MIME type＋読取権限を準備します。準備または実際の起動で生じる許可宣言欠如、設定Activity不在、installer不在、SecurityExceptionを区別します。Android 11以降はpackage visibilityによってresolveActivityの結果が制限されるため、nullだけで起動不能と断定しません。標準はActivityNotFoundExceptionを含む起動結果を扱い、事前照会が必要な場合だけ対応するintent queriesを宣言します。[package visibility](https://developer.android.com/training/package-visibility/use-cases)Activityが起動を担い、必要な権限はこのファイルの読取に限定します。[FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider)

標準Compose RouteはActivity Result経由で設定/installerを起動し、起動例外を状態に戻します。ユーザーの「ダウンロードしてインストール」操作を起点とする一度限りのイベントIDで起動を管理し、再Compose・画面回転・復帰で繰り返し起動しません。バックグラウンド中はインストーラーを勝手に開きません。

許可設定から戻ったら再確認し、未許可なら待機を維持します。再タップで続行でき、設定画面を自動でループ起動しません。installerを閉じた時も、Intent起動だけでは「更新完了」にしません。復帰/次回起動時のPackageManagerによる版確認を正本にします。

VerifiedApkはメモリー上のハンドルです。プロセス再生成後の「検証済み」フラグや未処理のinstallイベントを復元して自動起動しません。残存.partはGoldy領域だけ清掃し、残存.apkは再検査なしに利用しません。installerが読んでいるAPKを別転送や即時清掃で消さないよう、使用中ファイルを保護し、使用後/有効期限後に清掃します。

## 6. Compose UI

### 6.1 標準RouteとContent

```kotlin
GoldyUpdateRoute(
    goldy = goldy,
    candidate = candidate,
    onBack = onBack
)

// 独自Scaffoldや既存画面の一部に組み込みたい場合
GoldyUpdateOverviewContent(
    candidate = candidate,
    downloadState = state.downloadState,
    strings = goldyStrings(),
    onPrimaryAction = onPrimaryAction,
    onCancel = onCancel,
    onOpenRelease = onOpenRelease
)
```

Routeはライフサイクルに応じたStateFlow購読、主操作/取消、許可/installerの起動をまとめます。Contentは状態とイベントだけを受け、通信・File・Activityを直接操作しません。

共通部分は版、チャンネル、最新ノート、中間ノートの折り畳み、Releaseリンク、APK名、進捗/総量不明、取消、検証中、インストール、再試行、エラーです。候補がない・APKがない・履歴が一部・ノート空欄も状態として表示します。

| カスタマイズ | 方針 |
| --- | --- |
| テーマ | MaterialTheme.colorScheme/typography/shapesを継承。Goldy自身でThemeや動的配色を開始しない |
| 文言 | 日本語/英語のgoldyStringsを用意しcopyで上書き。title、操作、空表示、件数/進捗の書式、channelLabel、errorMessageを差し替え可能 |
| 戻る | onBackを必須にする。アプリのNavController、戻り先、ハードウェアBackHandlerはホストが所有 |
| topBar | slotでアプリ側TopAppBar/Pyriteの構成を差し込める |
| versionHeader | slotまたはGoldyVersionHeaderのTextStyle引数でNothingDot等を保持 |
| loadingIndicator | slotでShutUp/Schedulerのローディングを保持。既定は安定APIのMaterial 3部品 |
| notes | 既定の安全なMarkdown。必要な時だけrenderer slotでホスト描画に差し替え |
| レイアウト | 最大幅、余白、Modifierを指定可能。既定840dp上限を出発点に狭幅/長文/大文字倍率で確認 |
| 通知部品 | GoldyUpdateBanner/Action/Promptは任意。既存の配置/部品を残してstateだけ使うことも可能 |

中間ノートの開閉とスクロールはReleaseKeyをキーにrememberSaveableで保持します。ダウンロードと確認はViewModelのGoldyが所有し、ComposableのrememberCoroutineScopeへ転送全体を閉じ込めません。画面を離れた時に取り消すかどうかはホストの所有Scope/戻る処理で決め、標準ではOSをまたぐ継続サービスは作りません。

MarkdownはMarkwon core＋tableで、見出し、リスト、コード、リンク、表を表示します。選択可能なTextViewとテーマ色を使い、同じ本文を進捗更新のたびに再解析しません。解析負荷とUIへの適用を分け、長文の上限も守ります。HTML/JavaScript実行、外部画像の自動取得、任意intent/fileリンクは初版で有効にしません。リンククリックはhttp/httpsの妥当な外部URLだけをコールバックへ渡します。差し替えrendererの安全性はホスト側の責任として明示します。

## 7. 3アプリの導入例

以下の `*VersionPolicy`、`*ApkSelector`、`read*Options` は**導入時にアプリ側で用意する薄い互換アダプターの仮名**です。GoldyにShutUp/Scheduler/GlyphBartyの名前を組み込むAPIではありません。ここにあるコードを既存リポジトリへ追加していません。

### 7.1 ShutUp

```kotlin
val goldy = Goldy.create(
    context = application,
    config = GoldyConfig(
        repository = GitHubRepository("Link2011-Act2", "ShutUp"),
        policyId = "shutup-v1",
        versionPolicy = ShutUpVersionPolicy,
        channelPolicy = ChannelPolicy.InstalledTrack,
        apkSelector = ShutUpApkSelector,
        selectionMode = SelectionMode.HighestVersion,
        initialOptions = readShutUpUpdateOptions()
    ),
    ownerScope = viewModelScope
)
```

readShutUpUpdateOptionsは旧 `dev_ignore_version_comparison` と `dev_ignore_update_interval` を読みます。前者ONではignoreVersionComparison=trueとpreviewSelection=MostRecentlyPublished、後者ONではignoreCheckInterval=trueにします。実インストール版がIntDevでなければcore側も全テスト指定を無効化します。

ShutUpApkSelectorは名前にshutupを含むAPKを優先します。複数の最高優先候補が同点なら、既存の先頭依存をやめ曖昧選択を報告します。これは安全性の改善として移行差分に明記します。

既存AppUpdateViewModelからgoldy.stateを公開し、onResume/手動確認を委譲します。ShutterDestinationTheme、他操作との `canShowUpdate` 条件、戻り先のupdateReturnScreen、AboutのPyrite部品は保持します。

```kotlin
ShutterDestinationTheme("update") {
    GoldyUpdateRoute(
        goldy = goldy,
        candidate = candidate,
        onBack = onBack,
        strings = goldyStrings().copy(
            title = stringResource(R.string.update_screen_title)
        ),
        loadingIndicator = { ShutterUpdateLoadingIndicator() }
    )
}
```

### 7.2 NITTC Scheduler

```kotlin
val goldy = Goldy.create(
    context = application,
    config = GoldyConfig(
        repository = GitHubRepository("Link2011-Act2", "NITTC_Scheduler"),
        policyId = "nittc-scheduler-v1",
        versionPolicy = SchedulerVersionPolicy,
        channelPolicy = ChannelPolicy.AllPublished,
        releaseFilter = { release -> !isAiRuntimeReleaseTag(release.tag) },
        apkSelector = SchedulerApkSelector,
        selectionMode = SelectionMode.HighestVersion,
        initialOptions = readSchedulerUpdateOptions()
    ),
    ownerScope = viewModelScope
)
```

SchedulerVersionPolicyはscheduler_/nittc_、compact形式、PreReleaseを含む優先度、単純な末尾Beta10/IntDev10等の比較を再現します。AllPublishedで現在の受入範囲を維持します。Release利用者をStableOnlyへ制限する変更は、この移行へ暗黙に混ぜません。

isAiRuntimeReleaseTagは既存のアプリ側述語を使用し、ai-runtime-*を常に除外します。大文字小文字、APK添付、テストモード、旧キャッシュ、中間ノートでも除外できることを回帰試験します。

SchedulerApkSelectorはnittcを含むか、schedulerを含むか、universalを含むかの順に辞書式で優先します。readSchedulerUpdateOptionsは旧show_latest_release_for_testingをignoreVersionComparison＋ignoreCheckIntervalに、current_version_override_for_testingを比較用現在版へ対応させます。プレビューもHighestVersionであり、ShutUpと同じ公開日時順にはしません。

SchedulerUiState.appUpdateへの結合、onResume/設定から戻る時の確認、Outputタブのバッジ・バナー、無視解除、既存onBackを維持します。既存のAdaptiveContentPaneに合わせてContentを使うかRouteの最大幅を指定し、AppLoadingIndicatorをslotで渡します。既存ProviderはQR等の共有に残し、Goldyの検証済みAPKだけ専用Providerを使います。

### 7.3 GlyphVisualizer / GlyphBarty

```kotlin
val goldy = Goldy.create(
    context = application,
    config = GoldyConfig(
        repository = GitHubRepository("Link2011-Act2", "GlyphBarty"),
        policyId = "glyphbarty-legacy-v1",
        versionPolicy = GlyphBartyVersionPolicy,
        channelPolicy = ChannelPolicy.AllPublished,
        apkSelector = GlyphBartyApkSelector,
        selectionMode = SelectionMode.GitHubLatest,
        historyMode = HistoryMode.None,
        initialOptions = readGlyphBartyUpdateOptions()
    ),
    ownerScope = viewModelScope
)
```

latest自体の非prerelease選択を維持します。IntDev利用者だから一覧方式へ変えてIntDev Releaseも取得する、といった挙動変更はしません。将来はポリシー変更で可能ですが別の変更として扱います。中間ノートを初回移行で勝手に増やしません。

GlyphBartyVersionPolicyはglyphbarty_とcompact形式、数値＋チャンネル優先度という旧候補比較を保持します。APK同一性はBeta番号/_revも区別します。GlyphBartyApkSelectorはglyphbarty、universalの順で優先します。

readGlyphBartyUpdateOptionsは既存SettingsPreferences/CaptureUiStateのautomaticUpdateCheckEnabled（既定false）を正本にします。既存のshow_latest_release_for_testingはignoreVersionComparison＋GitHubLatestプレビュー＋ignoreDismissedVersionsへ、ignore_check_interval_for_testingは間隔無視へ対応させます。両設定は独立です。

```kotlin
GoldyUpdateRoute(
    goldy = goldy,
    candidate = candidate,
    onBack = { screen = Screen.ABOUT },
    topBar = { GlyphUpdateTopBar(nothingStyleEnabled, onBack) },
    versionHeader = { item ->
        GoldyVersionHeader(
            candidate = item,
            versionTextStyle = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = if (nothingStyleEnabled) NothingDotFontFamily else null,
                fontWeight = if (nothingStyleEnabled) FontWeight.Normal else FontWeight.Bold
            )
        )
    }
)
```

タイトルのNType、版のNothingDot、MaterialTheme、既存の日本語/英語文言、UPDATEからABOUTへの戻りを維持します。自動確認とAboutの手動確認で同じGoldy状態を使い、rememberの候補二重保持を解消します。「1日1回」の文言は実装の8時間に合わせる案を移行レビューへ含めます。

### 7.4 旧設定・キャッシュ移行

3アプリ共通の旧SharedPreferences名は `github_release_updates` ですが、同じ名前でも**アプリごとの別サンドボックス**です。端末間でこの情報を共有する設計にはしません。

| 項目 | ShutUp | Scheduler | GlyphVisualizer |
| --- | --- | --- | --- |
| 成功時刻 | last_check_ms | last_check_ms | last_check_ms |
| 確認時の版 | last_check_version | last_check_version（比較用版） | 旧キーなし |
| 無視tag | dismissed_tag | dismissed_update_tag | dismissed_update_tag |
| 候補JSON | cached_update（tag/notes/url等） | cached_update（tagName/releaseNotes等） | 永続候補なし |
| 比較無視 | dev_ignore_version_comparison | show_latest_release_for_testing | show_latest_release_for_testing |
| 間隔無視 | dev_ignore_update_interval | show_latest_release_for_testingと連動 | ignore_check_interval_for_testing |
| 比較用現在版 | なし | current_version_override_for_testing | なし |
| 自動確認設定 | 明示的な既存スイッチなし | 明示的な既存スイッチなし | SettingsPreferencesのautomatic_update_check_enabled |

導入時の一度限りのアダプターで旧値を読み、Goldyのバージョン付き保存形式へ移します。元の設定ファイルや無関係キーを一括clearしません。アプリ全体の既存設定キーは保持し、テストオプションも既存画面で読み書きできます。

旧候補JSONは現在ポリシー・URL・版を再検査してから引き継ぎます。asset ID/size/digest等の不足は `requiresMetadataRefresh` として持ち、ダウンロード前に同じtagの正規APIから再取得して確定します。旧キャッシュのURLを直接ダウンロードしません。復元不能・除外対象・破損データなら候補と間隔情報を無効化して再確認しますが、通知無視や他の設定は維持します。旧データからVerifiedApkを生成しません。

キャッシュのJSON文字列を新スキーマとして無条件に読み替えたり、旧版にないlast_check_versionを現在版の成功として捏造したりしません。新保存形式で移行完了を原子的に記録し、二度実行しても状態が壊れないようにします。アプリのバックアップ/設定インポート後も、実インストール版を使ってテスト設定を再制限します。

## 8. 必須テストと完了条件

本節は**実装後に実行する計画**です。Phase 1では既存テストの読取りのみで、テストの追加・実行はしていません。

| 領域 | 必須ケース |
| --- | --- |
| 3本の特性テスト | ShutUpの数値/Stable/Release/IntDev/内部/alpha/beta/rc/_rev/+metadata、Schedulerのprefix/compact/RC/Beta番号、Glyphのprefix/compact/旧同順位比較を固定。現存テストを根拠にGoldy内へ追加 |
| 異常な版 | 空、nightly、未知suffix、数値オーバーフロー、不正prefix、不正installed version。比較不能と更新なしを分離 |
| チャンネル/候補 | Release/Beta/IntDevの受入、prereleaseとの矛盾、API順とバージョン順の違い、意味上の同順位、published_at欠損、GitHubLatestを公開日時順に読み替えない |
| 非アプリRelease | ai-runtime-*、大文字、非常に大きい版、APKありでも除外。履歴/キャッシュ/比較無視/上書き現在版からも混入しない |
| API | 一覧/最新、draft、body=null、assetなし、無効フィールド、空一覧、ページ順不同、nextの不正URL、上限打切り。HTTP 404を「更新なし」と断定しない |
| HTTPキャッシュ | ETag＋304、本文消失、ページごとのETag、途中失敗、403/429のRetry-After/reset、5xx、TLS/timeout。失敗/取消でlastSuccessを進めない |
| 通知/競合 | 復元、閉じるとバッジは残る、版無視と再起動、手動詳細は残る、新tagで再表示、無視解除、確認中の無視/設定変更、同時auto/manualで重複取得を抑える |
| 時刻/設定 | 8時間境界、時計巻戻り、実版/code変更、比較用版変更、通知OFF、自動OFFでも手動可能。IntDev以外では保存済み・import済みテスト値をすべて無効化 |
| APK選択 | 大文字拡張子、複数APK、アプリ名/ universal優先、同点の曖昧性、対応しないABI、別Releaseのasset、APKなし |
| URL | HTTP、github.com.evil、userinfo、異常port、別repository/別tag、dot/encoded traversal、file/content/intent、HTTP降格、未知CDN、redirect loop。テストHTTPサーバーの許可はテスト内部だけ |
| 転送 | 長さ既知/不明、0byte、途中切断、偽Content-Length、asset.size不一致、上限/容量不足、write/rename失敗、遅い読込中のcancel、終了/検証/rename直前cancel、二重開始、古い進捗/完了、.part清掃 |
| digest | 正しいsha256、大小文字、不一致、欠損、不正形式、未対応形式。未照合を照合済みと表示しない |
| APK実体 | 有効な単体APK、ZIP/manifest破損、改ざん、証明書なし、別package、別鍵、debug/release鍵違い、複数署名の一部だけ一致、鍵ローテーション拒否 |
| APK版 | code下位/同じ/上位、期待code不一致、正しいpackage/署名でも別versionName、Beta2対Beta10、_rev違い、比較用版の偽装、比較不能tag。テスト設定で検証が緩まない |
| installer | 許可前後、拒否、宣言なし、設定/installer不在、起動例外、URIの読取だけ可能、他cacheや.part非公開、検査後の改変/消失、installer中にファイルを消さない、戻る/回転で二重起動しない |
| ライフサイクル | 構成変更、画面離脱/再入場、ownerScope終了、プロセス再生成、実インストール後のキャッシュ失効。成功扱いは実メタデータで確認 |
| Compose/Markdown | 空/長文/表/コード/危険リンク/HTML/画像、本文の過剰再解析なし、折り畳み/scroll復元、TalkBack/タッチ領域、狭幅、文字倍率200%、日本語/英語、dark/light/dynamic、Nothingフォント、ホストBack |
| 旧設定 | 3スキーマからの移行、欠損/null/壊れたJSON、古い版、不正URL/ai-runtime、無視・自動OFFの保持、二重移行、設定import、無関係キー非変更 |
| 配布/依存 | core単独でCompose/Pyrite/Markwonが入らない、composeからPyriteなし、Manifest mergeが3本の既存Providerと衝突しない、minSdk26、R8、consumer Kotlin/JVM互換、AAR/依存サイズ、ライセンス |

JVMテストは純粋な比較/候補/状態と、内部HTTPの偽物またはテスト用HTTPサーバーで行います。実APKの署名・PackageManager・Provider・installer・Composeはinstrumentedテストです。署名テストは専用のテスト鍵で複数のfixture APKを作り、本物のRelease秘密鍵は使いません。

端末/エミュレーターの最低確認対象はAPI 26/27、28/29、30、33、36/37です。API 28/29の署名flag、26/27のlegacy API、33以降のflags、現行targetでのinstaller起動を重点確認します。OEM固有のinstaller差は利用可能な実機で追加確認し、未確認の機種を検証済みとしません。

実装後のチェックはGoldyの単体テスト、instrumented/Composeテスト、compile/lint、サンプルのdebug/release双方のビルド、release R8検証と依存解析です。性能・サイズ・互換性は測定してから結果を記載します。

## 9. 段階的な作業計画

| 段階 | 作業と成果 | 次へ進む条件 |
| --- | --- | --- |
| Phase 1（今回） | 3本のHEAD/変更確認、比較表、API/範囲/安全性/導入例/テスト/計画を文書化。既存ツリーの終了時比較 | ユーザーによる設計承認 |
| Phase 2-1 | Goldy内にcore/composeモジュールの最小構成と3方針の特性テスト。core単独依存を確立 | 比較/除外/候補/通知のテストとAPIレビュー |
| Phase 2-2 | GitHub取得、pagination/ETag/制限、cache/通知/取消・同時処理、旧状態の受入/検査 | 通信異常・キャッシュ破損・競合テストが通る |
| Phase 2-3 | 転送、URL/hop検証、サイズ/digest/実APK検証、専用Provider、インストール準備 | セキュリティ試験が通る。検証を弱める逃げ道で失敗を回避しない |
| Phase 2-4 | composeのContent/Route/Markdown、文言/slots、Goldy内サンプルで3構成を再現 | 回転/取消/再試行/戻る/フォント/権限のUI検証 |
| Phase 2-5 | Goldy内の消費側fixtureで、3アプリ相当のminSdk/Kotlin/JVM/既存Providerとの連携確認。R8/サイズ/ライセンス/APIをレビュー | v0.1.0実装完了を報告。公開作業は依頼なしでは実施しない |
| 移行1（別途承認後） | ShutUp。既存の検証・状態を基準に薄いアダプターへ置換、cache/無視/テスト設定とテーマを維持 | 実行済み回帰結果とアプリ差分をレビュー |
| 移行2（別途承認後） | Scheduler。ai-runtime除外、全チャンネル/命名規則、設定/SchedulerUiState/共用Providerを維持 | 非アプリRelease混入なし、署名/取消強化、既存UIと戻り操作の回帰確認 |
| 移行3（別途承認後） | GlyphVisualizer。GitHubLatest/既定OFF/無視のプレビュー挙動、Nothing/日本語英語、設定互換を維持 | 通知・回転・新しいcancel/検証と既存動作の回帰確認 |

Phase 2の承認は**Goldy本体とGoldy内の検証ホスト**の実装開始に対するものとします。既存アプリの移行やPyrite変更を含めません。移行時は最新HEADと未コミット変更を再確認し、承認された作業環境で行います。今回のShutUpの既存差分をリセットしたり上書きしたりしません。

READMEを編集する場合は、明示的なREADME変更依頼がない限り、ユーザー指示に従って別途確認します。コミット・Push・Release・実APKのインストールも現在は許可範囲に含まれません。

## 10. 承認をお願いする設計判断

1. **core＋任意composeの2モジュール**、minSdk26、Pyrite非依存で進める。
2. 標準は8時間、バージョン最大、InstalledTrack。移行では3本ごとの選択/比較/チャンネル/通知差を明示して保持する。
3. Schedulerのai-runtime-*除外をすべての経路で維持する。
4. ShutUpの安全性を基準に、リダイレクト・asset.size・署名者集合・ReleaseとAPK版の対応・直前再検査・取消を強化する。曖昧なAPK選択は停止する。
5. 同versionCodeは許容し下位は拒否。鍵ローテーションの自動受入、split APK、転送resume、バックグラウンドサービスは初版外とする。
6. テストモードは実インストール版IntDevだけで有効にし、安全性検査は解除しない。
7. テーマ/文言/戻り操作/アプリ全体の既存設定はホストが所有し、まずGoldy内だけで実装・検証する。

以上が承認されるまで、Phase 2の本体実装には進みません。
