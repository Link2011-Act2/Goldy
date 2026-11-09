# Goldy共通UX・4アプリ移行 最終記録

実装と実施可能な検証は完了しました。通常の更新操作は「ダウンロード → インストール」とし、更新済みAPKは実際のインストール情報を確認してSDKが自動整理します。キャンセル・結果不明の場合は保持して通常の「インストール」からやり直せます。インストーラー起動をインストール成功と扱っていません。

公開Goldy SHA: `809dda97ab45cd28812f150697cba126c6d4d406`（開始時 `27692fc266e82f081406c85c9841226b4aca4733`）。Gold/EliefのAAR・POM・Module Metadata計6成果物を新SHAでHTTP200取得し、整合性と独立Gold-only/Elief推移依存ConsumerのDebug/Release R8を検証済みです。以後SDKソースが変わっていないため各ホストで内部試験・Consumerを重複実行していません。

| アプリ | 更新基盤・UI | 全Unit | Debug / R8 / Lint | ホスト端末試験 |
| --- | --- | ---: | --- | --- |
| ShutUp | Gold+Elief、Pyrite継承、新APK管理UX |190成功|成功。署名Release APK生成は既存guardで停止、R8/Lintは別途成功|API36:16ケース/段階成功|
| NITTC Scheduler | Gold+Elief、通常M3/Expressive、AI Runtimeと分離 |247成功|成功、unsigned Release R8|API36:10ケース/段階成功。以前のRange/API28証跡も保持|
| Glyph Barty | Gold+Elief、M3 Expressive/Nothing維持 |435成功|最終コードでDebug/Release R8/Lint成功|API36:15ケース/段階成功。実Mainからの自動整理再確認を追加|
| BeastLocator | Gold Coreのみ、XML/Views/Markwon維持 |66成功|成功。既存Release構成を明示的なverifyUpdateR8=trueで縮小検証|API36:23ケース/段階、API28:3ケース/段階成功|

Lint Error/Fatalは全ホスト0件。警告はShutUp Debug60/Release61、Scheduler245/243、Glyph213/213、Beast197/199です。既存警告や無関係な機能を本Goalで一括修正していません。

## 共通設計と移行結果

- GoldがRelease取得・比較・選択・永続キャッシュ・複数タグ無視を所有。公式の各アプリPolicyを使用し、Schedulerのai-runtime除外、GlyphのLatest取得、Beastの一覧取得を維持。
- SDKの署名/ローテーション、packageName、versionCode、厳密なPublished版名Binding、size/digest、インストール直前再検証を維持。公開JVM ABIは旧Gold807/Elief244 descriptorに欠落なし。
- SDKのreconciliationは実インストールAPKのバイト一致等を検証して不要コピーを整理。読み取り中FDを維持し、同じURIは確認済みインストールAPKへ安全に切替。API28/36で実際の署名済みテストパッケージ更新と外部読取を確認済み。
- UIDT/Foreground WorkManagerと通知はホスト責務。転送と画面/ViewModelの寿命を分離し、SDKの永続Rangeを利用。転送完了・復元だけでインストーラーを起動しない。
- 旧通知無視タグ・開発設定などを冪等に移行。信頼情報が不足する旧ReleaseキャッシュからAssetを捏造しない。設定保存に失敗した場合も移行済みマーカーを確定しない。
- 通常の保存APK管理カード、共有ファイル解放、専用再起動確認を撤去。必要な操作名と短いエラー表示に統一。既存のPyrite遷移・テーマ・アイコン・容量表示を維持。
- ShutUp/Schedulerは既存移行を新しい共通UXへ適用。Glyph/Beastは旧独自HTTP/Downloader/検証をproductionから撤去。ReleaseChannel等の他機能で共有する表示・開発用判定は維持。

BeastのAPI36では4194304/13382167byte、API28では3145728/13382167byteから別プロセスで206追記し、完成APKの全体検証・要求作成まで成功しました。200再取得の表示、破損部分の破棄も確認。記録された自動起動は0回、明示インストール操作は1回です。Glyphの既存Range試験も4194304/69351780byteから同様に成功しています。

## 失敗を含む検証履歴

- Glyphの最初の全件実行では、未変更のMatrix復旧テスト1件が失敗。単独再実行と、最終コードの全435件再実行で成功しました。最初の失敗XMLは残しています。テストの削除・無効化はしていません。
- BeastのcompileSdk36は公開Gold AARの37要件で失敗。compileSdkのみ37へ更新し、min26/target36を維持して成功しました。
- Beastの背景試験はActivityScenarioの後片付けに失敗。Debug専用Activityを用いるfixtureへ修正し、失敗した1件だけ再実行して成功。実XML UI試験8件は繰り返していません。
- Rangeドライバーのクラス名誤記で実試験を開始できなかった記録も保持。修正後の206/200/tamper試験はすべて成功。

## 未検証条件・制約

正式な秘密鍵、実機の最終インストール成功、Nothing/Glyph/音声/MediaProjection/GPS/Geofence等のハードウェア動作は未検証です。ホストのcontrolled fixtureはインストーラー呼び出しを記録しており、実インストール成功の証明とは分けています。実パッケージ更新とURI保持は共有SDKのテストAPKで検証済みです。

今回のホスト試験ではAPI26とGlyphのAPI33実行管理分岐は未検証。SDKの既存ローテーション記録はAPI26/28/33/36各14件で期待値一致、API27実機試験なしです。force-stopやメーカー制限下で常時自動継続する保証はしていません。

BeastにはElief・Compose UI・Compose runtime本体・Composeコンパイラーを追加していません。元から使用されていたActivity1.13.0のnavigationevent推移依存に注釈専用runtime-annotationが含まれます。既存依存を安全性不明の除外で壊していません。

各アプリのCommit/Push/Tag/Releaseは行っていません。既存の未コミット変更とBeastのステージ済み削除は保存されています。今回のGoldy SDKソースは809で公開済み、検証記録類はローカル差分です。READMEは変更していません。

## 詳細・変更ファイル一覧

- [要件別完了監査](D:/apps/goldy/verification/managed-apk-ux/completion-audit.json)
- [全リポジトリのHEAD・変更ファイル](D:/apps/goldy/verification/managed-apk-ux/worktrees.json)（既存ユーザー差分も区別して記録）
- [ShutUp結果](D:/apps/shutup/verification/phase3/results.json) のmanagedApkUx
- [Scheduler結果](D:/apps/schedulernittc/verification/phase4/results.json) のmanagedApkUx
- [Glyph結果](D:/apps/glyphvisualizer/verification/phase4/results.json)
- [Beast実装対応表](D:/apps/beastlocator/verification/phase5/implementation.md)・[検証結果](D:/apps/beastlocator/verification/phase5/results.json)

実装上の残タスクはありません。追加検証を行う場合は上記の未検証条件から選び、変更していないSDK内部試験やConsumer全件をホストごとに繰り返さないでください。

最終確認時刻: 2026-11-09 15:29:42 JST。実行中のビルド・テストはありません。
