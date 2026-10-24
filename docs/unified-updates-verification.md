# Phase 1〜4：共通比較・更新通知UIの検証

調査・ローカル検証は2026-10-24、公開Consumerの最終確認は2026-10-25。開始HEADは `0d8999160ec8d0bb0fc97580ff23ce4db4b000a5`、master、cleanでした。既存の公開実装を継続し、更新エンジン・詳細画面の作り直しは行っていません。

## 成果物

- [4アプリ比較と移行判断](four-app-updater-comparison.md)：ソースHEAD、12項目の比較、意図的な仕様差、導入チェックリスト。
- [通知API・状態・オフラインDemo](update-notifications.md)：Commonと4preset、banner/icon/dialog、ホストcallback、cacheとignoreの区別。
- [機械可読の検証結果](../verification/unified-updates-results.json)。従来の報告書・成功記録も保持。

ShutUpの比較アルゴリズムを変更せず、既に存在するrev数値比較を4presetから利用します。`VersionPolicies.ShutUp` の旧表示分類と既存公開署名を残し、Commonの分類は解析済みchannelを使います。旧Scheduler/Glyph規則はtest oracleへ移しました。新しいshowBannerとmetadata source factory以外にGoldの処理を追加せず、APK検査・transfer・installer・provider等16ファイルは開始HEADと同一です。

## ローカル結果

| 対象 | 結果 |
|---|---|
| Gold JVM | **119/119**、skip 0。既存103件＋共通比較13件＋通知状態/移行/source検証3件 |
| Elief JVM | **17/17**、skip 0。既存12件＋通知status/localization 5件 |
| Demo JVM | **6/6**、skip 0。既存設定・復元テスト維持 |
| Compose UI、API36、320dp | **36ケース成功**。一括実行35/36＋OS問題解消後IME 1/1。失敗ログも保持 |
| 320dp・文字倍率1.8 | **14/14**（通知7＋既存レイアウト7、IMEを含む） |
| 1000dp | **2/2**（3スタイル/明暗/フォント/通知操作、幅制限/Insets） |
| Debug / Release | 成功。Releaseは全package対象のR8、有効なmappingと新VMの保持を確認 |
| lint Release | errors 0。Gold既存warning 2、Elief 0、Demo既存warning 9。Demo Debugも成功 |
| 公開API記述子 | Gold **733**、Elief **178**の既存メソッド/フィールドを保持。旧公開AARに対するjavap比較 |
| 独立ローカルAAR consumer | Gold/Elief両方成功。生成AAR hash一致、Elief→Gold、stable M3 1.4.0、Pyriteなし。GoldにCompose/Markwonなし |
| Android検証・install回帰 | API36、4mode・**14検査成功**。Install決定操作なし、既存テスト鍵を再使用 |

Goldの新規比較テストでは実際の47件（重複除去36文字列）のtag/Gradle versionと境界値の全組合せを凍結ShutUp実装と照合しました。大小・等価・対称性・推移律、channel番号、rev、未知/不正tag、旧規則との差、実際の候補/履歴選択を検証しています。比較の同値性ではAPK bindingを通せないことも確認しました。

UIでは一時hide、show、ignore、reset、fresh session cache復元、手動詳細、初期/更新なし、エラーcache、取消、二重操作、dialog/Activity再生成、3スタイル×Light/Dark、Dynamic Color、ホストフォント、既存履歴/scroll/back/下部操作/IME/Insetsを検証しています。fixtureはネットワーク・APKなしです。Live/Updateの既存UIテストも維持しました。

Android回帰では実署名・複数署名抽出、異なる/部分的signer集合・改ざん署名の拒否、権限設定、FileProvider共存、別UIDのgrant前拒否/一時readのみ許可、準備後の変更/削除拒否、起動失敗後lease保持、明示release、provider無効時のlease非生成を確認しました。HandedOffからインストール成功は推測していません。

### 環境由来の中断と再実行

最初の通知テストでは新しいテストhelperが固定confirmボタンにもscroll actionを要求していたため、固定ボタンは通常のクリックへ修正しました。assertを削除・無効化していません。途中で専用emulator-5556が終了し、別ポート5588のread-only AVDへ切り替えました。

36件の一括実行中、IMEの1件だけがタイムアウトしました。採取画像でSystem UIの応答停止dialogが入力欄を覆っていることを確認し、専用AVD上でそのdialogを終了した後、同一条件でIME 1/1が成功しました。続く文字倍率1.8の14件でもIMEが成功しています。製品側のレイアウトやテスト条件を緩める修正はしていません。

- [一括実行の原ログ（35成功、IME 1失敗）](../verification/unified-updates-api36-320dp.txt)
- [復旧後のIME](../verification/unified-updates-api36-ime-recovered.txt)
- [文字倍率1.8](../verification/unified-updates-api36-font180.txt)、[1000dp](../verification/unified-updates-api36-wide.txt)
- [権限](../verification/unified-updates-api36-permission.txt)、[installer/URI](../verification/unified-updates-api36-installer.txt)、[cleanup](../verification/unified-updates-api36-cleanup.txt)、[provider無効](../verification/unified-updates-api36-provider-disabled.txt)

## 画面確認

| スタイル | Light | Dark |
|---|---|---|
| Material 3 Expressive | [banner](reviews/unified-updates/Material3Expressive-false-banner.png) | [banner](reviews/unified-updates/Material3Expressive-true-banner.png) |
| Nothing | [banner](reviews/unified-updates/Nothing-false-banner.png) | [banner](reviews/unified-updates/Nothing-true-banner.png) |
| 通常Material 3 | [banner](reviews/unified-updates/Material3-false-banner.png) | [banner](reviews/unified-updates/Material3-true-banner.png) |

[失敗後cache dialog](reviews/unified-updates/cached-failure-dialog.png)、[文字倍率1.8のdialog](reviews/unified-updates/font180-dialog.png) も保存しました。色・角・フォントは既存PyriteホストTheme/CardStyleを継承し、Nothingに新しい赤やカラフルなbadgeを追加していません。大きな文字ではtag/ボタン文言が折り返され、長いエラーdialogは内部scrollで操作へ到達できます。Demo上部の設定とタブは従来どおり横scrollです。全操作を同時に固定表示する構成にはしていません。

## 成果物サイズ（ローカルRelease）

| 成果物 | bytes |
|---|---:|
| Gold AAR | 270,388 |
| Elief AAR | 110,579 |
| Demo Debug APK | 13,825,704 |
| Demo Release APK（R8、未署名） | 1,520,222 |

APKはローカル検証生成物で、Git/JitPack/GitHub Releaseへ配布しません。新しい署名鍵、タグ、GitHub Releaseは作成しません。LICENSEとNOTICEは両モジュールのAAR/sourcesに含めます。

## 公開検証

実装コミット **`189092a7b9a142e5d432017b5b9ce71404a5758b`** をGitHub masterへ通常Pushし、同じ正確なSHAでJitPackの成功を確認しました。[公開build.log](https://jitpack.io/com/github/Link2011-Act2/Goldy/189092a7b9a142e5d432017b5b9ce71404a5758b/build.log)、[成果物・Consumer検証JSON](../verification/unified-updates-jitpack-results.json) を参照してください。JitPack APIはstatus=ok、commit一致、private=false、JDK21、modules=gold/eliefを返しました。タグ・Release・Demo APKの公開は行っていません。

確定座標：

```kotlin
implementation("com.github.Link2011-Act2.Goldy:gold:189092a7b9a142e5d432017b5b9ce71404a5758b")
implementation("com.github.Link2011-Act2.Goldy:elief:189092a7b9a142e5d432017b5b9ce71404a5758b")
```

ComposeではEliefだけで同じSHAのGoldが入ります。両モジュールのAAR/POM/Gradle metadata/sources JAR、計8成果物を実際に取得しました。POMとmetadataのGold推移依存、variantのサイズ/checksum、sourcesのLICENSE/NOTICEを検証しました。公開sourceはGold 17件、Elief 8件すべてがローカルsourceと改行正規化後に一致し、AAR内LICENSE/NOTICEも一致しました。ローカルSNAPSHOT、Pyrite、Demoの依存・クラスの混入はありません。

| JitPackのみから取得する独立Consumer | Release / R8 | lint errors / warnings | APK bytes | ログ |
|---|---|---|---:|---|
| Gold、Gradle metadata | 成功 | 0 / 3 | 79,226 | [結果](../verification/unified-updates-jitpack-scheduler-gold.txt) |
| Elief、Gradle metadata | 成功 | 0 / 1 | 1,110,428 | [結果](../verification/unified-updates-jitpack-scheduler-elief.txt) |
| Elief、POMのみ | 成功 | 0 / 1 | 1,110,428 | [結果](../verification/unified-updates-jitpack-scheduler-elief-pom.txt) |

Consumerは独立したGradleルートで、Goldy groupに対してJitPackをexclusive指定し、一般依存にGoogle/Maven Centralを使います。producer project、includeBuild、mavenLocal、ローカルMavenは参照しません。新しい共通比較・metadata source factory・通知3コンポーネントを含むホストがコンパイルされ、R8 mapping上でもホストActivityの存在を確認しました。Eliefはstable M3 1.4.0、Gold単体にはCompose/Markwonなし、両方にPyriteなし。ManifestマージではホストFileProviderとGold providerの共存・非exported・限定URI grantを検証し、インストール権限をライブラリが強制追加しないことも確認しました。

公開AARのサイズはGold **270,376 bytes**、Elief **110,519 bytes**、sourcesは各31,236 / 13,107 bytesです。ローカルZIPとの完全バイト再現性は主張しません。Goldの144 classはバイト一致しましたが、Eliefでは29 class中2件（UpdateNotificationsKt / UpdateScreenLayoutKt）でCompose生成コードの差がありました。ソース一致・API契約・公開物のConsumer成功とは別に記録しています。公開AARそのもののランタイムUI試験を実施したという主張もしません。

最終記録の追加はドキュメント・検証資料のみです。上記SHAの成功済みビルドを繰り返さず、導入座標は実際に取得・Consumer検証した実装コミットへ固定します。

## 保護と制限

既存4アプリとPyriteへ本作業からの書き込みは行っていません。開始後の監査ではShutUp/Scheduler/Beast/PyriteのHEAD/status/追跡・未追跡ファイルhashが一致しました。Glyphでは別作業による他機能の変更が発生しましたが、巻き戻し・ステージングを行わず保持しました。GlyphのHEADと、比較器/Release選択を持つupdate配下のファイルは監査時点で同一でした。

READMEは以前の公開版へ固定したままとし、新APIの導入座標は本書と通知ガイドに分けて記録します。READMEの編集は行っていません。

今回のランタイム検証はAPI36の専用エミュレーターです。API26〜35、実機/OEM、TalkBack操作、全壁紙のDynamic Color、実インストール完了は未検証です。HTTP転送実装は不変のためLive API再通信や旧APIレベルでの大量再試験は繰り返していません。BeastのABI selector、実配布APKとのbinding確認、鍵rotation方針、設定移行、native UI統合は導入時の残作業です。Goldの現在signer完全一致条件を維持します。

導入はShutUpから開始可能です。4アプリの順序差を確認しながら、比較レポートのチェックリストに従って個別に移行します。
