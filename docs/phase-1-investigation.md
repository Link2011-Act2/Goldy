# Project Goldy — Phase 1 調査報告・仕様案

調査日: 2026-10-21（JST）  
状態: **設計提案・承認待ち。本体実装は未着手です。**

Goldyを、Pyriteから独立した **gold＋任意のelief** として開発する案を提案します。ShutUpのAPK検証を出発点に安全性を強化し、3アプリの命名規則・候補選択・通知・見た目の差は設定または小さなアダプターで維持します。

公開API、各アプリへの導入例、詳細な安全性要件、テスト、段階的計画は [API・移行設計](phase-1-api-and-migration.md) に記載しています。両文書と [作業ツリー記録](phase-1-repository-state.json) が今回の提出物です。

| 提出物 | 掲載箇所 |
| --- | --- |
| 1. 3アプリの機能比較表 | 本文§2 |
| 2. v0.1.0の機能範囲 | 本文§4 |
| 3. モジュール構成・公開API | 設計文書§§1–3 |
| 4. 各アプリへの導入例 | 設計文書§7 |
| 5. 削減できる重複処理 | 本文§5 |
| 6. 必須テスト・セキュリティ上の注意点 | 本文§3、設計文書§§4–6, 8 |
| 7. 段階的な実装・移行計画 | 設計文書§§9–10 |

## 1. 調査基準と既存の変更

ShutUpの指定先は、存在する D:/apps/shutup と解釈しました。各HEADと未コミット差分を確認し、**現在の作業ツリーのソース**を読みました。

| リポジトリ | 調査時HEAD | 直近コミット | 開始時の状態 |
| --- | --- | --- | --- |
| ShutUp | 1a18497cfbe62eac20ecf3ba8264cb8daf163fac | Pyrite導入 | 変更3ファイル、未追跡18ファイル。ステージ済み変更なし |
| NITTC Scheduler | 9fad0402e268902bbfbea485ca216218c17afdf1 | Pyrite対応 | クリーン |
| GlyphVisualizer | d0adb9c7e98bd888110da7edcd1997ebb29ca4db | Pyrite移行 3 | クリーン |
| Goldy（提出先） | 3d5429b4c6043d558a874fd1d60b1dfa242ab31e | Initial commit | 前の依頼による.gitignore変更、.idea/.gitignoreとapp/.gitignoreの削除が既存差分 |

ShutUpの変更済みファイルは、ui/ShutterApp.kt、ui/Theme.kt、verification/pyrite-migration/report.mdです。未追跡はui/PyritePaletteTest.ktとverification/pyrite-light-palette/内の17ファイル（画像・JSON・レポート等）です。正確な全パスは記録JSONに掲載しています。

ShutterAppの差分は画面をShutterDestinationTheme(targetScreen)で包む変更です。更新画面もこのテーマの内側にあるため、移行時に保持する必要があります。更新ロジックの調査は未コミット変更を含む現在の構成を基準にしています。

開始記録の採取時刻は2026-10-21 19:02 JSTです。HEAD、git status、HEADからの差分のSHA-256、未追跡ファイルのSHA-256、Git indexのSHA-256を採取しました。更新機構の主要20ファイルについても行数と内容ハッシュを記録し、終了時にも再照合し、比較した全項目の一致を確認しました。

Gitの読取りではGIT_OPTIONAL_LOCKS=0または--no-optional-locksを使用します。既存3アプリに対する編集・整形・ビルド・テスト実行・インストールは実施しません。Pyriteの作業ツリーも操作しません。Goldyへの追加は今回の文書だけで、README・ソース・Gradle設定を変更しません。コミット・Push・タグ・Release作成も行いません。

比較対象はGitから見える変更・未追跡ファイルと主要調査ファイルです。無視済みのキャッシュを含むディスク全体の同一性を証明するものではありません。

## 2. 機能比較

根拠ファイルは§7にまとめています。「なし」は今回調査した更新経路で実装が見当たらなかったことを表します。既存テストは読み取りのみで、テスト成功を意味しません。

| 観点 | ShutUp | NITTC Scheduler | GlyphVisualizer |
| --- | --- | --- | --- |
| 配信元 | Link2011-Act2/ShutUp | Link2011-Act2/NITTC_Scheduler | **Link2011-Act2/GlyphBarty**。ディレクトリ名と異なる |
| GitHub Releasesの取得 | HttpURLConnection。/releases?per_page=100 の1ページ | HttpURLConnection。/releases の1ページ、per_page指定なし | HttpURLConnection。/releases/latest の1件 |
| HTTP設定 | Accept: application/vnd.github+json、User-Agent、接続/読込各10秒 | 同左 | 同左 |
| draft・対象外Release | draft除外。通常は比較不能タグを除外。Release URLも検査 | draft除外。**ai-runtime-***を大文字小文字を問わず除外。tag空欄ならname利用 | latestの仕様に依存。モデルにdraft/prereleaseなし |
| チャンネル識別 | versionNameのハイフン以降からIntDev/internal、Beta、Stable、Release。裸の数値はUIではUnknown、受入判定では安定版相当 | IntDev/internal、Beta、alpha/rcまたはprereleaseをPreRelease、Stable/Release、Unknown | IntDev/internal、Beta、Stable、Release、Unknown |
| 候補チャンネルの制限 | 安定版は非prerelease安定版、BetaはBeta＋安定版、IntDevは全チャンネル | **現在版のチャンネルによる受入制限なし** | **現在版のチャンネルによる絞込みなし**。ただしlatestは通常非prereleaseのみ |
| バージョン比較 | Longの数値列、不足桁0補完。Release/Stable同義。IntDev < alpha < beta < rc < 安定版。番号付きsuffix/_revをトークン比較。+metadataは順序に影響しない。比較不能はnull | scheduler_/nittc_接頭辞、v、ドット/compact形式。数値→チャンネル→末尾のBeta2/IntDev2等の番号。_rev番号は最後の比較の対象外 | glyphbarty_、v、ドット/compact形式。数値→チャンネル優先度のみ。同一チャンネルのBeta番号/_rev番号は比較しない |
| 候補の選択 | 適格な更新をバージョン降順で選択 | 取得候補中の比較上の最大 | GitHub latestが現在版より新しければ採用 |
| 自動確認の契機/間隔 | onResume。最後の成功から8時間、インストール版変更・時計巻戻りを考慮 | onResume、設定を閉じた時等。成功から8時間、比較用現在版変更を考慮。時計巻戻り専用処理なし | 自動確認設定に応じたLaunchedEffect。**既定OFF**。成功から8時間。版変更による期限解除なし。文言の「1日1回」と実装が不一致 |
| キャッシュ | SharedPreferencesへ候補・中間ノート・digest等。復元時にURL/チャンネル/版を再検査 | SharedPreferencesへ候補・中間ノート。復元時に版とテスト設定を再評価。URL・AI runtime除外の再検査なし | 最終成功時刻、無視tag等だけを永続化。候補はComposableのremember |
| 通知の一時非表示/無視 | availableUpdateとnotificationUpdateを分離。一時的にバナーだけ閉じる。版無視はバナー/バッジを抑止し、手動詳細を残す | ShutUpとほぼ共通。無視解除設定あり | オーバーレイを一時的に閉じる／次版まで無視。手動確認は無視tagで遮断しない。状態はremember |
| 開発者向け設定 | **実インストール版**がIntDevの時だけ比較無視と間隔無視を独立指定。比較無視はpublished_at最大。非数値tagも表示可能 | IntDevのみ更新扱い・比較用現在版上書き。更新扱いは間隔も迂回するが、候補は公開日時でなく比較上の最大。無視解除あり | BuildConfigの版がIntDevの時だけ最新Releaseの更新扱いと間隔無視。更新扱いは通知無視も迂回 |
| 更新履歴/中間版 | 現在版より新しく候補より古い適格版を降順、折り畳み表示 | 同じ範囲を降順、折り畳み表示。チャンネル制限なし | 最新1件のノートのみ |
| APK選択 | .apkのうちshutupを含む名前を優先。URLも配信リポジトリ配下を検査 | .apkをnittc→scheduler→universalの**辞書式優先度**で選択 | .apkをglyphbarty→universalの辞書式優先度で選択 |
| 転送/進捗 | cache/updatesの.part→検証→.apk。接続15秒/読込30秒。総量既知なら％、不明なら受信量。256KiBごと進捗 | 同じタイムアウト/進捗。asset名をサニタイズして.part→.apk。処理はUIファイル内 | Repositoryへ転送を分離。保存/進捗はSchedulerに近い |
| キャンセル | ViewModelのJob.cancel、runInterruptible、ensureActive、finallyで.part削除 | ボタンとJob.cancelあり。ループはThread.isInterruptedだが、withContext(IO)の取消とブロッキングI/Oの停止が連動する保証なし | 専用cancel操作・ループ内の取消確認なし。画面Scope終了で転送が即停止する保証なし |
| 完全性/digest | Content-Lengthと受信量を比較。正しいsha256形式のGitHub asset.digestがあれば照合。asset.sizeは未使用 | 受信後のサイズ一致/digest照合なし | 同左 |
| package/署名/versionCode | archive解析。同package、code >= installed、署名履歴等の証明書一致。複数署名でもany一致。**対象ReleaseとAPK versionNameの一致検査なし** | アプリ内の事前検証なし。最終的にOSインストーラー判定 | 同左 |
| installer | 不明ソース許可確認→設定。専用FileProvider (.updates)、content URI、ACTION_VIEW、読取権限。完了後の起動要求を消費 | .provider共用。resolveActivity確認、起動例外を状態化。Providerは他機能も利用し公開可能範囲が広い | .fileproviderをログと共用。ACTION_VIEW。onSuccess内の起動例外をFailedに変換する囲みなし |
| 詳細画面 | Material 3、840dp上限、v付き版表示、チャンネル、中間ノート、Releaseリンク、cancel/再install | Material 3、AdaptiveContentPane、中間ノート、アプリ固有loading部品 | Material 3、Nothing向けNType/ドットフォント。中間ノートなし |
| Markdown | Markwon＋table、14sp、選択可能、解析結果をremember。外部画像/HTML用プラグインなし | Markwon＋table、14sp。再描画時にsetMarkdown | 同左 |
| 状態保持 | 更新Repository＋Mutex＋StateFlow。転送はViewModelで構成変更に耐える | 更新状態をSchedulerUiStateへ結合。転送状態はremember/画面Scope | 確認・転送・候補が主にremember。構成変更で失われる |
| エラー処理 | 失敗で成功時刻を進めずcache保持。CancellationException再送出。一部検証失敗を既知文言へ変換。自動確認失敗はUI状態にならない | 更新Repositoryはcache維持、取消再送出。転送は例外メッセージを状態化 | 自動確認はonSuccessのみ処理。runCatching結果から取消を明示再送出するRepository契約なし。installer起動例外の扱いにも改善余地 |
| 既存テスト | 比較/チャンネル/URL/開発者設定/通知・cache競合、UI/永続化/別package APK拒否 | Repositoryの通知/cache/取消、ai-runtimeタグ除外、About/更新UI | チャンネル識別と設定互換。対象経路で転送・APK検証テストは確認できず |

3本とも、この経路にはページネーション、ETag条件付き取得、全リダイレクト先の検証がありません。ShutUpも最大100件であり全履歴ではありません。GitHubの一覧はページ分割され、latestは非draft・非prereleaseを選択するため、取得方法の一律変更は既存動作を変えます。[Releases API](https://docs.github.com/en/rest/releases/releases)、[ページネーション](https://docs.github.com/en/rest/using-the-rest-api/using-pagination-in-the-rest-api)

### 2.1 アプリ固有の命名規則

| アプリ | 確認できた例 | 維持する規則 |
| --- | --- | --- |
| ShutUp | ビルド既定1.1.1-IntDev、code 5。ローカルtagにv1.1.0-Release等。テストにBeta10、IntDev_rev0、beta.10、+build.10 | 先頭v、省略桁、Release/Stable別名、suffix自然順、現在チャンネルによる制限。テスト時だけ公開日時順 |
| Scheduler | ビルド1.2.0-IntDev、code 21。v1.1.1-Release、v1.0.0-RC0〜RC6、v0.9.x-Beta。パーサーはscheduler_/nittc_/compactも許容 | 独自比較、現在の全チャンネル候補、ai-runtime-*除外 |
| GlyphVisualizer | ビルド2.1.1-IntDev_rev0、code 15。v2.1.0-Release、v1.3.0-IntDev等。パーサーはglyphbarty_/compactも許容 | GlyphBarty配信先、GitHub latest、旧候補比較、自動確認OFF、Nothing UIとABOUTへの戻り |

compact形式の210は旧2本では[2,1,0]、ShutUpの100は数値100です。一律に同じパーサーを適用しません。接頭辞の互換性はソースからの確認であり、その全形式が配布済みという意味ではありません。ローカルtagを読み、fetchやremoteへの変更は行っていません。

Schedulerのai-runtime-0.4.0-r1はネイティブAIライブラリ配布です。APKが添付されても、候補・履歴・cache復元・比較無視に混入させません。除外は常時適用するアプリ側releaseFilterに置き、Goldy内部へScheduler固有名を埋め込みません。

## 3. 安全性の基準

ShutUpのHTTPS/配信元検査、私有cacheの.part、サイズ/digest検査、実インストール情報に基づくpackage/署名/code照合、取消/清掃、専用Providerを基準にします。

| 現状の不足 | Goldyの要求 |
| --- | --- |
| 元URLのみの検査 | repositoryだけでなく対象tag/assetも照合。自動redirectを止め、各hopをHTTPSと許可hostで検査 |
| digestが任意で、不正形式も未提供扱いになる | 未提供と不一致を区別。提供されている不正形式は拒否。digest一致で署名検査を省略しない |
| Content-Lengthのみ | asset.size・HTTP長・実受信量の一致。空/不完全/上限超過/破損を拒否 |
| 同package・署名・新しいcodeでも別ReleaseのAPKが通り得る | 候補tagとAPK versionNameの正規化した**同一性**を照合。比較上の同順位とは区別 |
| 複数署名でany一致 | 現在署名者の集合全体が一致すること。初版では鍵ローテーションを履歴交差だけで自動許可しない |
| Fileの再利用だけで再install | 検証済みハンドルを使い、起動直前に実ファイルと最新のインストール済み情報を再検査 |
| cancel後もI/Oが残り得る | Cancelling中は次の転送を始めず、接続/stream終了と.part削除後にCancelled。古いJobの完了を採用しない |
| API差 | SchedulerのminSdk26に対応。26/27の旧署名API、28/29のarchive flag互換、33以降のflags APIを回帰試験 |

Androidでは複数署名は署名者集合で識別され、単独署名のローテーション履歴とは別です。[SigningInfo](https://developer.android.com/reference/android/content/pm/SigningInfo)

archive解析では公開PackageManager APIに署名情報取得を要求し、解析不能・署名情報欠損を拒否します。AOSPには署名収集時の検証経路がありますが、対応API/OEMでのinstrumented検証が必要です。最終的なOSインストーラー判定も残します。[PackageManager](https://developer.android.com/reference/android/content/pm/PackageManager)、[AOSPの検証経路](https://android.googlesource.com/platform/prebuilts/fullsdk/sources/android-31/+/refs/heads/androidx-main/android/content/pm/parsing/ParsingPackageUtils.java)

初版は現在と同じcodeを許容し、下位codeをテスト時も拒否します。Release APIに期待versionCodeはないため、タグから推測しません。特定Releaseの期待codeを厳密に確認する必要があれば、アプリが対応値を渡すAPIを設けます。

Providerは検証済みAPKのディレクトリだけを公開可能にします。Schedulerの既存ProviderはQR等にも使うため、Goldy導入を理由に一括変更・削除しません。[公開範囲の注意](https://developer.android.com/privacy-and-security/risks/file-providers)

## 4. v0.1.0の範囲

| 機能 | 方針 |
| --- | --- |
| 配信 | 公開github.comのGitHub Releases。owner/repo指定。埋込みPAT不要 |
| 確認 | 手動・ホストの起動等の呼出し。標準8時間、版変更/時計巻戻り、成功cache、条件付きHTTP、レート制限 |
| 取得範囲 | 標準100件×最大5ページ。打切り時は履歴不完全を示し「更新なし」を確定したと装わない。latestも選択可能 |
| 比較/選択 | 標準の数値＋suffix、チャンネル、除外述語、APK選択。3本の互換アダプターを用意できるAPI |
| 通知 | 更新候補・通知対象・バナー表示の分離。一時非表示、永続無視、無視解除、通知OFF。手動詳細を残す |
| 開発者テスト | 実版IntDevのみ比較/間隔無視・比較用現在版。安全性検査は解除しない |
| 詳細/中間版 | 候補と現在版の間の適格Release。空ノート/APKなし/取得範囲不足に対応。全過去履歴ブラウザーは初版外 |
| APK転送 | 同時1件、進捗/総量不明、cancel、再試行、上限、.part清掃、私有領域 |
| 検証 | URL/hop、サイズ、提供digest、archive、同package、現在署名者集合、code下限、tagとAPK版、起動直前の再検査 |
| installer | 許可確認・設定/installer Intent準備。Activity起動・戻り先はホスト所有。起動と更新完了を区別 |
| Compose | 任意M3モジュール。Markdown＋table、中間ノート、転送・エラー、日/英の標準文言。MaterialTheme・slotsでアプリの見た目を継承 |
| 対応下限 | core/composeともminSdk26。ShutUp28、Scheduler26、Glyph33を満たす |
| 非対象 | GitHub以外、private/Enterprise、silent/root/Shizuku install、split/APKS/AAB、差分更新、range/resume、鍵ローテーション自動受入 |
| 常駐処理 | 更新専用WorkManager/Service/Alarm/OS通知を追加しない。ホスト所有Scopeで処理 |
| 継続性 | ViewModel所有で構成変更に対応。プロセス終了後の転送再開・installer自動再起動は行わない |
| Pyrite | 依存・変更なし。ホストから組み合わせることは可能 |

標準は「バージョン最大＋InstalledTrack」。Scheduler移行は「全チャンネル＋独自除外」、Glyph移行は「GitHub latest」とします。既存3本に標準をそのまま押し付けません。

標準APK選択は単一APK、またはuniversalを一意に選べる場合です。曖昧なら勝手に先頭を選ばずReleaseページを案内します。既存3本の名前優先は明示的なSelectorにします。同点の複数候補を停止することは意図的な安全性の改善です。ABIをファイル名だけから一般推測する仕組みは作りません。

## 5. 削減できる重複処理

| 共通化先 | まとめられる処理 | アプリに残す処理 |
| --- | --- | --- |
| coreの取得/DTO | HTTP接続、JSON、エラー、Release/assetモデル | repository、アプリ名、Schedulerの除外述語 |
| coreの比較/候補 | 数値/suffix/チャンネル比較、候補順位、中間版抽出 | 既存prefix/compact/旧比較の互換アダプター、受入方針 |
| coreの状態/保存 | ShutUpとSchedulerのStore/Repository/Mutex/StateFlow、cache JSON、成功時刻、バナーと無視 | 既存ViewModel/UiState結合、表示可能な画面/操作状態、旧設定移行 |
| coreの転送/検証 | .part、進捗、清掃、ハッシュ、PackageManager照合 | APK選択、任意の期待code |
| coreのinstaller | 許可/installer Intent、Provider URI、起動エラー | Activityによる実行、Manifestの明示許可、ナビゲーション |
| composeの詳細 | 版/チャンネル、ノート/table、中間版、進捗、cancel/retry/error | MaterialTheme、Nothingフォント、loading部品、文言、戻り先 |
| composeの任意通知部品 | バナー/バッジ/確認ダイアログ | 配置、他ダイアログとの優先順位、既存部品を残す選択 |
| 薄いホスト層 | 更新固有のBuildConfig/SharedPreferences参照を集約 | アプリ全体の設定正本、PyriteのAbout/設定構成 |

測定対象はShutUp 12ファイル888行、Scheduler 5ファイル1,183行、GlyphVisualizer 3ファイル724行、合計**20ファイル2,795行**です。import・空行を含む現在の対象コード量で、削除確定行数や純削減量ではありません。薄いアダプター・UI slots・設定移行が残るため、純削減は後の移行差分で測ります。

InstalledAppVersionやチャンネル識別が他機能からも使われていれば、更新部分だけを委譲する互換関数を残します。PyriteのAboutカード・設定部品・一般画面遷移はGoldyへ移しません。

## 6. 検証状況と次のゲート

静的に確認したのはHEAD、既存差分、主要実装、呼出し、設定キー、Provider/Manifest、既存テストの意図、ビルド上のminSdk/版、20ファイルの内容と規模です。

実施していないものはビルド、テスト実行、実機/エミュレーター操作、実ダウンロード/インストール、ライブRelease取得、AAR互換性/サイズ測定です。既存ツリーを変更しないため、Phase 1では実行しません。

既存3本のLICENSE先頭はMIT Licenseです。後の抽出時は著作権表示を維持し、GoldyのLICENSE/NOTICEと依存ライセンスを整備します。今回はコード複製・ライセンス変更をしていません。

実装計画は、承認→Goldy内で特性テスト/モジュール→取得/状態→転送/検証/installer→Compose→Goldy内の消費側検証、の順です。**既存アプリの移行は別途の承認対象**とし、ShutUp→Scheduler→Glyphの順を提案します。詳細と受入条件は設計文書§§8–10にあります。

承認前に本体実装を始めず、承認だけで既存3アプリやPyriteの変更まで許可されたとは扱いません。

## 7. 主な調査ソース

以下は調査したローカル作業ツリーです。将来の変更に備え、HEADと記録JSONの内容ハッシュを併用してください。

| アプリ | ファイル | 行数 |
| --- | --- | --- |
| ShutUp | [GitHubReleaseChecker.kt](D:/apps/shutup/app/src/main/java/jp/linkserver/shutter/GitHubReleaseChecker.kt:1) | 97 |
| ShutUp | [ReleaseVersion.kt](D:/apps/shutup/app/src/main/java/jp/linkserver/shutter/ReleaseVersion.kt:1) | 51 |
| ShutUp | [ReleaseChannel.kt](D:/apps/shutup/app/src/main/java/jp/linkserver/shutter/ReleaseChannel.kt:1) | 19 |
| ShutUp | [InstalledAppVersion.kt](D:/apps/shutup/app/src/main/java/jp/linkserver/shutter/InstalledAppVersion.kt:1) | 35 |
| ShutUp | [AppUpdateRepository.kt](D:/apps/shutup/app/src/main/java/jp/linkserver/shutter/update/AppUpdateRepository.kt:1) | 66 |
| ShutUp | [AppUpdatePreferences.kt](D:/apps/shutup/app/src/main/java/jp/linkserver/shutter/update/AppUpdatePreferences.kt:1) | 76 |
| ShutUp | [UpdateDeveloperSettings.kt](D:/apps/shutup/app/src/main/java/jp/linkserver/shutter/update/UpdateDeveloperSettings.kt:1) | 17 |
| ShutUp | [AppUpdateViewModel.kt](D:/apps/shutup/app/src/main/java/jp/linkserver/shutter/update/AppUpdateViewModel.kt:1) | 42 |
| ShutUp | [UpdateDownloadViewModel.kt](D:/apps/shutup/app/src/main/java/jp/linkserver/shutter/update/UpdateDownloadViewModel.kt:1) | 121 |
| ShutUp | [AppUpdateUi.kt](D:/apps/shutup/app/src/main/java/jp/linkserver/shutter/ui/AppUpdateUi.kt:1) | 117 |
| ShutUp | [UpdateOverviewContent.kt](D:/apps/shutup/app/src/main/java/jp/linkserver/shutter/ui/UpdateOverviewContent.kt:1) | 199 |
| ShutUp | [ReleaseNotesMarkdown.kt](D:/apps/shutup/app/src/main/java/jp/linkserver/shutter/ui/ReleaseNotesMarkdown.kt:1) | 48 |
| NITTC Scheduler | [GitHubReleaseChecker.kt](D:/apps/schedulernittc/app/src/main/java/jp/linkserver/nittcsc/update/GitHubReleaseChecker.kt:1) | 351 |
| NITTC Scheduler | [AppUpdateRepository.kt](D:/apps/schedulernittc/app/src/main/java/jp/linkserver/nittcsc/update/AppUpdateRepository.kt:1) | 74 |
| NITTC Scheduler | [AppUpdatePreferences.kt](D:/apps/schedulernittc/app/src/main/java/jp/linkserver/nittcsc/update/AppUpdatePreferences.kt:1) | 103 |
| NITTC Scheduler | [UpdateOverviewScreen.kt](D:/apps/schedulernittc/app/src/main/java/jp/linkserver/nittcsc/ui/UpdateOverviewScreen.kt:1) | 580 |
| NITTC Scheduler | [AppUpdatePrompt.kt](D:/apps/schedulernittc/app/src/main/java/jp/linkserver/nittcsc/ui/AppUpdatePrompt.kt:1) | 75 |
| GlyphVisualizer | [GitHubReleaseChecker.kt](D:/apps/glyphvisualizer/app/src/main/java/jp/linkserver/glyphvisualizer/update/GitHubReleaseChecker.kt:1) | 261 |
| GlyphVisualizer | [AppUpdateRepository.kt](D:/apps/glyphvisualizer/app/src/main/java/jp/linkserver/glyphvisualizer/update/AppUpdateRepository.kt:1) | 102 |
| GlyphVisualizer | [UpdateOverviewScreen.kt](D:/apps/glyphvisualizer/app/src/main/java/jp/linkserver/glyphvisualizer/ui/UpdateOverviewScreen.kt:1) | 361 |

呼出し・固有条件の追加根拠:

- ShutUp: [MainActivity](D:/apps/shutup/app/src/main/java/jp/linkserver/shutter/MainActivity.kt:74)、[ShutterApp](D:/apps/shutup/app/src/main/java/jp/linkserver/shutter/ui/ShutterApp.kt:88)、[AppLinks](D:/apps/shutup/app/src/main/java/jp/linkserver/shutter/AppLinks.kt:1)、[開発者設定UI](D:/apps/shutup/app/src/main/java/jp/linkserver/shutter/ui/DeveloperUpdateSettingsSection.kt:17)、[Manifest](D:/apps/shutup/app/src/main/AndroidManifest.xml:20)。
- Scheduler: [SchedulerViewModel](D:/apps/schedulernittc/app/src/main/java/jp/linkserver/nittcsc/viewmodel/SchedulerViewModel.kt:139)、[SettingsScreen](D:/apps/schedulernittc/app/src/main/java/jp/linkserver/nittcsc/ui/SettingsScreen.kt:881)、[NittcSchedulerApp](D:/apps/schedulernittc/app/src/main/java/jp/linkserver/nittcsc/ui/NittcSchedulerApp.kt:829)、[AI runtime除外](D:/apps/schedulernittc/app/src/main/java/jp/linkserver/nittcsc/logic/AiRuntimeSpec.kt:49)、[Provider公開範囲](D:/apps/schedulernittc/app/src/main/res/xml/file_paths.xml:1)。
- Glyph: [VisualizerApp](D:/apps/glyphvisualizer/app/src/main/java/jp/linkserver/glyphvisualizer/ui/VisualizerApp.kt:259)、[SettingsScreen](D:/apps/glyphvisualizer/app/src/main/java/jp/linkserver/glyphvisualizer/ui/SettingsScreen.kt:358)、[既存設定キー](D:/apps/glyphvisualizer/app/src/main/java/jp/linkserver/glyphvisualizer/SettingsSchema.kt:639)、[自動確認既定OFF](D:/apps/glyphvisualizer/app/src/main/java/jp/linkserver/glyphvisualizer/CaptureUiState.kt:56)、[配信URL](D:/apps/glyphvisualizer/app/src/main/res/values/strings.xml:57)、[Provider公開範囲](D:/apps/glyphvisualizer/app/src/main/res/xml/file_provider_paths.xml:1)。

既存テストの主な根拠:

- [ShutUp ReleaseUpdateTest](D:/apps/shutup/app/src/test/java/jp/linkserver/shutter/ReleaseUpdateTest.kt:10)、[ReleaseVersionTest](D:/apps/shutup/app/src/test/java/jp/linkserver/shutter/ReleaseVersionTest.kt:6)、[UpdateDeveloperSettingsTest](D:/apps/shutup/app/src/test/java/jp/linkserver/shutter/update/UpdateDeveloperSettingsTest.kt:6)、[RepositoryTest](D:/apps/shutup/app/src/test/java/jp/linkserver/shutter/update/AppUpdateRepositoryTest.kt:8)、[実APK検証テスト](D:/apps/shutup/app/src/androidTest/java/jp/linkserver/shutter/ui/AppUpdateUiTest.kt:180)。
- [Scheduler RepositoryTest](D:/apps/schedulernittc/app/src/test/java/jp/linkserver/nittcsc/update/AppUpdateRepositoryTest.kt:21)、[AI runtimeタグ除外テスト](D:/apps/schedulernittc/app/src/test/java/jp/linkserver/nittcsc/logic/AiRuntimeSpecTest.kt:24)。
- [Glyph チャンネルテスト](D:/apps/glyphvisualizer/app/src/test/java/jp/linkserver/glyphvisualizer/update/GitHubReleaseCheckerTest.kt:6)、[設定互換性テスト](D:/apps/glyphvisualizer/app/src/test/java/jp/linkserver/glyphvisualizer/SettingsPreferencesCharacterizationTest.kt:78)。

ビルド値: [ShutUp](D:/apps/shutup/app/build.gradle.kts:24)、[Scheduler](D:/apps/schedulernittc/app/build.gradle.kts:395)、[Glyph](D:/apps/glyphvisualizer/app/build.gradle.kts:50)。各リポジトリ内のAGENTS.mdも確認し、既存変更保持・設定互換性・生成物非変更の指示に従います。
