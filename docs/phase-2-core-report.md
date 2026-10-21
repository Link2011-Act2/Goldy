# Project Goldy — Phase 2 初回 core 実装報告

日付: 2026-10-21（JST）  
対象: 承認された初回の6項目。**メタデータ更新機構まで実装済み**です。APK転送・検証・インストール・Composeは含みません。

## 1. 今回の成果

- Androidライブラリ `gold` を追加。minSdk 26、compileSdk 37、JVM 11、Kotlin language/API 2.2。Gradle/AGPは既存Goldyの9.6.0/9.4.1を維持。
- GitHub Releases一覧/latestの取得、JSON解析、draft除外、明示的なname fallback、Release/assetメタデータを実装。
- 一覧のページネーション・取得上限、不完全な一覧の識別、メモリー内ETag/304、HTTP・レート制限・取消の扱いを実装。
- 3アプリ由来の比較・チャンネル・候補・中間ノート・開発者プレビュー方針を、明示的な互換プリセットとして提供。
- StateFlow、成功キャッシュ、8時間間隔、実版/code/方針・比較用版による失効、通知の一時非表示、版無視、無視解除を実装。
- SharedPreferencesの標準保存と差し替え用Store、JVM用InMemory Storeを実装。
- 独立Gradle consumerがローカルMavenリポジトリから**今回のAARそのもの**を解決し、コンパイル・テスト・lint・release variantのビルドを実施。

Pyrite、Compose、Markwon、Room、WorkManager、AndroidX Lifecycleへの依存はありません。実行時の公開依存はKotlin標準ライブラリとCoroutinesです。JSONはAndroid標準org.jsonを使い、JVMテストだけ実体ライブラリを追加しています。

変更した既存の追跡ファイルはsettings.gradle.kts、ルートbuild.gradle.kts、gradle/libs.versions.tomlです。既存のアプリ雛形・README・Phase 1文書・.gitignoreの既存差分は変更していません。

## 2. 公開API

| API | 用途 |
| --- | --- |
| Goldy.create(context, config, store?) | 自アプリの実PackageInfoから現在版を読み、標準SharedPreferencesまたは指定Storeで生成。生成時に通信しない |
| Goldy.state: StateFlow<GoldyState> | availableUpdate、notificationUpdate、showBanner、checking、成功時刻、cache由来、最後の確認結果 |
| suspend check(Automatic / Manual) | 自動確認の設定/間隔、手動の間隔迂回、server backoff、現在方針による候補選択 |
| suspend restoreCachedState() | 通信せず保存内容を読み、現方針で再評価 |
| suspend updateOptions(RuntimeOptions) | 自動確認/通知の有効状態と開発者設定を更新。通信は開始しない |
| suspend hideBanner() | 一時的にバナーを閉じる。通知対象・手動詳細は残す |
| suspend ignoreVersion(tag) / resetIgnoredVersions() | repositoryごとの永続的な版無視と解除。手動詳細は残す |
| GitHubReleaseClient(config).fetch() | Android Contextなしでメタデータ取得だけを使う |
| GitHubReleaseParser.parse(...) | GitHubのJSONから型付きモデルを作る。任意のJSONObjectを公開状態に持ち込まない |
| VersionPolicy / VersionPolicies | 比較と表示チャンネル識別。ShutUp / NittcScheduler / GlyphBarty、または独自実装 |
| UpdatePolicy / UpdatePolicies | 比較、チャンネル受入、feed、Release除外、中間ノート、開発者動作を組み合わせる |
| ReleaseSelector.select(...) | 取得済みcatalogから、現在版・方針・オプションに応じて純粋に候補を選ぶ |
| UpdateStateStore | suspend read/writeの保存境界。旧設定との連携等が必要な場合だけ差し替える |
| InMemoryUpdateStateStore | JVMでの利用・検証用の保存実装 |

主要な入口: [Goldy.kt](../gold/src/main/java/jp/linkserver/goldy/Goldy.kt)、[UpdatePolicy.kt](../gold/src/main/java/jp/linkserver/goldy/UpdatePolicy.kt)、[VersionPolicy.kt](../gold/src/main/java/jp/linkserver/goldy/VersionPolicy.kt)、[Models.kt](../gold/src/main/java/jp/linkserver/goldy/Models.kt)。

```kotlin
// ViewModel等で保持する例。既存3アプリへの追加は行っていません。
private val goldy = Goldy.create(
    context = application,
    config = GoldyConfig(
        repository = GitHubRepository("Link2011-Act2", "ShutUp"),
        policy = UpdatePolicies.shutUp()
    )
)
val updates = goldy.state

fun checkAutomatically() {
    viewModelScope.launch { goldy.check() }
}
fun checkManually() {
    viewModelScope.launch { goldy.check(CheckTrigger.Manual) }
}
```

Schedulerの場合はrepositoryをNITTC_Scheduler、policyをUpdatePolicies.nittcScheduler()にします。Glyph Bartyの場合はrepositoryをGlyphBarty、policyをUpdatePolicies.glyphBarty()とし、RuntimeOptions.automaticChecksEnabledには既存設定の値（既定false）を渡します。自動確認スイッチの保存はホストが引き続き所有します。

```kotlin
val glyphConfig = GoldyConfig(
    repository = GitHubRepository("Link2011-Act2", "GlyphBarty"),
    policy = UpdatePolicies.glyphBarty(),
    initialOptions = RuntimeOptions(automaticChecksEnabled = false)
)
```

Phase 1のAPIは設計用の仮宣言でした。今回の実APIでは、不要なScope保持を避け、確認処理を呼出し側CoroutineScopeで所有する構成にしました。ダウンロード関連型・インストール関連型・canonicalIdentityによるAPK版照合はまだ公開しません。互換プリセットは3本で使いやすいようcoreに明示的なopt-inとして配置していますが、配信repositoryを内部で固定してはいません。

## 3. 既存動作の互換性

| 方針 | 維持した主な挙動 |
| --- | --- |
| ShutUp | 数値列のLong比較、Release/Stable別名、suffix自然順、Beta番号/_rev、metadataを順序に使わない。現在チャンネルに応じた受入。取得100件×1ページ。比較無視は公開日時順。間隔無視は独立 |
| Scheduler | scheduler_/nittc_、compact形式、数値→チャンネル→単純末尾番号。_revは比較しない。RCがBetaより低い旧順位も維持。全チャンネル、30件×1ページ、ai-runtime-*常時除外。プレビューは比較上の最大で、間隔も迂回 |
| Glyph Barty | glyphbarty_/compact、数値→チャンネル。同チャンネルのBeta番号/_revを比較しない。latest取得、中間ノートなし。比較無視と間隔無視は独立。プレビュー時の通知無視迂回を維持 |

ShutUpには、候補と比較上同じ版の別Releaseも中間ノートへ残す挙動があります。Schedulerは厳密に候補より小さい版だけを残します。参照実装との候補・ノート照合でこの差も固定しました。互換プリセットの同順位候補は公開日時で並べ替えず、元の応答順を維持します。

標準方針はShutUp由来の比較＋InstalledTrack、一覧100件×最大5ページです。互換プリセットは元アプリの取得範囲を維持します。上限時にLinkのnextがある場合はIncompleteを返し、取得範囲全体を確認した「最新」とは区別します。

### Characterization testの基準

- ShutUp HEAD: `1a18497cfbe62eac20ecf3ba8264cb8daf163fac`
- Scheduler HEAD: `9fad0402e268902bbfbea485ca216218c17afdf1`
- GlyphVisualizer HEAD: `d0adb9c7e98bd888110da7edcd1997ebb29ca4db`

テスト内に元の純粋な比較関数を固定した参照実装を配置しました。ShutUp/Schedulerについては候補と中間ノートの選択本体も固定しています。元のライセンス・著作権を保持し、元ファイルのSHA-256を [参照ハッシュ](../gold/src/test/resources/legacy-source-hashes.txt) に記録しています。

43種類の版文字列についてShutUp 1,849組、Scheduler 7,396組（prereleaseフラグの4組合せ）、Glyph 1,849組、合計**11,094組の比較**と129件のチャンネル照合を実施。候補/中間ノートは2アプリ合計140シナリオを参照実装と照合しています。

空文字、nightly、未知suffix、オーバーフローも含みます。旧Scheduler/Glyphの寛容な数字抽出や比較上の同順位も、そのまま観測して固定しています。**これらは更新候補の順序付け専用であり、APKの版同一性や安全性を証明する関数ではありません。**

## 4. 安全性・状態処理

- repository入力をURI/owner/repoとして検査し、任意文字列中のgithub.com検索を廃止。
- Release URLは対象repositoryのreleases/tag/配下、asset URLも対象repositoryの既知の配信経路だけをメタデータとして受け入れる。
- APIへのリダイレクトは自動追従しない。paginationのnextは同一API/repository/一覧pathとpage/per_pageに限定。
- JSON本文・ノート・catalog全体に上限を設け、不正/過大な応答を空の成功としない。
- ETagは検査済み本文と組で保持し、メモリー使用量も制限。304で元本文がなければ失敗。
- 403/429のRetry-After/resetを保持し、Manualでもserver backoffを迂回しない。
- 取消はCancellationExceptionを再送出し、成功時刻・cacheを変更しない。
- cacheはschema・repository・policy・実版/name/code・比較用版・preview状態に対応付ける。復元時にもURLとRelease除外・チャンネルを再評価。
- cache JSON破損で無視tagや無関係設定を消さない。SharedPreferences.commit失敗では新しい成功状態を公開しない。
- ネットワーク処理中の通知無視やオプション変更を、完了時の古い結果で巻き戻さない。
- 実際のインストール済み版からIntDevを判定し、偽の比較用版を開発者設定の解禁根拠にしない。
- 一時非表示／版無視／通知OFFを分け、availableUpdateによる手動詳細を残す。

SharedPreferencesへの実機I/OとPackageManagerの生成経路はまだinstrumented確認していません。保存境界はJVM上の偽物で、JSON・再読込・破損・独立キー保持・commit失敗を検証しています。ネットワークのJVM試験は差し替えた内部transportを使用し、GitHubへのライブリクエストは行っていません。

## 5. 検証結果

| 検証 | 最終結果 |
| --- | --- |
| core JVMユニットテスト | **49件成功、失敗0、skip 0** |
| core lintDebug | **指摘0** |
| core release AAR生成・ローカル配置 | 成功 |
| 独立consumer JVMテスト | **3件成功、失敗0、skip 0** |
| 独立consumer lintDebug | **指摘0** |
| 独立consumer assembleRelease | 成功 |
| verifyCoreDependencies | 今回の生成AARと解決AARのSHA-256一致、Coroutines推移依存、Pyrite/Compose/Markwon非依存を確認 |
| 既存作業ツリー照合 | 3アプリのHEAD/status/diff/index/未追跡内容一致、Goldyの既存差分とPhase 1文書も内容一致 |
| 実機・APK転送/インストール | 今回の範囲外につき未実施 |

結果の内訳と保護照合は [機械可読記録](../verification/phase-2-core-results.json) に保存しています。

最終AARは171,599 bytes、SHA-256は `da12838e5ec7de210f7c96d28330d7abcb2467af7395fc64f78ee19d0ac147d0` です。これはAAR単体のサイズで、依存を含むアプリの増加サイズではありません。

ビルドはJDK 25を使い、既存キャッシュを利用するofflineモードで実施しました。初回の通常サンドボックスではGradleユーザーキャッシュのlock書込みが拒否されたため、承認済みの権限で再実行しています。途中で検出した「不正オブジェクトを空一覧として扱うケース」とconsumerのGradle DSL名前衝突は修正し、上記の最終チェックを成功させています。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio1\jbr'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

.\gradlew.bat :gold:testDebugUnitTest :gold:lintDebug `
  :gold:publishReleasePublicationToConsumerRepository --offline --console=plain

.\gradlew.bat -p verification/consumer :consumer:testDebugUnitTest `
  :consumer:lintDebug :consumer:assembleRelease :consumer:verifyCoreDependencies `
  --offline --console=plain
```

consumerはGoldyとは別のsettings.gradle.ktsを持ち、project依存・includeBuild・本体sourceの直読みを使用していません。Goldy/build/consumer-repositoryに置いたAAR/POM/Gradle metadataを座標で解決しています。上記のpublishタスクは**Goldy内への一時的なローカル配置**であり、Maven Central/GitHub等への公開やGitHub Release作成ではありません。

## 6. 残タスクと次段階

今回の6項目は初回の基礎として完了しています。次は以下を別段階で扱います。

1. **Releaseタグと実配布APKのversionName/codeの対応調査・仕様確定。** 現在の比較関数で0になることを、同じAPK版とみなさない。
2. APK選択・ダウンロード・進捗・確実な取消・.part清掃。
3. digest、完全性、package、署名者集合、versionCode等の検証。
4. 許可・専用Provider・システムインストーラー連携。
5. elief、Markdown、ホストテーマ/文言/Backの連携。
6. 旧3アプリの設定/cacheスキーマを引き継ぐ移行処理と実アプリへの導入。**既存作業ツリー変更は未承認のため実施しない。**
7. Android実機でのfactory/SharedPreferences確認、実HTTP接続の検証、実アプリでの依存サイズ/R8等の検証。

ETagページcacheは今回インスタンス内だけです。Release catalog自体は永続化していますが、raw HTTPページをプロセス間で再利用する永続ETag cacheは未実装です。v0.1.0全体の完成・外部公開を宣言する段階ではありません。

### APK版照合の仕様確定で確認する点

- ShutUpはVERSION_NAME環境変数と別のBUILD_NUMBERを持つ。Releaseのv接頭辞、Release/Stable別名、suffixと+metadataの扱いを実APKで確認する。
- Schedulerのscheduler_/nittc_やcompactは**候補比較用の旧表記**。APK versionNameがどの表記かは、配布済みAPKのmanifestで確認する。
- Glyph Bartyのglyphbarty_、_rev、チャンネル名も、比較で番号を無視する旧動作とAPKの版同一性を分離する。
- tag、asset名、実versionName、実versionCode、期待される対応を検査用fixtureにし、正当例と別Release/別版の拒否例を揃える。
- 根拠がない対応を任意の文字除去や「比較結果0」で許可しない。対応不能は明示的に扱い、package/署名等の検査を解除する設定で逃がさない。

## 7. 変更保護

ShutUp、Scheduler、GlyphVisualizerの既存作業ツリーは開始時と一致しています。Pyriteの作業ツリーは操作していません。Goldyの既存.gitignore差分と削除状態、既存の未追跡Phase 1文書も内容を保持しています。

README編集、既存アプリの移行、コミット、Push、タグ、外部配信、GitHub Release作成は実施していません。
