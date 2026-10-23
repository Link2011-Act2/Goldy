# Phase 2-6 — Goldy Demo

2026-10-23。開始時／終了時HEADは `d97042dce6dec2b913ffb6a378969a441d39ac7f`、branchはmasterです。開始時の作業ツリーはcleanでした。今回の変更は未コミットで保持しています。Commit・Push・タグ・GitHub Release作成は行っていません。

## 実装

既存 `:app` をGoldy Demoへ拡張しました。

| 画面 | 内容 |
|---|---|
| UI Showcase | 既存fixtureと12件のUIテストを維持。キャンセル済みfixtureを追加。中間Release履歴・Markdown・各転送状態をオフラインで表示 |
| Live Demo | ShutUp／NITTC Scheduler／Glyph Bartyの実GitHub Releases取得。Goldの公開APIによる取得と比較。タグ、チャンネル、ノート、asset情報、通信・セッションキャッシュ・失敗・rate limitを表示。他アプリのダウンロード・インストール操作なし |
| Update Demo | 実際の自己package・version・署名・許可を表示。Goldの実ダウンロード／検証／prepare／launchの接続、対象releaseの固定、重複操作防止、明示的再試行・破棄・pending request復元・確認後の解放を実装 |

通常M3／M3 Expressive、Light／Dark、API31以降のDynamic Color、全Typographyの等幅フォント切替を共通化しました。画面・テーマと展開状態はsaveable、通信・転送状態はViewModelでActivity再生成を越えて保持します。Liveのメモリーキャッシュはプロセス終了後の永続キャッシュではありません。

Gold／Eliefのソース、公開API、ビルド・publication設定は変更していません。依存追加はappからGoldへの明示的依存と、既存バージョンのcoroutines-test（テスト用）のみです。既存3アプリとPyriteは変更していません。READMEは編集していません。

## 実APIの確認

API36の専用エミュレーターから、DemoのGoldクライアントを使用して未認証の実通信を確認しました。

| Repository | 取得catalog件数 | 比較用0.0.0-Releaseに対する候補 |
|---|---:|---|
| Link2011-Act2/ShutUp | 3 | v1.1.0-Release |
| Link2011-Act2/NITTC_Scheduler | 18 | v1.1.1-Release |
| Link2011-Act2/GlyphBarty | 1（latest feed） | v2.1.0-Release |

Schedulerの件数はfilter前の取得catalog件数です。Goldの既存policyによりAI runtimeリリースを除外します。選択した他アプリの「インストール済み版」を自動検出した結果ではありません。

Goldy自身は `NoPublishedReleases` でした。Update Demoでは **Not configured: no eligible Goldy Demo APK release** と判定し、ダウンロードやインストーラー起動を行っていません。

## 検証結果

- Debug APK／Release APK／androidTest APK：ビルド成功。
- Gold JVM：既存103件、Elief JVM：既存10件、Demo JVM：6件（既存1＋追加5）、すべて成功。
- Compose UI：既存12件と追加3件成功。API36、320dp、文字倍率1.8、Activity再生成、Light/Dark、M3/M3 Expressive、Dynamic Color、フォント切替を確認。
- 実API instrumentation：1件成功。上記3リポジトリと自己配布の取得、連打防止、LiveのActivity再生成後の保持を確認。
- 最初の追加UIテスト1件は、再生成後も正しく保持されたスクロール位置を考慮しないアサーションで失敗。見出しまでスクロールしてから確認するテストへ修正し、追加3件の再実行で成功。表示整理・全フォント切替後には追加3件＋影響するテーマ1件を実行し、4件成功。最後の復元連打ガード補強後にも追加3件を実行して成功し、最終Releaseで起動・自己identity表示を再確認しました。
- lint Debug／Release：エラー0、警告9（既存のRedundantLabel 1／UnusedResources 7、新規の非推奨ではないUseKtx推奨1）。警告の抑制や検査緩和なし。
- Release：全packageをR8最適化。既存debug keyでローカル検証用コピーだけに署名し、API36で起動・UI Showcase・Update Demoの実identity表示を確認。新しい鍵は作成していません。
- R8起動確認時、エミュレーターのSystem UI応答待ちダイアログが前面に表示されたため一度画面判定に失敗。Goldyのプロセスは稼働しており、AndroidRuntimeクラッシュなし。System UIのWaitを選んだ後に両画面を確認しました。
- Manifest：REQUEST_INSTALL_PACKAGESをappだけに追加。既存INTERNET、`jp.linkserver.projectgoldy.goldy.fileprovider`（非export・URI grantあり）を維持。追加のstorage権限・serviceなし。

| APK | bytes |
|---|---:|
| Debug | 13,098,120 |
| Release（R8・未署名） | 1,466,370 |
| Releaseの既存debug key署名コピー（ローカル検証専用） | 1,483,581 |

機械可読の結果は [検証JSON](../verification/phase-2-6-results.json)。生ログ・APK・画面キャプチャ・R8起動画面XMLは無視対象 `build/phase-2-6/` に保持しています。再実行用スクリプトは [run-demo-ui.ps1](../verification/run-demo-ui.ps1) です。通常の一括テストでは実APIテストをskipし、明示的な `goldyLive=true` でだけ通信します。

## 実際の自己更新に残る作業

[Goldy Demo手順書](goldy-demo.md) に配布規約と、公開せず旧版・新版をローカルで作る手順を記載しました。

1. 使用者が管理する同一署名鍵で、同じpackageかつversionCodeを増やした旧版・新版を用意する。
2. 別途承認を受けて、Goldyリポジトリへ `v<version>-Release` タグ、対応するversionName、1つの `goldy-demo-*.apk` という規約で配布する。
3. 旧版から実ダウンロード→検証→許可→インストーラーへ進み、新版を起動して実際のidentityを確認する。

今回、実配布による自己更新・インストーラー引き渡し・インストール完了の通し検証は未実施です。HandedOffは成功判定に使いません。pending leaseはActivity復帰やViewModel破棄で解放しません。全reader終了を利用者が確認した場合だけ明示的に解放します。

DemoのAPI26〜35・実機／OEMでの新規動作は未検証です。既存ライブラリの過去の検証記録は保持しています。実rate limit発生のための大量リクエストは行わず、429と再試行抑制はJVMテストで検証しました。プロセス終了前にprepareしていないVerifiedApkは復元できず、再ダウンロードが必要です。実pending leaseのDemo画面からの復元・再起動・解放も、自己配布準備後の通し検証に残ります。

Phase 3のShutUp移行へ進める状態です。既存アプリへの導入は今回行っていません。

