# Elief / Pyriteスタイル統一

実施日：2026-10-23。Goldy開始時HEADは `be3d5f90674a6178150fef0c1df032d1a4f5ab3d`、master、cleanでした。前回のElief改善はこの作業ツリーに取り込まれています。Pyriteは `508b1cf5131679fee7581acbc7ea55a365b6ff2c`、master、既存の `docs/shutup-migration-plan.md` 変更1件を持つ状態で読み取りました。

今回もCommit・Push・タグ・Release・JitPack再公開はしていません。README、Goldの更新処理、既存4アプリ、Pyriteは変更対象にしていません。

## Pyriteの正本と依存版

テーマ、SourceColors、SourceTypography、PyriteFonts、PyriteDefaults、SettingsScreen、SettingsGroup、SettingsInfoCard/SettingsActionCard、UIカタログ実装・既存画像を読み取りました。

最新ソースに対応する公開座標は `com.github.Link2011-Act2:Pyrite:508b1cf5131679fee7581acbc7ea55a365b6ff2c` です。既存JitPack APIのstatus=ok/private=falseを確認した後、既存AAR/POM/sources/metadataを取得しました。新規ビルド要求はしていません。公開sourcesのKotlin 17ファイルが現在のソースと一致し、Demoが解決したキャッシュAARも今回取得した公開AARとSHA-256が一致しました。

Pyrite AARは207,801 bytes、SHA-256は `57493fdcf23a8f60a67f78ba402d8c714fc723ed72e0ba16f31b92aee72e376c` です。古い4244bed/43557c8のAPIを想定せず、現行のPyriteFonts overloadとカードAPIを呼んでいます。

| 対象 | Compose / Material3 | 責務 |
|---|---|---|
| Gold | 変更なし | 取得・比較・APK検証・インストーラー |
| Elief | 既存BOM 2026.02.01 / UI 1.10.4 / stable M3 1.4.0 | 表示・イベント。公式Animationだけ追加 |
| Goldy Demo | UI・tooling・UI test 1.12.0 / M3 1.5.0-beta01 | 実PyriteTheme・PyriteFontsを適用 |
| 公開Pyrite | UI/Foundation/Animation 1.12.0 / M3 1.5.0-beta01 | 現行の公開POMとソースで確認 |

Kotlin Compose plugin 2.2.10、AGP 9.4.1、compileSdk37、minSdk26は維持しています。EliefにPyriteやMaterial3 betaの必須依存は追加していません。JitPackリポジトリはPyriteの単一moduleに限定しました。

## デザイン対応表

| 項目 | Pyriteの実装 | Elief / Demo |
|---|---|---|
| M3E Lightの背景 | surfaceContainerLowをsurface/backgroundへ適用 | PyriteThemeを直接使用。Scaffoldもsurface/onSurfaceへ統一 |
| M3E Lightのカード | surfaceContainerLowest | Demoから同じロールを供給。固定色の複製なし |
| Dark / Nothingカード | surfaceContainerHigh | 同じロールを供給 |
| カード形状・内余白 | 22dp角丸、22dp padding、14dp contentGap | Demoで22dp形状、Eliefの主要カード22dp padding・操作間隔14dp |
| 画面余白 | SettingsScreenの20dp横・18dp縦 | 同じ外余白。更新画面の節間は20dp、幅上限840dpを保持 |
| M3E文字 | Pyriteの強い見出し・通常本文 | ホストTypographyを継承。独自の太字上書きを除去 |
| Nothing文字・色 | SansSerif等のホスト役割、白黒/グレー、tertiaryのみ赤 | 実PyriteThemeのまま。primaryを赤へ差し替えない。フォント同梱なし |
| チャンネル | 非操作の意味を保持 | 小さいoutlineラベル。チェック・リップル・クリックactionなし |
| 主操作 | 公式Button、primary/onPrimary | 同じ公式Button。更新操作は56dp以上・全幅で強調 |
| 補足・状態 | onSurfaceVariant | ステータス文へ適用 |
| エラー | Materialの意味色 | errorContainer/onErrorContainerの組で可読性を確保。通常領域は着色しない |
| モーション | 公式Composeの展開・サイズ変化 | 履歴にexpand/shrink＋fade、矢印に回転。独自エンジン・固定タイマーなし |

矢印・横並びversion/channel・Markdown面・履歴の一括展開・下部操作・文字拡大時の一体スクロールという前回の構造は維持しました。操作の所有権はホストViewModelに残しています。

## APIと実装

既存のUpdateOverviewScreenの2 overload、UpdatePresentation、状態・文字列・Markdown・adapterの公開APIは維持しました。追加は `UpdateCardStyle(containerColor, shape)` と `LocalUpdateCardStyle` だけです。Pyriteのカード色選択はinternalなので、明暗・styleを所有するDemoがこのCompositionLocalへ渡します。Eliefが色の明度からNothing/Darkを推測する構造にはしていません。

指定なしでは既存同様にホストのsurfaceContainerHigh / shapes.largeを使います。公開ColorSchemeのSurfaceロールを書き換える必要はありません。表示文字列・チャンネル・カードの変更は、元tag、版比較、署名・package/versionCode検証に影響しません。使用例は [Compose API](compose-api.md) を参照してください。

## Demo

- UI Showcase / Live Demo / Update Demoを保持しました。通常M3も両styleを解除すれば使えます。
- Expressive / Nothing、Light / Dark、Dynamic、フォント切替を用意しました。PyriteFontsにホストのMonospaceを渡す例も維持しています。
- NothingではDynamic Colorを無効・OFF表示にし、配色には適用しません。M3EのON設定は保存して戻った際に復元します。
- Demoのstatus/navigation barアイコンも選択した明暗へ同期します。R8の画面全体で発見したDark時の視認性を修正したもので、Windowの操作はDemoだけが担当します。
- 「Style reference」は実際の公開SettingsInfoCard/SettingsActionCardを表示します。Preview actionはカウンターだけを操作し、APK転送は開始しません。
- ShowcaseにRelease / IntDevを末尾追加し、既存fixture 0〜12の番号は維持しました。Beta、Markdown・履歴、更新なし、cancel/retry/error/permission等もそのままです。
- 実通信・自己更新のViewModel、ファイル寿命、ApkInstaller連携は変更していません。HandedOffを成功扱いにしていません。

## 画像比較と評価

実画像を並べた [比較ギャラリー](../verification/pyrite-style-review/index.html) と [色・コントラスト記録](../verification/pyrite-style-review/theme-metrics.json) を保存しました。画像は加工せず、同一API36端末・320×640dpで生成しています。左の基準は今回取得した公開Pyriteコンポーネントを同じPyriteThemeで描画したものです。Pyriteアプリ自体の再ビルド・変更はしていません。既存カタログ画像は設計参照として別に確認しました。

| 手順 | 表示・操作 | 評価 |
|---|---|---|
| 1 | M3E / Nothing × Light / DarkとDynamicの更新画面 | 本文・version・badge・操作を区別でき、横方向のはみ出しなし。通常画面で赤い面を増やしていない |
| 2 | 同条件のPyrite実カードと比較 | 6テーマすべてでカード塗りの実画素が一致。形状・余白・文字の役割も画像で比較 |
| 3 | 履歴の展開、テーマ変更、Activity再生成 | 展開とscrollを保持。色と字体だけを切り替えられる |
| 4 | 320dp・fontScale1.8 / 1000dp幅 / IME | 大文字は折り返し、操作はスクロールで到達可能。大画面は幅840dp以内。固定操作も維持 |
| 5 | Nothingのcancel/retry/error/permissionと参考画面 | 操作callbackと独立性を確認。エラーのみ意味色、主操作は白黒で明確 |

| テーマ | 背景 / カード | 本文contrast | 主操作contrast |
|---|---|---:|---:|
| M3E Light | #E2EBF0 / #F2F8FB | 16.11 | 7.29 |
| M3E Dark | #0E1519 / #26313A | 10.26 | 7.96 |
| Nothing Light | #EDEDED / #FFFFFF | 21.00 | 21.00 |
| Nothing Dark | #000000 / #121212 | 18.73 | 21.00 |
| Dynamic Light（試験端末） | #F4F3FA / #FFFFFF | 17.19 | 6.46 |
| Dynamic Dark（試験端末） | #121318 / #282A2F | 11.14 | 7.77 |

本文・補足・主操作・errorの色の組合せを検査しました。errorContainer/onErrorContainerはLight12.77、Dark7.17です。これは測定したSurfaceと文字の色の組合せで、全端末・全壁紙・すべての描画文字の完全なアクセシビリティ適合判定ではありません。

## 検証記録

最終集計は [verification/pyrite-style-results.json](../verification/pyrite-style-results.json)、生ログは無視対象 `build/pyrite-style-review/` に保存しました。

- Gold JVM既存103件、Elief既存12件、Demo既存6件が成功しました。
- 最終配色の通常320dpでUI26件、320dp/倍率1.8で影響範囲14件、1000dp幅で2件が成功しました。旧UIの画像取得を含む全ケースを倍率ごとに繰り返してはいません。
- 初回の新規4件中1件は、disabledを持たない子Textを検査するselector不備でした。Chip本体を検査するよう修正し、最終26件で成功しました。製品の検査条件は緩めていません。
- 最後のDynamic OFF表示・M3E設定復元は通常/倍率1.8で各1件を追加確認しました。最初の試行はテスト開始前の起動ANRで終了し、ApplicationExitInfoのfailed to complete startupを保存しました。ビルドと端末背景負荷を止めた再実行は両方成功しています。失敗した試行のログも残しています。
- システムバー修正後も6テーマの明暗フラグを通常/倍率1.8で各1件確認し、R8の全画面画像でDark時の時計・アイコンが白く表示されることを確認しました。初期のContextキャストはlintで指摘されたため、公式LocalActivityへ修正しています。抑制・baseline追加はしていません。
- Screenshotと機能試験で、戻る、下部操作、スクロール、再生成、履歴、font、Markdown、二重操作、cancel/retry/permission、IME/Insetsを確認しました。
- 独立ConsumerにはPyriteを与えず、stable Material3で両overloadとカード設定をコンパイルしました。R8はDemoアプリで別に検証しています。
- PyriteのMIT帰属をDemo用依存としてライセンス資料へ追加しました。独自フォント、秘密鍵、新しい権限は追加していません。

| 最終確認 | 結果 |
|---|---|
| Debug / テストAPK / Release（R8有効） | 成功 |
| Elief lint Debug/Release | エラー0・警告0 |
| Demo lint Debug/Release | エラー0・既存警告9（RedundantLabel 1、UnusedResources 7、UseKtx 1） |
| 独立Consumer | Release AARビルド・リソース検証成功。producer project/includeBuildなし、解決AARのハッシュ一致 |
| 依存分離 | Consumerはstable M3 1.4.0、Pyriteなし。Elief内のPyrite classも0 |
| 公開API | 開始時AARの16型・147 JVM descriptorをすべて保持、欠落0 |
| R8実行 | 最終APKの既存debug鍵署名コピーでM3E/Nothing Darkの表示・切替・プロセス生存を確認。実インストール操作なし |
| ライセンス | Release APK内のMETA-INF/pyrite/LICENSE・NOTICE.mdを確認 |
| Demo Debug APK | 13,488,912 bytes |
| Demo R8 Release APK（unsigned） | 1,502,734 bytes |
| Gold / Elief Release AAR | 280,404 / 85,954 bytes |

最終Demoビルドログは `build/pyrite-style-review/final-window-build.log` です。署名済みR8 smokeコピーは無視対象のローカル確認用で、配布Releaseではありません。

## 保護と残る課題

開始時に6リポジトリのHEAD・branch・status・index・Git対象/未追跡ファイルのハッシュを記録しました。読み取り専用5リポジトリの1,004エントリー（元から欠落する3件を含む）を照合し、HEAD・branch・status・index・内容に変更0を確認しました。Gold、既存公開entry point/state/Markdown、README、GoldyのHEAD/indexもそのままです。既存Pyrite/ShutUpの変更、Beastのステージ済み削除も保護しています。無視済みキャッシュ全体の照合ではありません。

- EliefのAppBarはホストtitleLargeを使い、Pyrite固有の23sp Medium指定には固定していません。更新ボタンの56dp最小高もPyriteの通常Buttonとの差です。更新操作の到達性を優先した違いです。
- Markdownの見出し・表・コードは入力の構造に従います。長い単語、横長の表、任意の独自フォントはホスト固有の内容でも確認が必要です。Nothing専有フォントは今回も提供しません。
- 今回のUI差分はAPI26〜35・実機・TalkBack実操作・実APK自己更新では未検証です。Dynamic Colorの全壁紙や端末固有の配色を保証するものではありません。
- UI testの既存v1ルールはCompose1.12で非推奨警告がありますが、今回も使用可能で、既存試験を維持しています。
- 新UIはローカル成果です。Goldy/Pyriteの公開版を更新していません。既存4アプリへの移行は別作業です。
