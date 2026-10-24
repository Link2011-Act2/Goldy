# 4アプリの更新機構と共通化判断（Phase 1〜4）

調査日：2026-10-24。正本は各ローカル作業ツリーです。アプリを変更・ビルド・インストールせず、ソース、既存テスト、ローカルtagを読みました。Goldyの開始HEADは `0d8999160ec8d0bb0fc97580ff23ce4db4b000a5`（master、clean）です。

## 調査対象と根拠

| リポジトリ | HEAD / branch | 開始時の既存差分 |
|---|---|---|
| ShutUp | `1a18497cfbe62eac20ecf3ba8264cb8daf163fac` / main | 変更3、未追跡18 |
| NITTC_Scheduler | `9fad0402e268902bbfbea485ca216218c17afdf1` / master | clean |
| GlyphBarty（glyphvisualizer） | `d0adb9c7e98bd888110da7edcd1997ebb29ca4db` / master | clean |
| BeastLocator | `bc8589756a58b0637cca35eb1ac6042c2577d66c` / develop | release生成物3件のステージ済み削除 |
| Pyrite（テーマ参照のみ） | `508b1cf5131679fee7581acbc7ea55a365b6ff2c` / master | 既存ドキュメント変更1 |

開始時のHEAD/status/追跡・未追跡ファイルSHA-256は無視対象 `build/unified-updates/preservation-before.json` に保存しました。無視対象キャッシュ全体を保護監査の対象としたものではありません。

主なソース根拠は次のとおりです（各リポジトリルートからの相対パス）。

- ShutUp：`app/src/main/java/jp/linkserver/shutter/ReleaseVersion.kt`、更新Repository/ViewModel、`ui/AppUpdateUi.kt`、更新詳細画面、設定・比較テスト。
- Scheduler：`app/src/main/java/jp/linkserver/nittcsc/update/{GitHubReleaseChecker,AppUpdateRepository,AppUpdatePreferences}.kt`、`ui/AppUpdatePrompt.kt`、`ui/TaskPlanUiComponents.kt`、更新詳細画面。
- Glyph：`app/src/main/java/jp/linkserver/glyphvisualizer/update/{GitHubReleaseChecker,AppUpdateRepository}.kt`、更新確認設定、`ui/HomeMeterComponents.kt`、更新詳細画面。
- Beast：`app/src/main/java/jp/linkserver/beastlocator/{GitHubReleaseChecker,AppUpdateManager,UpdateDownloadViewModel,ReleaseChannel,MainActivity,UpdateActivity}.kt` とXML、関連4テストクラス24件。これら24件は読んだ件数であり、今回実行して成功した件数ではありません。

ファイル単位の詳細とBeastの既存UIは [前回の調査記録](beastlocator-investigation.md) にあります。その文書の「3ポリシーを変更せずBeast専用比較器を作る」という当時の計画は、今回のユーザー指定により**本書のShutUp共通比較方針へ更新**します。過去の記録自体は残します。

## 12項目の比較

| 項目 | ShutUp | Scheduler | Glyph Barty | BeastLocator | Goldyの実装・不足・共通化判断 |
|---|---|---|---|---|---|
| 1. 解析・比較 | 数字core、自然順suffix、v、rev | 寛容な数字抽出・compact表記 | 数字抽出、channel連番を区別しない | 数字、channel、revision | 共通基盤あり。今回ShutUpを正本に4presetを統一。旧compact/連番無視は採用しない |
| 2. channel | IntDev/Internal→Alpha→Beta→RC→正式版 | RC/Alpha等はPreReleaseでBetaより下 | IntDev/Beta/Stable/Release等 | RCを表示上独立、比較はBetaより下 | Commonの順序はShutUpそのまま。表示分類を構文解析ベースに追加。通知許可trackは別Policy |
| 3. API/feed | releases、100件1ページ | releases、標準30件1ページ | releases/latest | releases、標準30件1ページ | 既存List/Latest、ETag・上限・失敗型あり。各presetの取得件数を保持、無条件全件統合しない |
| 4. 候補 | installed track内最大。previewは公開日時 | 全channel最大、ai-runtime除外、name fallback | latestのみ、name fallback | 全channel最大、name fallback、ai-runtime除外なし | feed/filter/previewを維持。Beast presetを追加。比較規則だけ共通化 |
| 5. 間隔/cache/復元 | 8h、成功catalog永続、version変更検知 | 8h、成功cache | 8h、失敗も時刻更新、metadata永続なし | 8h、失敗も時刻更新、時計逆行で確認、metadata永続なし | Goldの成功cache・HTTP retry・復元を共通使用。失敗後8h抑止は自動移植せず差異として扱う |
| 6. 入口UI | banner、badged icon、詳細/無視dialog | 同種のCompose UI、主な移植元 | 上部overlay、独自Nothing描画 | AppCompat/XML bannerとAboutボタン | 今回Eliefにbanner/icon/dialog追加。遷移と確認実行はホストcallback。BeastへのCompose導入は別作業 |
| 7. 非表示/無視 | 一時hideと永続tag無視を分離 | 同じ分離 | 一時hideと次版まで無視 | banner閉じが永続tag無視 | Goldのavailable/notification/showBannerを再利用。showBanner()追加。ignore後も手動詳細を表示。swipe方向/閾値はホスト独自 |
| 8. notes/中間版 | Markdown、全体fold、同値履歴あり | Markdown、途中版のみ | Markdown、中間履歴なし | Markdown連結、全体fold、80,000字制限 | 既存Eliefの安全なMarkdown・saveable全体foldを維持。各presetの中間履歴条件を保持 |
| 9. APK/ABI | shutup_優先・size等 | nittcsc_優先等 | glyphbarty優先等 | unsafe除外→universal→端末ABI順位→製品名/Release、曖昧拒否 | 3アプリ用選択は既存。Beast ABI専用selectorは未実装、公開ApkSelectionPolicyでホスト実装可能。全方式の無条件統合はしない |
| 10. transfer/検証/install | cancel、digest等、署名履歴、provider | 画面scope、事前APK検査少 | 画面scope、明示cancelなし | executor、250MiB、進捗、package/code、方向付き鍵rotation | Goldのdigest/size/package/完全current signer集合/code/tag binding、URI leaseを維持。Beastのrotationを理由に検査を緩めない |
| 11. 開発テスト | IntDevのみ比較/間隔無視 | override/比較/間隔無視 | latest強制、間隔無視 | IntDevのみ最高版preview、override、間隔迂回 | RuntimeOptions/DeveloperOptions既存。preview選択とdismissal迂回はpreset別。通知Demoはオフラインsourceで本物の状態管理を使用 |
| 12. 失敗/cancel/retry | coroutine/Mutex/VM、cache保持 | stateはVM、転送は画面scope | 主に画面state | metadata Activity operation、転送VM/LiveData、one-shot installer | Goldの直列化・取消伝播・cache保持を再利用。UIはcheck/retry/エラーを通知。二重操作の同期claimはホストVM責務 |

## 共通比較の確定仕様

`VersionPolicies.Common` は既存 `compareShutUpVersions()` を直接呼びます。ShutUpソースの `compareReleaseVersions()` を凍結した既存test oracleと実タグ・境界値の全組合せを照合します。**rev対応のために比較器を別方式へ書き直していません。** 元々 `_` / `-` / `.` 区切りと文字→数字の分割によりrevを数値比較できました。不足していたのは4presetでの利用と回帰テストです。

- coreはLong数値列、末尾0を補完。先頭v、大小文字、前後空白、`+metadata`の既存扱いを保持。
- channel順位はIntDev/Internal、Alpha、Beta、RC、正式版の順。ただし同順位aliasの文字列は勝手に同一化しません。
- `IntDev1 < IntDev2`、`Beta2 < Beta10`、`RC1 < RC2`、`IntDev_rev2 < IntDev_rev10`。
- `Beta_rev2 < Beta-rev3`、`Beta10 < RC1 < Release`、`2.1.0-Release < 2.1.1-IntDev_rev0`。
- **`IntDev2 < IntDev_rev2`、`IntDev < IntDev_rev0`**。Beast旧実装の同値化は採用しません。省略番号を推測で0へ変換しません。
- `Beta2_rev3 < Beta2_rev10`。channel順位を先に比較するため、IntDevの大きなrevがBeta/RCを越えることはありません。
- `_rev3` と `-rev3` は同値。Release/Stable/no suffixの同値性も元のShutUpどおり。
- 未知でも構文上有効なsuffixはShutUpの自然順（rank 1）を維持し、表示channelはUnknown。未知suffixを正式版と呼びません。
- 不正core、構文外tag、coreのLong overflowはnull。suffixの数値がLongを越える場合は元の文字列fallbackを保持します。任意精度整数へ変更していません。
- GitHub `prerelease` は順序を変えません。stable-onlyの候補許可には引き続き利用します。

実タグ・Gradle現在版の出典は [four-app-versions.json](../gold/src/test/resources/four-app-versions.json) に固定しました。これはGitHub上の全Releaseや配布APKの全数調査ではありません。

### 公開APIと旧規則の差

`VersionPolicies.Standard/NittcScheduler/GlyphBarty/BeastLocator` はCommonを指します。既存getterのJVM署名を維持します。`VersionPolicies.ShutUp` も比較結果は同じで、従来のchannel表示分類を直接使うホスト向けにその分類を残します。4つの `UpdatePolicies` presetはCommonの解析に基づくchannel分類を使います。

Schedulerのrev無視・RCをBetaより下にする順序、GlyphのBeta2/Beta10同値、compactの `100 == 1.0.0` や独自prefix許容は共通APIでは終了します。`100` は数値100として扱い、`scheduler_120` のような構文外tagは拒否します。Beastのchannel連番=rev同値化、missing=0、RC<Betaも採用しません。理由はユーザー指定のShutUp優先です。旧Scheduler/Glyph比較実装はtest oracleに移し、旧characterizationテストを削除・無効化せず残しました。

変更したpresetのidを更新し、旧比較器で選択したcacheを再利用しません。無視tagは保持します。CommonはBeta/IntDevという単語をsuffixの途中やmetadataに含むだけのtagをそのchannelと誤認しません。通常の既存ShutUpタグに対する比較・installed-track条件は回帰検証します。

## 導入順とアプリごとの作業

推奨順はShutUp → Scheduler → Glyph → Beastです。今回は既存アプリに導入しません。

1. **ShutUp**：比較の正本なので先に移行し、既存候補と対照。cache/ignoredタグ移行、notification/available分離、VMの転送所有、FileProvider共存を確認。前方鍵rotationが必要な配布はGoldの完全current signer条件との差を先に評価。
2. **Scheduler**：RC/Alpha/連番/revの意図的な順序変更を告知。ai-runtime除外、30件、name fallbackを保持。旧画面scope転送をVMへ移す。設定cache id変更後の再取得を確認。
3. **Glyph**：latestのみ・履歴なしを保持。Beta連番が比較される差を確認。旧失敗時8h抑止の変更、永続cache追加、独自NothingフォントをホストThemeで維持。ignoreはGoldの集合方式に移行。
4. **Beast**：`UpdatePolicies.beastLocator()`でmetadata選択は可能。ただし完全互換導入にはABI安全除外/曖昧拒否selector、実配布タグとAPK versionNameの厳密binding、250MiB設定、XMLかCompose橋渡しの決定が必要。鍵rotationは未対応のまま検査を緩めない。banner閉じを一時hideへ誤変換せず、既存dismissed tagをignoreへ移す。

Beastの設定は `github_release_updates` の `last_check_ms`、`dismissed_update_tag`、`show_latest_release_for_testing`、`current_version_override_for_testing`。失敗を含む試行時刻を成功cache時刻へ偽装して移しません。metadata永続cacheの移行は不要です。GoldのignoredTagsは集合なので旧「1tag置換」との差をホストで決めます。転送完了後のone-shot引き渡しを維持し、HandedOffをインストール成功に読み替えません。

安全性とファイル寿命は [public-api-contracts.md](public-api-contracts.md)、通知APIの使用方法は [update-notifications.md](update-notifications.md) を参照してください。
