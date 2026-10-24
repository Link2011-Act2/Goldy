# Goldy Demo — UIスタイル選択の最終調整

確認日: 2026-10-24。開始/終了HEAD: `f3f42e8c666ac2447156640016cf1e828974407b`、branch: `master`。

## 変更

- Demoの「Style」から単一選択ダイアログを開き、Material 3 Expressive / Nothing風 / 通常Material 3の3択を表示します。選択済み項目を押しても解除されません。初回はMaterial 3 Expressiveです。
- `DemoStyle`で選択を一元化し、2つのトグルを組み合わせる従来方式を置き換えました。ダイアログはRadioButtonの役割と選択グループを持ち、文字拡大時は縦スクロールできます。既存の設定行は横スクロールを維持しています。
- スタイル・Dark・Dynamic Color・Custom fontをDemo専用SharedPreferencesへ保存します。列挙順に依存しない名前で保存し、未知のスタイルはM3Eへ戻します。他の設定は保持します。
- Nothing風ではDynamic ColorをOFF/無効として表示し、M3E/通常M3へ戻ると以前のON/OFFを復元します。
- DemoThemeのMaterialTheme/PyriteTheme、カード色・形状の橋渡し、フォント指定、システムバー、スタイル切替時の履歴・スクロール保持を維持しています。
- Showcase / Live Demo / Update Demo / Style referenceと更新処理は従来のままです。

製品差分はDemoの`MainActivity.kt`・`DemoTheme.kt`と新しい`DemoAppearance.kt`です。既存4 UIテストの操作先・設定の隔離を更新し、`DemoAppearanceTest.kt`に3件追加しました。テストは外観設定だけを隔離・復元し、更新のキャッシュや保留中インストール情報を消去しません。

## 検証結果

専用API36エミュレーターで実行しました。実通信やAPKインストーラーの操作は今回の変更対象に含めていません。

| 検証 | 結果 |
|---|---|
| Debug / UIテストAPK / Releaseビルド | 成功 |
| Demo JVM | 既存6件成功 |
| 通常倍率・320×640dp UI | 29件成功（既存26件＋新規3件） |
| 320×640dp・文字倍率1.8 UI | 影響範囲10件成功 |
| 最終設定保存コードでの追加確認 | 設定テスト3件成功 |
| 3スタイル×Light/Dark | 通常/倍率1.8の画面確認成功 |
| 設定保存・復元 | 初期値、排他選択、新規Activity、Activity再生成、未知の保存値、Dynamic/フォント保持を確認 |
| 既存操作 | 戻る、キャンセル、再試行、二重操作防止、権限案内、Markdown、履歴・スクロール保持、IMEを確認 |
| lint Debug / Release | 各エラー0、既存警告9（RedundantLabel 1、UnusedResources 7、UseKtx 1）。新規警告0 |
| R8 | Release最適化成功。1000dp幅で6パターンの表示・操作・プロセス終了後の4設定復元を確認 |
| Debug APK | 13,427,389 bytes |
| Release APK（unsigned） | 1,519,118 bytes |

最終ビルドコマンド:

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:assembleRelease :app:lintDebug :app:lintRelease --offline
```

通常倍率は`verification/run-pyrite-style-ui.ps1`で`UpdateScreenTest,DemoScreenTest,EliefLayoutTest,PyriteStyleTest,DemoAppearanceTest`を明示指定しました。倍率1.8は新しい設定3件、Pyrite4件、Eliefの固定操作/IME/低い画面3件です。最後のKTX推奨指摘の修正後は設定3件とR8実行確認を再実施しました。

R8実行確認用のAPKは既存debug鍵で署名した無視対象のコピーです。鍵の生成、配布、公開はしていません。補助スクリプトでは選択済みRadioの`checkable`検出と、入力反映後にプロセス停止する同期を修正しました。製品側の検査条件を緩めていません。

証跡は`build/demo-style-selection/`に保存しています。

- `final-build-lint.txt`: 最終Debug/Release/JVM/lint/R8ビルド
- `standard/instrumentation.txt`、`font18/instrumentation.txt`: 29件/10件の結果
- `ui-final-prefs-run.txt`: 最終設定3件の結果
- `standard/demo-style-selection/`、`font18/demo-style-selection/`: 各6画面
- `r8-smoke.json`、`r8-1.png`〜`r8-6.png`: R8版の6パターンと実システムバー
- `preservation-before.json`、`preservation-after.json`: 保護照合

## 保護・未検証

Goldyは開始時クリーンでした。Pyrite・ShutUp・NITTC Scheduler・GlyphVisualizer・BeastLocatorのHEAD/branch/status/indexとGit対象・未追跡1,004エントリー（開始時の欠落を含む）を照合し、既存差分を含め変更0です。GoldyのHEAD/index、`:gold`、`:elief`、Gradle依存、Manifest、READMEと以前の検証資料も変更していません。

EliefのホストMaterialTheme/UpdateCardStyle継承と公開APIは差分なしです。Gold/Elief JVM・独立Consumerは今回変更していないため再実行せず、前回の成功記録を維持しています。

実機・API26〜35・TalkBack実操作・全壁紙のDynamic Color・実APK自己更新は今回未検証です。狭い画面ではDemoの設定行を横スクロールして操作します。Commit、Push、タグ、Release、JitPack再公開は行っていません。
