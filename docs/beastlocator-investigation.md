# BeastLocator更新機構とGoldy導入計画

調査日：2026-10-23。既存4アプリとPyriteは読み取り専用です。更新機構の差を調べるため、各作業ツリーを正本とし、既存の変更・ステージ済み削除を含めて保護しました。BeastLocatorのPolicyや既存アプリの移行コードは今回実装していません。

## 調査対象の特定と状態

Codexの登録プロジェクトはBeastLocatorを古いDesktop上のパスで示していました。その場所が存在しなかったため推測で別フォルダーを操作せず、ユーザーが指定した `D:/apps/beastlocator` を確認しました。実体はGitリポジトリです。

| 対象 | HEAD | branch | 開始時差分 |
|---|---|---|---|
| Goldy | ef483970f31efc5d2dcb6560c229c6e71a464601 | master | clean |
| ShutUp | 1a18497cfbe62eac20ecf3ba8264cb8daf163fac | main | 変更3・未追跡18。ステージ済みなし |
| NITTC Scheduler | 9fad0402e268902bbfbea485ca216218c17afdf1 | master | clean、origin/masterより1 commit先行 |
| Glyph Barty | d0adb9c7e98bd888110da7edcd1997ebb29ca4db | master | clean |
| BeastLocator | bc8589756a58b0637cca35eb1ac6042c2577d66c | develop | app/release配下のbaselineProfiles 2件とoutput-metadata.jsonの計3件がステージ済み削除 |
| Pyrite | 508b1cf5131679fee7581acbc7ea55a365b6ff2c | master | docs/shutup-migration-plan.mdに既存変更 |

HEAD・status・index・Git管理対象と未追跡ファイルのハッシュはGoldyの無視対象 `build/elief-ui-review/preservation-before.json` に記録しました。無視済みキャッシュを含むディスク全体の同一性を表すものではありません。既存アプリのビルド・テスト・インストールは行わず、ソース・既存検証画像・既存APKを読みました。

## 4アプリの画面比較

| 観点 | ShutUp | NITTC Scheduler | Glyph Barty | BeastLocator |
|---|---|---|---|---|
| UI基盤 | Compose M3/M3E・ホストテーマ | Compose M3/M3E・AdaptiveContentPane | Compose、Nothing表示切替 | **AppCompat Activity＋XML**。Compose依存なし |
| AppBar | 矢印IconButton＋更新タイトル | AutoMirrored ArrowBack＋タイトル | AutoMirrored ArrowBack、Nothing時NType | 48dp ImageButton＋22sp太字TextViewの独自ヘッダー |
| version/channel | 横並び、太字headlineMedium、AssistChip。displayVersionはvを付けて表示 | 横並び、raw tag＋AssistChip | 横並び、raw tag、Nothing時ドットフォント | widget_bgのパネル内で**縦並び**。タグはNoto Sans Mono 22sp、channelは14sp文字 |
| ノート | surfaceContainerHigh、large shape、outlineVariant、16dp内余白 | 同じ基本形 | 同じ基本形 | 背景付きTextView、14sp、widget_bg、16dp内余白 |
| 履歴 | 全体をまとめて展開。件数・矢印・カード別version/chip/notes | 同じ基本形 | 中間履歴なし | 全体を展開するXML領域。件数・chevron、**履歴を1つのMarkdownへ連結** |
| 操作位置 | 固定bottomBar、full-width Button | 固定bottomBar、full-width Button | 固定bottomBar、full-width Button | **ScrollView内**のDownloadとOpen Releaseボタン |
| 進捗/cancel | VM管理、Cancelを常時操作可能、既知/未知サイズ・エラー・asset名 | rememberと画面Coroutine、cancel Job、状態文言 | remember、転送中ボタン無効。明示cancelなし | LiveData/VM、横ProgressBar、percent/MB、Cancelボタンへ切替 |
| 幅・余白 | max840dp、外18/14dp、間隔18dp | AdaptiveContentPaneのFormContentMaxWidth | 外18/14dp、間隔18dp、専用幅上限なし | 外18dp、notes16dp、画面幅いっぱいのネイティブレイアウト |
| 色・フォント | ホストTheme/Dynamic Color/Pyrite。画面自体が独自テーマを所有しない | 同左 | NType/ドットフォントとホストtheme | Theme.Material3.DayNight.NoActionBar、expressive_*色、背景gradient、角14/20/28dp。名称がExpressiveでもCompose M3Eとは別実装 |
| Dynamic Color | ホスト設定 | ホスト設定 | ホスト設定 | ApplicationでDynamicColors.applyToActivitiesIfAvailable。固定@color参照もありCompose全体の色継承と同一ではない |
| 再生成 | 転送VM、履歴rememberSaveable。通常scrollもsaveable | 転送/履歴は主にremember/画面scope | 転送はremember/画面scope | 転送VM/LiveData、SingleUseValue。履歴foldはBundle。ScrollViewは明示ID/位置保存なしで復元を保証できない |

更新の入口も別構造です。ShutUpはprimaryContainerの更新banner、閉じアイコン、BadgedBox付き更新アイコンと詳細/無視のAlertDialogを持ちます。Schedulerもbanner・badge付き更新アクション・詳細/無視dialogを持ち、ボタンはアプリ共通部品です。Glyphは上部72dp付近のAnimatedVisibility付きUpdateNotificationOverlayから詳細へ進み、一時非表示と次版までの無視を区別します。BeastはMainのネイティブbannerカードを220msで出し、180msで隠します。閉じ操作は永続的な次版までの無視で、Aboutの確認ボタンからもUpdateActivityへ進みます。これらの通知入口はEliefの更新詳細画面が所有するものではなく、移行先ホスト側に残します。

BeastLocatorを既存3アプリと同一のCompose画面とは扱いません。Eliefの標準形はShutUp/Schedulerを基準とし、BeastLocator固有のXML配置を無条件には移植しません。

## 更新機構の比較

| 観点 | ShutUp | NITTC Scheduler | Glyph Barty | BeastLocator |
|---|---|---|---|---|
| 配信先 | Link2011-Act2/ShutUp | Link2011-Act2/NITTC_Scheduler | Link2011-Act2/GlyphBarty | **Link2011-Act2/BeastLocator** |
| metadata取得 | /releases?per_page=100、1ページ | /releases、1ページ | /releases/latest | /releases、1ページ。10秒接続/15秒read、最大2Mi文字。ページング/ETag/rate-limit日時なし |
| 除外 | draft、通常は比較不能tag、URL検査 | draft、ai-runtime-*。name fallback | latest feedに依存 | draft・空tag/nameを除外。**ai-runtime等の固有除外なし**。prereleaseも候補 |
| 比較 | ShutUpの自然順、track制限 | Scheduler数字列/チャンネル順 | Glyph数字列/チャンネル順、latest | 数字列→channel優先度→channel revision。current/remote双方のsuffixを比較 |
| channel | IntDev/Beta/Stable/Release等 | IntDev/Beta/PreRelease/Release等 | IntDev/Beta/Stable/Release等 | IntDev、Beta、PreRelease、**RCを独立表示**、Release、Unknown。AlphaはPreRelease |
| 候補と履歴 | 対象trackの最大、同値履歴あり | 全channel、最大、途中版 | latest、途中履歴なし | 全channel、比較最大。途中版はcurrentより新しくlatestより小さいもの。比較同値は候補・履歴の区別に注意 |
| APK選択 | shutup_優先、最大size等 | nittcsc_優先、最大size等 | glyphbarty優先、最大size等 | unsafe variant/非対応ABIを除外→universal→端末ABI順位→製品名→release名。最後が複数なら選択しない |
| 転送 | suspend/IO、取消、size/digest/署名等 | 画面scope＋同期IO、取消、partial | 同期IO、partial | 2-thread executor＋待ちqueue4、Operation/Future/AtomicBoolean、disconnect、250MiB、5 redirect、256KiB単位進捗 |
| payload検証 | 任意asset SHA256、受信size、package、code、署名履歴等 | アプリ内事前検査なし | アプリ内事前検査なし | Content-Lengthと受信上限、package、code>=installed、方向付きsigner lineage。**payload SHA256/asset.size/Release対APK versionName検査なし** |
| install | .updates FileProvider、許可設定、URI grant | .provider、resolveActivity、許可設定 | .fileprovider、許可設定 | .providerでcache/updatesのみ、URI grant、resolveActivity。設定復帰後に自動download、完了時に1回だけinstallerを開く |
| 間隔/cache | 8h、成功cache、ignore、bannerと通知別 | 同左 | 8h、設定キー、metadata永続cacheなし | 8h、時計逆行時は実行。**失敗時にもlast_check_ms更新**。metadata永続cacheなし |
| 実行/再生成 | Repository/Mutex/StateFlow、転送VM | 更新stateはVM、転送は画面scope | 主に画面state | metadataはActivity所有Operation。転送はAndroidViewModel/LiveData。Coroutinesではなくexecutor/mainHandler |

## BeastLocatorの詳細

### Release・version・ABI

`GitHubReleaseChecker.kt` はDTOへ変換する時にAPKを選択し、`selectLatestUpdate` が最大versionを選びます。`showLatestForTesting` は「公開日時が新しいRelease」ではなく、比較の新旧判定を無視して最大versionを選ぶ機能です。設定の有効化は実BuildConfigがIntDevの時だけです。Beta/RCで開発者UIの一部が見えても、更新用の比較無視・現在版overrideが有効になるとは限りません。

Beastのrevisionパーサーは `_rev12`、`-rev12`、区切り付き番号を扱います。既存GoldのScheduler方針はこれと完全一致しません。例えばBeastのテストは `v0.9.6-IntDev_rev12 > 0.9.6-IntDev_rev9` を要求しますが、GoldのSchedulerパーサーはこのrev形式を同値として扱います。またBeastはRCを独立したchannelとし、Releaseというsuffixが明示されていればprerelease属性だけでPreReleaseへ変更しません。この差を無視して `VersionPolicies.NittcScheduler` をそのまま指定することはできません。

unsafe APK名はdebug/unsigned/unaligned/androidtest/benchmark、test/tests/testing/profile/profiling等を除外します。ABIはarm64-v8a、armeabi-v7a、x86_64、x86とその対応aliasを正規化し、`Build.SUPPORTED_ABIS` の順位を使います。GoldのSingleや既存3アプリ用のprefix方針で代用すると、曖昧性・ABI選択の既存契約を失います。

Gradleは現在package `jp.linkserver.beastlocator`、minSdk26、compile/target36、JVM17、versionName `1.0.0-RC1`、code `202608092` です。既存の `生成APK/beastlocator_v100rc1.apk` を静的解析し、同じpackage/versionName/codeとAPK署名形式の検証成功を確認しました。ローカルtag `v1.0.0-RC1` と対応する一例です。これはGitHubから今回取得した公開assetの同一性や、旧版との署名一致を証明するものではありません。過去のBeta/IntDev/legacyAPI24 APKを含む全配布形式のbinding確認は別途必要です。

### ダウンロード・署名・インストーラー

ダウンロードの接続先はHTTPSのgithub.com・そのsubdomain・githubusercontent.com配下を許可し、redirectも検査します。Goldはより狭いCDN許可とrepository/tag/assetの対応を要求するため、実配布URLで確認が必要です。既存の緩い条件へGoldを戻す計画は採りません。

AppUpdateManager内のSHA-256は**署名証明書の識別**に使われます。APKファイル全体とGitHub digestの比較ではありません。単一署名は同じcurrent signer、またはcandidateの署名履歴にinstalled current signerが含まれる前方rotationを許容します。過去署名鍵だけへ戻す更新は拒否し、複数署名は正確な集合一致を要求します。Goldはcurrent signer集合の完全一致であり、前方rotationにも対応していません。この差は選択Policyだけでは解消できません。

`UpdateDownloadViewModel` はDownloadingを同期的に設定して二重実行を防ぎ、LiveData再配信後も `SingleUseValue.claim()` でinstaller起動を1回に限定します。permission待ちはVMに持ち、正しい設定復帰を消費した場合だけ自動downloadします。プロセス終了後の転送receiptや永続installer leaseはありません。Goldへ移す場合も、HandedOffをインストール成功へ読み替えません。

### 通知・cache・設定キー

自動確認はMainActivityの位置権限/Welcome処理を通った後です。Aboutはforceで確認します。MainActivityのbanner閉じ操作は「そのtagを次のversionまで無視」であり、単なる一時的なbanner非表示ではありません。比較無視テスト中でもdismiss判定は行われます。

SharedPreferences名は `github_release_updates`、確認できた更新用キーは以下です。

- `last_check_ms`：成功・失敗を区別しない最終試行時刻。
- `dismissed_update_tag`：大文字小文字を無視して一致する1件の抑止tag。
- `show_latest_release_for_testing`：IntDevだけ有効、intervalも迂回。
- `current_version_override_for_testing`：IntDevだけ有効、空ならBuildConfig.VERSION_NAME。

自動更新確認の通常ON/OFF設定や `cached_update` はこの経路では確認できません。MainのpendingUpdateInfo/banner情報はメモリー内で、Activityのmetadata確認OperationはonDestroyでcancelします。UpdateActivityはIntentのtag/channel/notes/URL/assetを復元し、notesと連結履歴はそれぞれ80,000文字で制限します。履歴foldは `intermediate_notes_expanded` としてBundleへ保存します。

### 既存テスト

更新関連4クラスに24件の `@Test` を確認しました（読み取りのみで、今回の成功件数ではありません）。

- GitHubReleaseCheckerTest：14件。version/rev、信頼URL、テスト選択、unsafe APK、universal/ABI/曖昧性。
- ReleaseChannelDetectorTest：3件。IntDev suffix、alias/優先度、開発UI対象channel。
- AppUpdateManagerTest：6件。code、同署名、前方rotation、旧鍵拒否、無関係鍵、複数署名。
- UpdateDownloadViewModelTest：1件。ダウンロード結果のclaimは1回だけ。

API通信、実APKの旧版→新版rotation、UpdateActivity再生成/Insetsのinstrumentation成功をこれらから推測しません。

## 導入適合性と計画（今回未実装）

**Goldの基盤・APIは利用できますが、既存Policyの指定だけで完全互換の移行はできません。**

1. **Beast用VersionPolicyをホスト側に定義する。** 実装済み比較関数とテストを根拠に、rev形式、RC、prerelease fallback、compact数字を保持する。公開APIのVersionPolicyで表現可能。既存3アプリのPolicyは変更しない。
2. **専用UpdatePolicyを構成する。** ListReleases(30,1)、AllPublished、allowNameAsTag=true、intermediateNotes=true、同値履歴なし、publicationTieBreak=false、developerSelection=HighestVersion、previewBypassesInterval=true、previewBypassesDismissal=falseを基準に、既存テストを移したcharacterization検証で確定する。Scheduler用ai-runtime filterはコピーしない。
3. **ABI対応ApkSelectionPolicyを作る。** 上記安全除外・universal・ABI順位・製品名・release優先・同順位曖昧性拒否をそのまま再現し、実端末ABI一覧を渡す。Gold公開APIで実装可能。maxBytesは旧制限と同じ250MiBへ設定する。
4. **ApkVersionBindingを確定する。** ローカルRC1ではtagのvを除いた値とversionNameが一致。公開配布のBeta/RC/IntDevを実APKで照合し、確認できた厳密な対応だけを許可する。比較関数の「同値」をAPK本人性の検証に流用しない。
5. **署名rotation方針を先に決める。** 同じcurrent keyで配布する範囲なら現Goldのまま導入可能。既存の前方rotation互換を必須とするなら、Goldのmetadata/認証済みlineage契約を別途設計・レビューする必要がある。偽のsigner情報を渡す、検証を迂回する、Goldの条件を緩める対応はしない。
6. **設定移行アダプターを置く。** dismissed tagをGoldのignoredTagsへ移行する。旧キーは1タグを置き換える方式、Goldは集合なので、将来のignore操作も同じ挙動にするかをホストadapterで明示する。開発者2設定はIntDev制約付きでRuntimeOptionsへ反映する。last_check_msをGoldの「成功時刻」として偽って移さない。失敗後も8h待つ旧仕様を維持するか、Goldの成功cache/HTTP retryに改めるかを別途決定する。既存metadata cacheの移行は不要で、新しいcatalogはGoldから取得する。
7. **native UIを保持したGoldのみの移行を先行できる。** コールバック/LiveDataへのadapterで既存Activityを残す。GoldのIO/cancelをviewModelScopeで管理し、必要ならLifecycle側で1回だけinstaller起動を消費する。設定復帰後の自動download、起動場所、banner-dismissを維持するかを明示して移す。
8. **Elief採用は別のUI移行。** Compose依存・compiler・ComposeView/ActivityとXMLテーマ→Compose MaterialThemeのbridgeが必要。新Eliefのversion表示・Chip・notes/history・操作は利用でき、字体・色・shapeはホストが供給できる。versionLabel/historyChannelLabel/backIcon、既存header/loadingIndicator/UpdateStringsで調整する。ただしgradient、縦のversionパネル、XML版と同一配置を既存slotだけで完全再現することはできない。native維持ならGoldのみが自然。
9. **SDKとManifestを確認する。** Gold AARの実metadataはminCompileSdk=37、minSdkは26。現BeastはminSdk条件を満たすがcompileSdk36→37とGradle互換検証が必要。targetSdkまで同時に上げる必要はない。Gold専用FileProviderを旧providerと共存させ、更新以外の利用箇所を確認してから旧経路を整理する。

## 根拠ファイル

BeastLocator：`GitHubReleaseChecker.kt`、`ReleaseChannel.kt`、`AppUpdateManager.kt`、`UpdateDownloadViewModel.kt`、`UpdateActivity.kt`、`AboutActivity.kt`、`MainActivity.kt`、`RandomDirectionApp.kt`（いずれもapp/src/main/java/jp/linkserver/beastlocator）、`app/src/main/res/layout/activity_update.xml`、`values/themes.xml`・`values-night/themes.xml`、`xml/update_file_paths.xml`、`app/build.gradle.kts`、上記4テスト。

ShutUp：`ui/UpdateOverviewContent.kt`、`ui/AppUpdateUi.kt`、`ReleaseChannel.kt`、`GitHubReleaseChecker.kt`、`update/AppUpdatePreferences.kt`・`AppUpdateRepository.kt`・`UpdateDownloadViewModel.kt`。

Scheduler：`ui/UpdateOverviewScreen.kt`・`AppUpdatePrompt.kt`、`update/GitHubReleaseChecker.kt`・`AppUpdatePreferences.kt`・`AppUpdateRepository.kt`。Glyph：`ui/UpdateOverviewScreen.kt`、`update/GitHubReleaseChecker.kt`・`AppUpdateRepository.kt`。

Gold：`VersionPolicy.kt`、`UpdatePolicy.kt`、`ReleaseSelector.kt`、`ApkPolicy.kt`、`ApkDownloader.kt`、`ApkInstaller.kt`、`internal/LegacyVersionRules.kt`。今回変更なし。
