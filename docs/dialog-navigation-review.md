# 更新確認ダイアログとDemo画面遷移の検証

2026-10-27。開始HEADは `3a86b73abc2a2879e02ecb03f86620726130f501`、masterです。
今回の変更はローカルのみです。Commit・Push・タグ・Release・JitPack公開は行っていません。

## 変更内容

EliefのUpdateCheckDialogは `availableUpdate` がないときに従来のCheck for updatesを主ボタンとして表示します。候補がある場合はView updateを主ボタンとし、再確認をバージョン横の48dpタッチ領域を持つIconButtonへ切り替えました。再確認のcontentDescriptionには既存の `strings.check`／`strings.retry` を使用します。checking中は確認操作を無効にし、ホストViewModelの同期claimも維持しています。

notificationUpdateがnullの無視済み候補や、Failureと併存するキャッシュ候補も詳細確認の対象です。失敗状況のメッセージ、Ignore this version、Not nowは維持しています。Eliefが候補・キャッシュを消去する処理や通信を開始する処理は追加していません。

Demoは既存Pyrite SHA `508b1cf5131679fee7581acbc7ea55a365b6ff2c` をそのまま使用します。ローカルGitの同SHAのソースと、実際に依存解決された公開AARのjavap出力でPyriteScreenTransitionおよびForward/Backwardの公開APIを確認しました。依存更新は不要でした。

単一のPyriteScreenTransitionが全5タブとNotificationsの詳細画面を描画します。タブ順はUI Showcase、Live Demo、Update Demo、Style reference、Notificationsです。実際のinitial/targetの順序で方向を決め、詳細へはForward、戻りはBackwardです。幅の1/5の物理的な水平slide、fade、既定springはPyriteへ委譲し、独自のduration/easingやNavigation Composeを追加していません。

画面状態はStringの安定キー `showcase`、`live`、`update`、`style-reference`、`notifications`、`notification-details` とPyriteのSaveableStateHolderを使用します。content lambdaへ渡された画面で描画し、グローバルな遷移先で退場中の画面を書き換えません。BackHandlerはDemoホストの1か所に集約しました。

詳細候補はホストViewModelへ固定し、戻る際にも退場描画用に保持します。次の明示的な詳細選択時に置き換えるメタデータであり、APKやインストーラーの寿命管理は変えていません。各ViewModelとその通信状態、画面のスクロール・履歴展開・設定・dialog状態をActivity再生成やタブ移動で維持します。

## 変更ファイル

| ファイル | 変更理由 |
|---|---|
| elief/src/main/java/jp/linkserver/goldy/compose/UpdateNotifications.kt | 候補の有無による主操作と再確認アイコンの切替 |
| elief/src/main/res/drawable/elief_refresh.xml | 再確認を表すテーマ追従のアイコン |
| app/src/main/java/jp/linkserver/projectgoldy/MainActivity.kt | 単一遷移ホスト、ルート状態、戻る処理 |
| app/src/main/java/jp/linkserver/projectgoldy/DemoNavigation.kt | 安定した画面キーと実initial/targetに基づく方向 |
| app/src/main/java/jp/linkserver/projectgoldy/NotificationDemo.kt | 詳細遷移をホストへ委譲し、詳細の描画を分離 |
| app/src/androidTest/java/jp/linkserver/projectgoldy/NotificationScreenTest.kt | 主操作・無視・キャッシュ・二重実行・全テーマの回帰 |
| app/src/androidTest/java/jp/linkserver/projectgoldy/DemoNavigationScreenTest.kt | 実際の入場方向、途中操作、スクロールと再生成の検証 |
| app/src/test/java/jp/linkserver/projectgoldy/DemoNavigationTest.kt | キー復元と方向のUnit Test |
| verification/run-pyrite-style-ui.ps1 | 新しい遷移テストの画面キャプチャ回収 |
| verification/dialog-navigation-results.json | 今回の実測結果と初回失敗・再実行記録 |
| docs/update-notifications.md | ダイアログの操作優先度を説明 |
| docs/goldy-demo.md | 遷移・状態保持・責務を説明 |
| docs/dialog-navigation-review.md | 本報告 |

元から変更されていた `.idea/compiler.xml`、`.idea/gradle.xml`、`.idea/misc.xml` は開始時のSHA-256と一致しています。上表の変更に含めていません。Goldのソース、依存宣言、README、LICENSE/NOTICE、歴史的な検証記録、既存4アプリとPyriteのソースは変更していません。

## 結果

| 検証 | 結果 |
|---|---|
| Gold JVM | 119件の既存成功結果を再利用。ソース無変更、Gradle UP-TO-DATE |
| Elief JVM | 18件成功、failure/error/skip 0 |
| Demo JVM | 9件成功、failure/error/skip 0 |
| Demo Debug・AndroidTest APKビルド | 成功 |
| Elief / Demo Debug lint | エラー0。警告は0 / 9件（既存） |
| Elief公開JVM記述子 | 公開f7c13187 AARと現在のDebug JARを比較し、237件すべて維持 |
| API36、320dp、fontScale 1.0 | 通知・遷移13ケースと既存回帰8ケース、計21ケースの成功を確認 |
| API36、320dp、fontScale 1.8 | 通知・遷移13件の一括実行に成功 |
| M3E / Nothing / 通常M3 × Light / Dark | 両文字倍率でdialog・詳細への遷移・操作到達性を確認 |
| Dynamic Color、ホストフォント、設定保存 | 既存回帰と通知のテーマ検証で維持を確認 |

画面幅は320dp、表示高さ640dp、density 160dpiです。実際の左右入場はテスト時計を進めた途中の位置で確認しています。詳細への前進・後退、連続タブ操作と途中の戻る操作、Activity再生成、詳細と履歴のスクロール位置、Liveの選択、参照カードの操作回数も検証しています。Live/Update画面の確認では通信や実インストールを開始していません。

最初の13件の試行は、System UIの起動ANR、テストのフレーム時計とdialog/スクロール処理の同期不備、ADB切断で未完走でした。8件成功・3件失敗、残り2件未完了です。OS画面を回復し、フレーム停止前に現在のwindowとスクロールを安定させるテスト同期を修正しました。意図的な連続遷移のテストでは、停止中の時計でスクロール完了を待たずアクセシビリティのOnClickを実行します。方向・復元・操作性のアサートは削除・緩和していません。

失敗・未完了だった5件の再実行はすべて成功しました。通常表示の21件はこの再実行と既存回帰の結果を合わせた件数であり、初回13件の完走を主張していません。文字倍率1.8の13件は一括成功です。ログと各ケース名は [検証JSON](../verification/dialog-navigation-results.json) に記録しています。

## 画面と制限

取得したM3Eライト・Nothingダークのdialogを通常文字と倍率1.8の両方で目視しました。View updateが唯一の大きな主操作となり、再確認が小さなアイコンとして区別できます。文字拡大時はバージョンと無視ボタンの文言が折り返し、必要時のdialog内スクロールで各操作へ到達できます。

画面キャプチャはローカルの `build/pyrite-style-review/dialog-navigation-recovery/` と `dialog-navigation-font180/`、ビルド・API照合・診断ログは `build/dialog-navigation/` にあります。これらの生成物をソース管理へ追加していません。

実機・OEM・API26環境、Release/R8、新しいJitPack成果物、実通信、実インストールは今回検証していません。通知fixture/storeと選択候補は従来どおりプロセス内のみです。既存のDemo lint警告9件とComposeテストルールの非推奨警告は残っています。
