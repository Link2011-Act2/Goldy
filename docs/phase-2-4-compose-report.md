# Project Goldy Phase 2-4 実装報告

2026-10-22。任意導入のCompose UIモジュール `:elief` と独立fixtureサンプルを追加しました。公開API・導入方法・ファイル寿命は [compose-api.md](compose-api.md)、数値は [検証結果](../verification/phase-2-4-results.json) にまとめています。

## 実装と公開API

| API | 役割 |
|---|---|
| UpdateOverviewScreen | バージョン、チャンネル、ノート、中間履歴、転送・インストール要求の状態を表示 |
| ReleaseNotes | ホストのTypographyを使うMarkdown表示。単独利用可能 |
| UpdateUiState | UpdateCandidate、チャンネル、APK有無、確認中、転送状態、ホストのメッセージ |
| GoldyState.toUpdateUiState | 同じVersionPolicy / ApkSelectionPolicyを使って表示モデルへ変換 |
| UpdateTransferState | Idle / Downloading / Verifying / Ready / PreparingInstall / PermissionRequired / HandedOff / Cancelled / Failed |
| UpdateAction | Download / CancelDownload / Install / OpenInstallPermissionSettings |
| UpdateStrings | タイトル、操作、状態・空表示、履歴の文言 |

パッケージは `jp.linkserver.goldy.compose` です。UpdateStrings、channelLabel、loadingIndicator、headerを主なカスタマイズ点に絞りました。MaterialThemeの色・Typography・Shapesを利用し、独自テーマやPyrite依存はありません。MarkdownもComposeのFontFamilyをAndroid Typefaceへ解決します。

更新処理とCoroutineScope、VerifiedApk、InstallRequestはホストが保持します。UIはonBack / onOpenUrl / onActionを呼ぶだけでActivityを起動しません。処理の二重実行はホストがMain上で同期的に獲得するガードで防止します。Composable破棄時のrelease、成功とみなす状態、バックグラウンドService / WorkManagerは追加していません。

中間履歴の展開とスクロール位置はRelease IDごとにsaveableです。長文・大きな文字で操作部が画面外になる場合も、単一の縦スクロールでアクセスできます。固定高の下部操作領域は設けていません。Content-Length不明は不定進捗として表示します。

## Markdown

3アプリと同じMarkwon core + ext-tablesを使用します。見出し、箇条書き、リンク、太字、コード、引用、表、空ノート、中間Releaseのノートに対応します。

HTMLノードは無視し、画像は代替テキストだけを描画します。HTML/imageプラグイン、WebView、外部画像読み込み、暗黙のURL起動はありません。リンクはホスト付き・資格情報なしの絶対HTTP(S)だけをホストへ渡し、javascript / intent / file / contentなどは無効にします。ホスト側の追加URL方針は引き続き必要です。

## 依存と成果物

| 項目 | 構成 |
|---|---|
| Elief | minSdk 26 / compileSdk 37 / Kotlin JVM 11・言語2.2 |
| ビルド | 既存AGP 9.4.1 / Gradle 9.6.0 / Compose plugin 2.2.10 |
| 公開依存 | gold、Compose BOM 2026.02.01、Material3 |
| UI基準 | Compose UI / Foundation 1.10.4、安定版Material3 1.4.0 |
| Markdown | Markwon core / ext-tables 4.6.2。推移依存commonmark / commonmark-ext-gfm-tables 0.13.0 |
| サンプルのみ | Material3 1.5.0-beta01（Glyph Bartyの既存指定と同じ）、ViewModel KTX 2.11.0 |
| Release Elief AAR | **58,519 bytes**。依存ライブラリを内包しない通常のAAR |
| Release Gold AAR | **280,398 bytes**。作業開始前とSHA-256も同じ |

Elief自身はExpressiveの試験版バージョンを要求しません。標準テーマの独立consumerはMaterial3 1.4.0でビルドし、サンプルは公開された実験的MaterialExpressiveThemeを確認するためだけに1.5.0-beta01を選択します。[依存ツリー](../verification/phase-2-4-ui-dependencies.txt)も記録しました。

Maven座標は `jp.linkserver.goldy:elief:0.1.0-SNAPSHOT`。Release AAR / sources / POM / Gradle module metadataを作業フォルダー内のconsumer用Mavenリポジトリへ生成しました。外部へのPublish / Push / Releaseは実行していません。

## サンプルと導入例

既存の初期テンプレート `:app` を独立サンプルにしました。更新あり・なし、転送中、総量不明、失敗、権限不足、APKなし、空ノート、引き渡し済み、確認中、不正Markdownを選択できます。Light / Dark、通常 / Expressive、API31以降のDynamic Color、独自フォントの例としてMonospaceを切り替えられます。

サンプルのDownloadはViewModel内で進捗を再現するだけです。GitHub通信、権限設定画面やインストーラーの実起動、実APKインストールは行いません。実際のGold APIへ接続する [HostUpdateViewModel](../verification/compose-consumer/consumer/src/main/java/example/eliefconsumer/HostUpdateViewModel.kt) と [Composable導入例](../verification/compose-consumer/consumer/src/main/java/example/eliefconsumer/Consumer.kt) は独立consumerのコンパイル対象です。

ShutUpはShutUp、SchedulerはNittcScheduler、Glyph BartyはGlyphBartyの既存Metadata / Version / APK選択 / 版対応ポリシーをそのまま指定します。Schedulerの非アプリRelease除外、未知のタグ版対応の拒否もGoldに残しています。ホストのテーマ内に画面を配置し、既存のViewModelとNavigation、URL・Activity起動へ接続する方法をAPI文書に記載しました。3アプリへの移行は行っていません。

## 検証

| 検証 | 結果 |
|---|---|
| Gold JVM | 既存 **103件成功**。版判定characterization、転送異常系、安全性検査を維持 |
| Elief JVM | **10件成功**。状態変換、APK方針、曖昧な選択、Scheduler除外、URL許可・拒否 |
| 既存Gold独立consumer | Releaseビルド、既存4件、AAR同一性、Compose / Markwon / Pyriteなしを確認 |
| 新Elief独立consumer | 公開AARだけでReleaseビルド。Gold推移依存、安定版Material3、AARのSHA-256一致を確認 |
| Debug / Release | Elief AAR、サンプルAPKのビルド成功 |
| API 36 | 320dp・文字倍率1.8でCompose UI **12件成功**。倍率1.0でも先行11件成功 |
| API 28 | 320dp・文字倍率1.0 / 1.8のそれぞれでCompose UI **12件成功**。Dynamic ColorはAPI条件により無効表示 |
| lint Debug / Release | Elief 0 errors / 0 warnings。Gold 0 errors / 既存UseKtx警告2件。サンプル0 errors / 既存テンプレート由来8警告 |
| Manifest | Debug / ReleaseでREQUEST_INSTALL_PACKAGES宣言なし、Serviceなし、Gold FileProviderが一つ、非exported、authority整合 |

UIテストは戻る操作（アプリバー・システム）、リンク委譲、失敗からの再試行、同一フレーム相当の重複イベント、総量不明転送のキャンセル、権限案内、引き渡しを成功とみなさない表示、空ノート、履歴展開・スクロールのActivity再生成後復元、フォント実変更、Markdown表・危険リンク・画像・HTMLを確認します。

画面記録は `build/phase-2-4/` 以下にあります。320dp・大きな文字での縦折り返し、横スクロールできるサンプル設定チップ、テーマによる色・形・文字の違いは意図した表示です。画素一致のためにホストテーマの差を打ち消す処理は加えていません。検証用AVDはread-only / no-snapshotで起動しました。API36起動直後のSystem UI応答停止はビルド同時実行時のAVD側のもので、待機ダイアログを閉じた後のテストは成功しています。

再現用スクリプトは [run-compose-ui.ps1](../verification/run-compose-ui.ps1)、[verify-compose-manifest.ps1](../verification/verify-compose-manifest.ps1) です。端末変更を避けるためUIスクリプトは専用エミュレーターだけを受け付け、画面倍率・サイズを実行後に戻します。

## ファイル寿命と残る制限

- InstallRequestはインストーラーがURIを読み終わるまで保持します。UI破棄、Activity復帰、起動成功・失敗だけを理由にreleaseしません。必要ならpendingRequestsで復元し、使用終了をホストが確認して明示的にreleaseします。
- VerifiedApkはメモリ上の検証済みreceiptです。Activity再生成ではViewModelで保持し、prepare成功後はGoldの共有コピーが残るため元cacheを削除できます。prepare前のプロセス終了では再ダウンロードが必要です。
- API26、他OS・OEM・実機でのUI動作は未検証です。API26のsystem imageが環境にありません。minSdk / API利用はビルドとlintで確認しています。
- Nothingの実フォントファイルは同梱していません。ホストのライセンス済みFontFamilyをTypographyへ指定してください。動作検証ではMonospaceへの変更を確認しました。
- Markdownは同期解析で、巨大なホスト独自入力や極端に広い表は個別確認が必要です。Goldのメタデータ上限は維持し、相対リンク・HTML・画像の自動読み込みは対応範囲外です。
- 新サンプルはfixture用です。実通信・実インストーラーとの通し操作やホスト固有のプロセス終了後復元は各アプリ導入時の検証対象です。Goldの転送・検証・インストーラー本体はPhase 2-3と同一です。

既存Goldyの未コミット実装を保護し、Goldソース・既存テストを変更していません。READMEは変更していません。3アプリとPyriteも変更せず、コミット・Push・Releaseは実行していません。保全の詳細は [保全結果](../verification/phase-2-4-preservation.json) に記録しました。
