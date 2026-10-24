# Goldy Demo（Phase 2-6）

既存 `:app` を実行可能なDemoへ拡張しています。GoldはCore、EliefはCompose UIです。更新処理と表示には両モジュールの公開APIを使用します。Demoだけが公開Pyrite SHA `508b1cf5131679fee7581acbc7ea55a365b6ff2c` に依存し、実際のPyriteThemeを適用します。Gold/EliefにPyriteの依存はありません。JitPackの配布対象はGold/Eliefだけで、Demo APKは含みません。

## 画面

- **Notifications**：通信・APKなしの通知Showcase。実際のGold state/storeで一時hide、tag無視、再表示、cache復元、失敗、取消を再現し、Eliefのbanner・badge・確認dialogから既存詳細画面へ進みます。詳細は [通知API](update-notifications.md)。

- **UI Showcase**：従来のオフラインfixture。更新あり／なし、既知・未知サイズのダウンロード、失敗、キャンセル、権限要求、APKなし、空ノート、引き渡し、確認中、信頼できないMarkdownを表示します。ダウンロードやインストールはシミュレーションです。
- **Live Demo**：公開の `Link2011-Act2/ShutUp`、`Link2011-Act2/NITTC_Scheduler`、`Link2011-Act2/GlyphBarty` を選び、Fetch releasesで通信します。Goldの `GitHubReleaseClient`、各アプリ用 `UpdatePolicies`、`ReleaseSelector` を使用します。比較用バージョンは初期値 `0.0.0-Release` の入力例で、他アプリのインストール済みバージョンではありません。タグ、チャンネル、prerelease、公開日時、ノート、asset名・サイズ・公開digestを閲覧できます。**他アプリのAPKを取得・インストールする操作はありません。** 表示したassetは未検証のメタデータです。
- **Update Demo**：自身の実際のpackage、versionName、versionCode、現在の署名証明書SHA-256、インストール許可を表示します。Check Demo updateでGoldyの配布を確認します。Goldの `Goldy`、`ApkDownloader`、`AndroidApkInspector`、`ApkInstaller` とEliefの画面を使用します。
- **Style reference**：実際のPyriteのカード・操作コンポーネントを同じテーマ内で比較します。更新処理は開始しません。

テーマ操作は全画面共通です。Styleから **Material 3 Expressive（初回の既定値）／Nothing風／通常Material 3** を排他的に選択します。DarkでLight/Dark、DynamicでAPI31以降のDynamic Color、Custom fontで等幅フォント例を切り替えます。Nothing風ではDynamicをOFF/無効表示にし、他スタイルへ戻ると以前の設定を復元します。Nothing専有フォントは同梱しません。

スタイル・明暗・Dynamic Color・フォントはDemo専用設定としてプロセス終了後も保持します。選択画面と履歴・スクロールの表示状態はsaveable、通信・転送とLiveのセッションキャッシュはViewModelでActivity再生成を越えて保持します。設定行は横スクロール、スタイル選択ダイアログは必要に応じて縦スクロールできます。詳細は[スタイル選択の検証](demo-style-selection.md)を参照してください。

## 通信・キャッシュ

起動時のネットワーク通信、バックグラウンド更新、PATの埋め込みはありません。Liveの取得成功・通信中・失敗・キャンセル・HTTPアクセス制限と、利用可能な再試行日時を表示します。失敗しても直前の成功結果を破棄しません。Liveのキャッシュはメモリー内で、プロセス終了後は再取得が必要です。Goldクライアントが使うETag/304の有無は公開APIでは区別できないため、「必ずネットワークから全件取得した」とは表示しません。

各アプリの既存feed・filterを使用します。Glyphはlatestのみ、ShutUp／Schedulerは既存の取得上限までです。`complete=false` は不完全な履歴として表示し、SchedulerのAI runtime配布は比較・閲覧候補から除外します。Stable releases onlyはバージョン比較のチャンネル条件です。Browse fetched releasesは取得済み履歴の閲覧であり、インストール候補一覧ではありません。

UpdateのメタデータはGoldの永続キャッシュを使用します。ダウンロード開始後は対象releaseを固定し、破棄するまでは別releaseへ切り替えません。失敗・キャンセル後のretryも同じreleaseを使用します。ネットワーク中の連打は新しい処理を開始しません。

## 自己更新配布の設定

現在の配布先は `Link2011-Act2/Goldy` です。このPhaseでは鍵、タグ、GitHub Release、配布APKを作成・公開しません。公開Releaseがなければ、確認結果は **Not configured** です。

将来、配布を準備する場合の規約：

1. applicationIdは既存の `jp.linkserver.projectgoldy` を維持します。
2. versionNameは `0.2.0-Release` など、`<数字>.<数字>.<数字>-Release` です。
3. tagはversionNameに`v`を付けた `v0.2.0-Release` と完全一致させます。
4. `goldy-demo-` で始まる `.apk` を1つだけ付けます。複数あると曖昧なため選択しません。
5. 配布先APKは、インストール済みDemoと**同じ現在の署名証明書**、同じpackage、より大きいversionCodeでビルドします。署名履歴の共通部分だけでは許可しません。実機デモでは必ずversionCodeを増やしてください。
6. APKサイズ・digest・HTTPS/CDN redirect・署名・package・versionCode・tagとversionNameの照合はGoldに任せます。メタデータの表示だけではAPKを信頼しません。

バージョンはGradleプロパティで指定できます。通常の初期値はcode `1`／name `0.1.0-Release` です。

```powershell
./gradlew.bat :app:assembleRelease `
  -PgoldyDemoVersionCode=2 -PgoldyDemoVersionName=0.2.0-Release
```

Release出力は署名設定を追加していないため**未署名**です。公開用秘密鍵、password、keystoreをリポジトリに保存しないでください。DebugとReleaseの署名が異なる場合、相互更新できません。Demoに追加した権限は、自己更新に必要な `REQUEST_INSTALL_PACKAGES` のみです。INTERNETと限定FileProviderはGoldからの既存Manifestマージを使用します。

## 配布せずに行える安全なローカル確認

専用エミュレーターで実施してください。通常使用している端末や既存3アプリは対象にしません。

1. **既存の**ローカルdebug keystoreがあることを確認します。新規の署名鍵はこの手順では作りません。未存在ならビルドを進めず、鍵の準備を別途判断してください。
2. 同じ既存debug keyを使用し、次の旧版／新版を順番にビルドして、それぞれの出力APKをプロジェクトの `build/phase-2-6/local-update/` などの無視対象へコピーします。

```powershell
./gradlew.bat :app:assembleDebug `
  -PgoldyDemoVersionCode=1 -PgoldyDemoVersionName=0.1.0-Release
# app/build/outputs/apk/debug/app-debug.apk を old.apk として退避
./gradlew.bat :app:assembleDebug `
  -PgoldyDemoVersionCode=2 -PgoldyDemoVersionName=0.2.0-Release
# 同出力を new.apk として退避
```

3. SDKのapksignerで両APKの証明書SHA-256が同一であること、apkanalyzerでpackageとversionCode/nameが上記の値であることを確認します。異なる証明書やpackageなら停止します。
4. `adb -s <専用emulator> install old.apk` で旧版を起動し、Update Demoの実際のidentityを確認します。
5. OSの更新互換性だけをローカル確認する場合は `adb -s <専用emulator> install -r new.apk` を実行し、新版のidentityでcode `2` を確認します。これは**Goldのダウンロードやインストーラー経路の成功とは別の確認**です。
6. ローカルAPKを任意パスからVerifiedApkとして偽造する抜け道は設けていません。Goldの実ダウンロード→検証→インストーラーを通す自己更新は、上記規約の実配布を別途承認・準備してから実演します。今回はその経路の実配布による成功は未検証です。

## インストーラーとファイル寿命

- 未許可なら「Allow installation」またはInstallation source settingsを開き、戻ってから明示的に再試行します。復帰時には許可表示だけを更新し、インストールを自動開始しません。
- prepareが成功するとGoldがimmutableなコピーとInstallRequestを保持します。元のverified downloadは削除し、引き渡し後のコピーは保持します。
- Activity再生成で転送・verified receiptを維持します。プロセス終了前にprepare済みならpendingRequestsを復元し、**Retry installer explicitly** で再検証して起動します。prepare前のreceiptは復元せず、再ダウンロードが必要です。
- 旧プロセスの未共有download cacheはOSのcache削除対象になり得ます。Demo終了でViewModelが破棄されたとき、保持中の未共有ソースは削除します。永続InstallRequestは削除しません。
- 引き渡し、Activity復帰、アプリ内の「完了」操作からインストール成功を推測しません。新版の起動後に実際のidentityを確認してください。
- **Release file after installer finishes…** は利用者がインストーラーと全URI readerの終了を確認した場合だけ使用します。確認ダイアログで明示してからreleaseします。onResume／onCleared／onDispose／タイマーによる自動解放はありません。

## 検証コマンド

JDK21、既存のGradle wrapper、Android SDK 37を使用します。

```powershell
./gradlew.bat :gold:testDebugUnitTest :elief:testDebugUnitTest :app:testDebugUnitTest `
  :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest `
  :app:lintDebug :app:lintRelease

./verification/run-demo-ui.ps1 -Serial emulator-5556 -Label api36-320dp-font180 `
  -AndroidSdk <Android-SDK> -FontScale 1.8 -WidthDp 320

# 実通信だけを明示的に実行（未認証のGitHub APIを少数回使用）
./verification/run-demo-ui.ps1 -Serial emulator-5556 -Label api36-live `
  -AndroidSdk <Android-SDK> -Live
```

既存のCompose UIテストを保持しています。実APIテストは通常のテスト一括実行ではskipし、`-e goldyLive true` を指定した場合だけ通信します。ログと画面キャプチャは `build/phase-2-6/` に保存します。R8はReleaseの全packageに適用します。テスト用にRelease APKを起動する場合は既存のローカルdebug keyでコピーに署名し、公開用APKとは区別してください。
