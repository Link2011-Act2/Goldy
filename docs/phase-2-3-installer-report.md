# Project Goldy Phase 2-3 実装報告

2026-10-22。`:gold`へAndroidシステムインストーラーへの安全な引き渡しを追加しました。公開API・利用例・Manifest宣言・ファイル寿命は[installer-api.md](installer-api.md)、数値とログ参照は[検証結果](../verification/phase-2-3-results.json)にまとめています。

## 実装

- ApkInstaller.create / permissionStatus / unknownSourcesSettingsIntent / prepare / launch / pendingRequests / releaseを追加しました。ホストがActivityの起動コールバックを渡す構造で、GoldyはstartActivityを呼びません。
- prepareとlaunchで、ファイル全体のSHA-256、サイズ、APK metadata、現在のインストール済みpackageName・署名者集合・versionCodeを再検査します。VerifiedApkに記録されたmetadata全体とも一致を要求します。元のReleaseとの対応は、ダウンロード時に確認した同じ全バイト・metadataを固定することで維持します。
- ApkVerifierの既存条件は内部の共通関数へ抽出しただけです。比較関数による版同一視、未知の命名変換、署名履歴の一部一致、code下限の緩和は行っていません。
- 読み取り専用の共有用コピーをfilesDir/goldy-apks/installへ作り、記録は共有対象外の別ディレクトリへ保存します。元のcacheファイルを消しても、インストーラー用コピーは残ります。
- GoldyFileProviderは専用クラスで、authorityは `${applicationId}.goldy.fileprovider`、exported=false、grantUriPermissions=trueです。公開pathsは専用installディレクトリだけです。openFile時にもハッシュ・APK・現在のインストール済み情報を検査し、書き込み・削除要求を拒否します。
- Intentはcontent URIとClipDataに同じAPKを設定し、一時読み取りflagだけを付けます。query可能なシステムアプリのActivityへ明示的に向け、任意の第三者handlerや全候補への一括grantは使いません。
- REQUEST_INSTALL_PACKAGESはホストの明示宣言が必要です。AARでは自動宣言しません。canRequestPackageInstallsを確認し、許可がなければ自分のpackageを対象とする許可設定Intentを返します。
- 起動成功はInstallLaunchResult.HandedOffです。Installedという状態や、インストール成功の推測は提供しません。権限拒否、宣言不足、Provider不備、不在、削除・改変、検証失敗、起動例外を分けて扱います。
- 起動直後やActivity結果だけでは共有APKを削除しません。使用終了を確認したホストがreleaseします。プロセス終了後もpendingRequestsから復元できます。コピー途中の.partは失敗・キャンセル時、または次回prepare時に清掃します。

## 品質確認

| 確認 | 結果 |
| --- | --- |
| gold JVM | 103件成功、失敗0。既存90件を内容を変えず維持し、再検証・保存・清掃の13件を追加 |
| 独立Gradle consumer | 4件成功、Release consumer AAR、公開gold AARとのハッシュ一致を確認 |
| gold Debug / Release lint | エラー0、各警告2（UseKtx: Uri.parseと既存SharedPreferences.editへの拡張関数利用提案） |
| consumer lint | エラー・警告0 |
| Release AAR / sources JAR | 成功。Goldy内ローカルMavenへ出力し、外部公開無し |
| Manifestマージ | library test APKと、公開AARを参照する独立アプリで、既存host FileProviderとの共存を確認 |
| authority | host用とGoldy用の2つが別authorityで解決。Goldy Providerは非公開・URI grant可 |
| 権限opt-in | REQUEST_INSTALL_PACKAGESを持たない独立アプリでMissingManifestPermissionを確認 |

lint警告を避けるために既存メタデータ保存APIを書き換えることはしていません。新しいAPIレベルの誤使用を示す警告・エラーはありません。

## Android instrumented検証

独立UIDのreceiverアプリと、署名付き専用テストAPKを使用しました。テストはSDKのInstrumentation上でassertを実行する専用runnerで、am instrumentから起動しています。connectedDebugAndroidTestのJUnit件数としては数えていません。記録された成功は36検証グループであり、JVM 103件とは別です。

| 対象 | API28 | API36 |
| --- | --- | --- |
| PackageManagerでの単独/複数署名取得、別鍵・不完全署名集合拒否 | 成功 | 成功 |
| 改ざん署名APK拒否 | 成功 | 成功 |
| 元ファイル削除後の共有コピー読み取り | 成功 | 成功 |
| 別UIDでgrantなし拒否、読み取りgrantのみ受理、書き込み拒否 | 成功 | 成功 |
| host FileProvider共存・hostファイルの範囲外拒否 | 成功 | 成功 |
| prepare後の改変をlaunch / provider openで拒否 | 成功 | 成功 |
| 元APK消失拒否 | 成功 | 成功 |
| 起動例外分類、失敗時も共有ファイルを保持 | 成功 | 成功 |
| 不明なアプリ許可の拒否判定・設定Activityへの案内 | 成功 | 成功 |
| 専用テストAPKのシステムインストーラーActivity起動 | 成功 | 成功 |
| インストーラーを閉じた後の明示解放・別プロセスでの記録復元 | 成功 | 成功 |
| 許可Manifest宣言不足 | 成功 | 成功 |
| 実際にインストーラーを無効化して不在を検出・後片付け | 未実施 | 成功 |
| Providerを自分のUIDで一時無効化して設定不備を拒否 | 未実施 | 成功 |

API36のProvider無効化試験は、最初にシェルのcomponent変更制約でセットアップに失敗しました。その後、テストアプリ自身が標準のPackageManager APIで無効化・復元する方式に直し、成功しました。rootやShizukuは使用していません。

インストーラーへ渡したのは専用署名付きテストAPKです。公式アプリのAPKはインストールしていません。インストール確定操作は行わず、起動とインストール成功を区別しました。起動したActivityはdumpsysの記録にも保存しています。使用したAVDはread-only / no-snapshotで終了し、既存AVD状態は保存変更していません。

## 実ネットワーク転送

API28とAPI36の両方で、製品のApkDownloader.createとAndroid HttpURLConnectionから[ShutUp公式ReleaseのAPK](https://github.com/Link2011-Act2/ShutUp/releases/tag/v1.1.0-Release)を実際に取得しました。transportやTLS検証、PackageManagerはテスト用の模擬実装へ置き換えていません。

- HTTPSとGoldyのCDN redirect検証を通り、2,878,925 bytesを受信しました。
- 進捗通知はAPI28で391回、API36で390回、最終bytes/totalは配布サイズと一致しました。
- GitHub assetのsizeとSHA-256の照合を通りました。固定した期待digestは4f567b5cee03217a9c3c5efc11031edf5b6abaff29754022fb74f546e6e21f6bです。
- 実PackageManagerで解析し、その後テストアプリとは異なるpackageNameとしてPackageMismatchで安全に拒否しました。公式アプリをテスト用に信頼扱いしたり、インストール済みpackage/署名検証を迂回したりしていません。
- 実際の転送の進捗callbackからCoroutineキャンセルを発生させ、.partの清掃を確認しました。接続待ち・ブロック中readからの切断は既存JVM異常系テストでも維持しています。
- 存在しない同repositoryのasset URLを実際に要求し、HTTP404を受け取って安全に失敗しました。
- API36の初回転送ではSocketTimeoutExceptionが発生し、Networkとして分類されました。同じ検証条件での再試行が成功しています。ネットワーク依存試験は外部配信・接続状況で一時失敗し得ます。署名やURL条件を緩めて成功させていません。

Androidでの名前解決は、専用AVDの起動引数で公開DNSを指定しました。ホストOSのDNS設定は変更せず、TLSのホスト名・証明書検証は維持しました。

## 追加依存・サイズ

直接追加した依存はimplementationのandroidx.core:core:1.19.1です。FileProviderを使用するためです。推移依存は[依存グラフ](../verification/phase-2-3-runtime-dependencies.txt)に記録しています。

主な追加推移依存にはcore-viewtree、annotation、collection、lifecycle-runtime/common、arch.core、tracing、versionedparcelable、concurrent-futures、listenablefuture、profileinstaller、startup-runtime、coroutines-androidがあります。Pyrite、Compose、Markwonはありません。AndroidXのART profile最適化用startup Provider/receiverは依存元の標準Manifestとしてマージされます。Goldy独自のService、Worker、更新用バックグラウンド常駐処理は追加していません。

| AAR | サイズ |
| --- | ---: |
| Phase 2-2 | 223,348 bytes |
| Phase 2-3 gold-release.aar | 280,398 bytes |
| 増加 | 57,050 bytes（約55.7 KiB） |

AARのサイズに推移依存自体は内包されていません。SHA-256はf7c384357bac4a4d1b6f4a66f7e2273801981e31e8188be699584767be7d0983です。独立consumerが解決した公開AARとの一致を確認しました。

## 再検証

JDK25 / Gradle9.6.0 / AGP9.4.1 / compileSdk37で確認しました。

```powershell
.\gradlew.bat :gold:testDebugUnitTest :gold:assembleDebugAndroidTest :gold:lintDebug :gold:lintRelease :gold:publishReleasePublicationToConsumerRepository --offline --console=plain
.\gradlew.bat -p verification/consumer :consumer:testDebugUnitTest :consumer:lintDebug :consumer:assembleRelease :consumer:verifyCoreDependencies --offline --console=plain
.\gradlew.bat -p verification/android-probe verifyInstallerManifest --offline --console=plain
# 初期状態の使い捨てエミュレーターを起動し、JAVA_HOMEを設定してから:
.\verification\run-android-instrumentation.ps1 -Serial emulator-5580
```

runnerは専用テストAPKだけを導入し、署名を変えたテストfixtureと破損fixtureをbuild内へ作ります。API26イメージの取得やAVDの起動は自動で行いません。既に未終了のインストーラー要求がある環境では、まず使用終了を確認し、既存要求を安全に片付けてください。外部ネットワークを使わない再現には-SkipNetworkを指定できます。

## 未検証・制限

- API26/27、API29〜35の実行、実機・OEM固有インストーラーは未検証です。minSdk26とAPI分岐は維持し、legacy署名フラグを含むJVMテストとlintで確認しています。
- API26はローカルにイメージが無く、Google公式配布メタデータを確認して取得を試みましたが、帯域制約で未完了です。途中ファイルは完成イメージと区別してbuild下に保持し、既存SDKへ導入していません。
- 最終的なインストール成功は未検証です。HandedOffはActivity起動の受理のみで、成功状態ではありません。実インストールはホストがPackageManager等で別途確認します。
- OEMが独自の非システムhandlerしか提供しない場合はNoInstallerとして拒否します。
- 同期PackageManager解析は途中で即座に中断できません。Coroutineキャンセルは解析後にも再確認し、未引き渡しファイルを清掃します。
- 未解放APKは、インストーラーがまだ読む可能性を優先して自動期限削除しません。ホストは使用終了を確認してreleaseする必要があります。
- 複数プロセスの同時操作、署名ローテーションの自動受理、root/Shizuku/サイレントインストールは対象外です。

## 保護した作業

作業開始時のGoldy HEADは176d6946fee53b0fe2fd0e89d5196d39a6b2f357で、作業ツリーはcleanでした。他3アプリは既存の未コミット・未追跡ファイルを含めてハッシュとHEAD・statusを照合し、一切変更していません。Pyriteには操作していません。Goldyのメタデータ取得・キャッシュ・通知状態・互換テストは維持しました。READMEは変更していません。

`:gold`を維持し、将来のCompose名`:elief`も変更していません。eliefの実装、コミット、Push、外部Maven publish、GitHub Releaseは行っていません。
