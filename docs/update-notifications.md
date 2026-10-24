# 共通バージョン比較と更新通知UI

Phase 1〜4で追加したAPIです。導入にはこの変更を含む検証済みコミットを使用してください。READMEに残る以前の公開SHAには新APIが含まれません。公開検証の座標と結果は [unified-updates-verification.md](unified-updates-verification.md) に記録します。

## 責務と状態

4アプリ用 `UpdatePolicies.shutUp()/nittcScheduler()/glyphBarty()/beastLocator()` はShutUpを正本とする `VersionPolicies.Common` を使用します。比較とchannel許可・feed・APK選択・tag binding・package/signer/versionCode検査は別契約です。比較の同値性をAPK本人性に利用しないでください。仕様差は [比較レポート](four-app-updater-comparison.md) を参照してください。

| 状態・操作 | 意味 |
|---|---|
| availableUpdate | 手動で確認可能な更新。無視後も保持 |
| notificationUpdate | 通知設定とignoredTags適用後の候補 |
| showBanner | notificationUpdateがあり、そのtagをセッション内で一時hideしていない |
| hideBanner() | バナーだけ一時hide。badge/手動確認は維持 |
| showBanner() | 一時hideを解除。通知無効・ignoredTagsは解除しない |
| ignoreVersion(tag) | 永続的なtag通知抑止。手動確認は可能 |
| resetIgnoredVersions() | ignore集合をクリア。一時hideは維持 |
| restoreCachedState() | 現在のGoldセッションへcache読込。新しいGoldセッションでは一時hideを引き継がず、ignoreはstoreから復元 |

Activity再生成では同じViewModel/Goldyを保持します。再生成のたびにGoldyを作り直すと一時hideが解除されます。プロセス死亡からの再構成・設定保存・store移行はホスト責務です。policy idの更新で旧catalogを再評価せず再取得しますが、ignoredTagsを破棄しません。

## Eliefの3コンポーネント

```kotlin
// stateはホストViewModelのGoldy.stateを収集した値。
UpdateNotificationBanner(state, onOpenDetails = navigateToUpdate,
    onHideBanner = { model.hideBanner() })
UpdateNotificationIcon(state, onOpenPrompt = { model.openCheckDialog() })

if (showDialog) {
    UpdateCheckDialog(state,
        onCheck = { model.checkManually() },
        onOpenDetails = navigateToUpdate,
        onIgnoreVersion = { tag -> model.ignoreVersion(tag) },
        onDismiss = { model.closeCheckDialog() },
        strings = UpdateNotificationStrings(title = "アプリの更新", openDetails = "更新内容"))
}
// navigateToUpdateの引数UpdateCandidateを固定し、既存UpdateOverviewScreenへ渡す。
```

BannerはshowBannerとnotificationUpdate、IconはnotificationUpdate、DialogはavailableUpdateを参照します。したがってbannerをhideしてもbadgeは残り、ignoreしても設定画面の手動dialogから詳細を確認できます。Dialogの表示自体がネットワーク通信を開始することはありません。

Eliefはcallbackだけ通知し、画面遷移・scope・通信・永続設定・インストーラーを所有しません。Dialogのcheck操作はchecking中無効になりますが、ホストVMでもlaunch前の同期claimで二重操作を防いでください。手動checkは `Goldy.check(CheckTrigger.Manual)` を呼び、HTTP retry制約は迂回しません。GoldのCancellationExceptionを握りつぶさないでください。

色・Typography・ShapesはホストMaterialThemeに従います。Bannerは `LocalUpdateCardStyle` のcontainer/shape、残りはMaterialThemeの役割色を使用します。`UpdateNotificationStrings` で全表示文言、各 `icon` composable slotでアイコンを差し替えられます。modifierはホストの配置・幅・追加gestureに使います。Pyriteの必須依存も独自フォントもありません。Nothingのprimaryはホストのモノクロ設定に従い、独自の赤いアクセントを追加しません。

Schedulerの主な構造を採用しつつ、dialog内部の縦スクロールでチェック/詳細/無視を小画面と文字拡大でも利用可能にしました。閉じるボタンはdialogの固定confirm領域です。既存アプリ固有のスワイプ方向・距離・アニメーション時間は標準APIへ増設していません。ホストが必要ならmodifierで追加します。

## 通信を行わないDemo

Goldy Demoの **Notifications** タブは新しい通知部品を表示します。既存Showcase、Live、Update、Style referenceは維持します。

- Available / Up to date / Network failure / Slow checkのオフラインsource。
- hide、show、ignore、reset、fresh session restoreを実際のGold APIで実行。
- キャッシュ保持、エラー、取消、二重操作防止、dialog再生成、詳細への遷移。
- M3E / Nothing / 通常M3、Light/Dark、Dynamic Color、ホスト指定フォント。
- sourceの実行回数を表示し、cache復元が再通信しないことを確認可能。

`Goldy.createWithSource(context, config, fetchCatalog, store)` は既存createに追加したmetadata source注入専用factoryです。実際のinstalled package metadataを読み、check時にIOコンテキストでsourceを呼びます。UI状態変更はMainへdispatchしてください。repo外URL等のcatalog検証は既存通りです。sourceはCancellationExceptionを伝播し、ネットワークを使うadapterは自身で応答サイズ・HTTPS・接続先等を制限してください。通常の実通信は検査済みの `Goldy.create` / GitHubReleaseClientを使用します。このfactoryでVerifiedApkを作ることはできません。

Demo sourceは明示操作まで動作せず、GitHubへ接続せずAPKも供給しません。storeはDemo専用InMemoryUpdateStateStoreで、fresh session復元はプロセス内の擬似再起動です。プロセス死亡後の通知fixture保存は保証しません。既存のLive/Update Demoの実通信やインストール操作とは分離しています。
