# Goldy 更新UI 横断修正・最終結果

記録時刻: 2026-11-09T19:02:28.472231+09:00

Goldy公開SHA: `811908945c0763066e04b54ee1424a208ceb87a5`（masterへCommit/Push済み）。4アプリはCommit/Pushせず、Release/Tagも作成していません。

## 修正内容

- Elief: 中断メッセージ・保持進捗・再開・キャンセルを下部の通常操作領域へ集約。エラー色の静止バー、総容量不明時の百分率非表示、再開後の通常色を共通実装。既存コンストラクター/JVM ABIを維持。
- ShutUp: 共通中断表示へ接続。Aboutカードに候補と保存済み状態への導線を統合し、重複ボタンを撤去。手動確認だけでは画面遷移しません。移行前APIを参照してコンパイルできなかった更新関連AndroidTestを現行Goldyへ更新。
- NITTC Scheduler: 中断表示とAbout操作を共通化。候補のtag/prereleaseからホストのUpdatePolicyでchannelを設定。AI Runtime/モデル/時間割機能は変更していません。
- Glyph Barty: 同上。Aboutの通知アイコンを撤去し、通常・Experimental・詳細ホームの設定アイコン左へ移動。Nothing Red／M3 secondaryContainerを採用。開いた画面を保存し、ツールバー・システムBack・Activity再生成後も元のホーム/詳細/Aboutへ戻ります。Glyph/Matrix/音声処理は変更していません。
- BeastLocator: Views/XMLのまま、中断バーのエラー色・静止表示・再開後の通常色復元を実装。Aboutの操作を1つへ整理し、再確認は更新画面のアイコンへ移動。位置情報処理は変更していません。

## 検証

- Gold JVM 172、Elief JVM 24、Demo JVM 9: 成功（失敗・skipなし）。Gold/Elief Debug/Release、Demo Release R8、Gold/Elief Lint成功。
- 809dda9公開AAR→新SHA公開AARのJVM ABI比較: Gold888、Elief248の既存公開記述子をすべて保持。
- API36: 共通中断UI 2件成功（320dp/fontScale1.8、進捗不明の静止表示を含む）。既存UI 20件も、通常実行19件と短い画面高でのIME個別確認1件で成功。
- JitPack: build status ok、Gold/EliefのAAR/POM/Module Metadata全6成果物をHTTP200で取得。AARのサイズ/SHA-256とMetadataが一致し、Elief→同じSHAのGold推移依存を確認。
- 独立Consumer: ローカルMaven・隣接Goldyに頼らず、Gold単独およびElief推移依存のDebug/Release R8・依存検証・公開sources取得に成功。

| アプリ | 関連Unit | API36 UI | Debug / Release R8 / Lint |
|---|---:|---:|---|
| ShutUp | 19 | 5（4+対象再確認1） | 成功 |
| NITTC Scheduler | 19 | 3 | 成功 |
| Glyph Barty | 2 | 10（9+配色再確認1） | 成功 |
| BeastLocator | 11 | 1 | 成功（R8は既存verifyUpdateR8=true） |

3つのEliefアプリは新しい公開SHAを使用し、検証中だけ使ったローカルMaven設定を撤去済みです。BeastLocatorは公開Gold Core `809dda97ab45cd28812f150697cba126c6d4d406` を維持しています。

## 実行途中の失敗と解消

- 初回JitPack要求はビルド待ちでタイムアウト。その後の新SHA直接取得・独立Consumerは成功。
- 並行ビルドでメモリー不足が生じたため実行数を制限。Daemon停止でShutUp検証を中断した後、再実行して最終成功を確認。
- API36エミュレーター起動時のANRと、前面に残ったSystem UI応答停止ダイアログを解消。IME試験は画面高に依存する既存の前提を満たす1080x1920/density420で成功。テストは無効化していません。
- Glyph配色試験は直前のクリックによるリップルが次の撮影へ混入。静止色の4条件比較を先に行い、その後クリックと非通知状態を検証する順序で成功。
- 独立Consumerは初回SDK場所未設定で停止。ANDROID_HOMEを実環境へ設定して解消。
- 自動承認レビューの利用上限で新規実行が一度拒否。その後の続行指示時にレビューが成功し、未実行だった検証を完了。
- 各試行のログは削除せず `build/verification/crossapp-ui`（SDK）および各アプリの `build/verification/crossapp-ui-*.log` / `*.txt` に保存。

## 未実施・再確認範囲

- 実機とAPI28での今回のUI再検証。
- 新たな実通信切断・force-stop・OS Job停止・外部インストーラーまでの一連の端末試験は今回は反復していません。HTTP Range/永続化/Job/Worker/APK検証/署名/URI寿命の実装は変更していません。既存の内部試験を各ホストで重複していません。

## 変更ファイル

### goldy

今回Commitしたファイル:

- `app/src/androidTest/java/jp/linkserver/projectgoldy/PausedDownloadUiTest.kt`
- `elief/src/main/java/jp/linkserver/goldy/compose/UpdatePresentation.kt`
- `elief/src/main/java/jp/linkserver/goldy/compose/UpdateScreenLayout.kt`
- `elief/src/test/java/jp/linkserver/goldy/compose/UpdatePresentationTest.kt`
- `verification/crossapp-ui/sdk-verification.md`

今回追加した検証記録: `verification/crossapp-ui/results.json` と本レポート。既存のmanaged-apk-ux等の未コミット差分は保持。

### shutup

- `app/build.gradle.kts`
- `app/src/androidTest/java/jp/linkserver/shutter/AndroidCompatibilityTest.kt`
- `app/src/androidTest/java/jp/linkserver/shutter/ui/PyriteAboutBehaviorTest.kt`
- `app/src/main/java/jp/linkserver/shutter/ui/AboutScreen.kt`
- `app/src/main/java/jp/linkserver/shutter/ui/ShutterApp.kt`
- `app/src/main/java/jp/linkserver/shutter/ui/UpdateOverviewContent.kt`
- `app/src/main/res/values-ja/goldy_updates.xml`
- `app/src/main/res/values/goldy_updates.xml`
- `app/src/androidTest/java/jp/linkserver/shutter/ui/GoldyAboutActionsTest.kt`
- `app/src/androidTest/java/jp/linkserver/shutter/ui/GoldyPausedUiTest.kt`

### schedulernittc

- `app/build.gradle.kts`
- `app/src/main/java/jp/linkserver/nittcsc/ui/AboutScreen.kt`
- `app/src/main/java/jp/linkserver/nittcsc/ui/NittcSchedulerApp.kt`
- `app/src/main/java/jp/linkserver/nittcsc/ui/UpdateOverviewScreen.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/androidTest/java/jp/linkserver/nittcsc/ui/GoldyAboutActionsTest.kt`
- `app/src/androidTest/java/jp/linkserver/nittcsc/ui/GoldyPausedUiTest.kt`

### glyphvisualizer

- `app/build.gradle.kts`
- `app/src/main/java/jp/linkserver/glyphvisualizer/ui/AboutScreen.kt`
- `app/src/main/java/jp/linkserver/glyphvisualizer/ui/ExperimentalDetailsScreen.kt`
- `app/src/main/java/jp/linkserver/glyphvisualizer/ui/ExperimentalHomeScreen.kt`
- `app/src/main/java/jp/linkserver/glyphvisualizer/ui/UpdateOverviewScreen.kt`
- `app/src/main/java/jp/linkserver/glyphvisualizer/ui/VisualizerApp.kt`
- `app/src/main/java/jp/linkserver/glyphvisualizer/ui/VisualizerScreens.kt`
- `app/src/main/res/values-ja/goldy_updates.xml`
- `app/src/main/res/values/goldy_updates.xml`
- `app/src/androidTest/java/jp/linkserver/glyphvisualizer/ui/GoldyAboutActionsTest.kt`
- `app/src/androidTest/java/jp/linkserver/glyphvisualizer/ui/GoldyHomeUiTest.kt`
- `app/src/androidTest/java/jp/linkserver/glyphvisualizer/ui/GoldyPausedUiTest.kt`
- `app/src/androidTest/java/jp/linkserver/glyphvisualizer/ui/GoldyUpdatePresentationTest.kt`
- `app/src/main/java/jp/linkserver/glyphvisualizer/ui/GlyphUpdateNotificationIcon.kt`

### beastlocator

- `app/src/main/java/jp/linkserver/beastlocator/AboutActivity.kt`
- `app/src/main/java/jp/linkserver/beastlocator/UpdateActivity.kt`
- `app/src/main/res/layout/activity_about.xml`
- `app/src/main/res/layout/activity_update.xml`
- `app/src/main/res/values-en/goldy_updates.xml`
- `app/src/main/res/values-zh-rCN/goldy_updates.xml`
- `app/src/main/res/values/goldy_updates.xml`
- `app/src/androidTest/java/jp/linkserver/beastlocator/UpdatePausedViewsTest.kt`

## 次回の確認

必要に応じて実機/API28で中断・明示再開・通知からの復帰を確認してください。失敗していないUnit/R8/LintやGoldy内部のRange/署名試験を無条件で繰り返す必要はありません。アプリ側の差分は未Commitのままレビューできます。
