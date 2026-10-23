# Elief標準UI改善・最終検証

実施日：2026-10-23。開始時HEADは `ef483970f31efc5d2dcb6560c229c6e71a464601`（master）。Goldyだけをローカルで変更しました。Commit・Push・タグ・Release・JitPack再公開は行っていません。

## 実装結果

- 戻る操作を矢印IconButtonへ変更。文言・タイトルはUpdateStrings、アイコンは追加のUpdatePresentationで変更できます。イベントとシステムBackはホストが所有します。
- `v2.4.0-Beta` と `Beta` の非操作Chipを横並びにしました。標準表示はraw tagを維持し、任意のversionLabelで先頭vだけを表示上除けます。比較・APK検証の入力は変わりません。
- MarkdownをsurfaceContainerHigh、large shape、outlineVariantのSurfaceへ配置。見出し、18/14dp外余白、16dpノート内余白、18dp間隔を既存Composeアプリに寄せました。ReleaseNotesの安全な描画処理は変更していません。
- 中間履歴は件数・矢印付きの全体展開に変更。カードごとにversion/channel/notesを表示し、展開状態とスクロール位置は選択Release単位で再生成後も復元します。履歴ゼロではセクションを出しません。
- 通常は操作をbottomBarへ固定。Download、Cancel、Install、Permission、Retry、進捗・エラー・HandedOff案内を表示します。APKがない場合はReleaseページへ案内し、失敗後もリンクを残します。HandedOffを成功扱いにする処理はありません。
- 内容と操作の最大幅は840dp。fontScale >= 1.5または使用可能高 < 480dpでは、操作を同じスクロールの上部へ移します。長いエラー等を持つ通常時bottomBarも画面高40%を上限にスクロールできます。IME/nav bar/ScaffoldのInsetsを適用しています。
- ホストMaterialThemeの色・shape・字体を継承。Elief独自テーマ・Pyrite依存・新権限は追加していません。

既存のUpdateOverviewScreen引数・JVM descriptorを残し、presentationが必須となる別overloadだけを追加しました。UpdateUiState、UpdateTransferState、UpdateAction、UpdateStrings、ReleaseNotes、GoldyState adapterは変更していません。UpdatePresentationの3項目は表示用version、履歴channel、戻るアイコンです。使い方は [Compose API](compose-api.md) に記載しました。

## 既存画面との差と比較資料

4アプリのソース・UI・更新処理の比較表と導入判断は [BeastLocator調査](beastlocator-investigation.md) にまとめています。

ShutUpの既存 `app/build/update-port/ja/update-design/markdown-intermediate.png` と、今回のElief `build/elief-ui-review/api36-normal-final/elief-ui-review/width-and-insets.png` を目視比較しました。矢印、横並びversion/channel、ノートの背景・境界・余白、まとめた履歴、固定下部操作が対応しています。文言・ノート内容・ホスト色・試験画面サイズは異なり、画像の画素一致は評価していません。既存アプリを起動・ビルド・インストールせず、保存済み画像を読みました。

Eliefの以下の違いは意図したものです。

- 文字拡大・低い画面では、既存アプリの常時固定操作を維持せず一体スクロールに適応します。
- channelは操作のないラベル、進捗は既存Eliefのbytes/attempt表記です。ダウンロードとインストールの操作も分離した既存契約を維持します。
- 字体、色、ボタンのアイコン、個別文言を既存アプリへ完全固定せずホストテーマ/APIに従います。
- GlyphのNothingデザインは字体・テーマ・slotで調整できます。Beastのgradient/XML/縦versionパネルは標準UIと異なり、既存slotだけで完全再現できるとは判断していません。

確認画像は無視対象の `build/elief-ui-review/` に置いています。

| 確認 | 画像・記録 |
|---|---|
| 最終通常表示・固定操作 | api36-normal-final/elief-ui-review/width-and-insets.png |
| 1000×700dp・最大840dp | api36-wide1000/elief-ui-review/width-and-insets.png |
| M3/M3E Light/Dark、Dynamic Light/Dark、字体 | api36-180-capture-theme-final/phase-2-4/{light,dark,expressive-light,expressive-dark,dynamic-light,dynamic-dark,custom-font}.png |
| 履歴・再生成 | api36-180-capture-theme-final/phase-2-4/history-restored.png |
| 大きい文字・IME | api36-normal-final/elief-ui-review/{adaptive-large-font,ime-actions}.png（倍率1.8の実行で生成され、後の回収時に保存） |

各実行スクリプトは端末上の画像フォルダー全体を回収します。フォルダー中の全画像がその実行で更新されたとは限らないため、上表では実際に対象テストが生成した画像を指定しています。

## Goldy Demo

既存Showcaseの更新あり/なし、長文Markdown、ダウンロード、失敗、キャンセル、権限不足、APKなしを維持し、「No history」を末尾へ追加しました。既存fixture番号0〜11は変えていません。通常M3/M3E、Light/Dark、Dynamic Color、フォント切替はそのまま新Elief画面へ適用されます。

Live DemoとUpdate Demoの本体・更新エンジンを変更せず、既存の3件のDemo UIテストでナビゲーション、Activity再生成、他アプリのmetadata専用表示、自身の実identityと未設定配布表示、fixtureキャンセル再試行を確認しました。実GitHub通信の既存Phase 2-6成功記録は履歴として維持し、今回再通信して成功したとは扱っていません。自己更新Releaseや署名鍵は作成していません。

## 検証結果

機械可読な集計は [verification/elief-ui-results.json](../verification/elief-ui-results.json)。生ログ・保護照合の全記録は `build/elief-ui-review/` です。

| 対象 | 結果 |
|---|---|
| Gold JVM | 既存103件、失敗0・エラー0 |
| Elief JVM | 既存10件＋表示customization 2件、失敗0・エラー0 |
| Demo JVM | 既存6件、失敗0・エラー0 |
| Demo Debug / AndroidTest APK | 成功 |
| Demo Release | R8有効で成功 |
| Elief lint Debug/Release | エラー0・警告0 |
| Demo lint Debug/Release | エラー0、各警告9。既存Phase 2-6と同数（RedundantLabel 1、UnusedResources 7、UseKtx 1） |
| 独立consumer | ローカル公開AARのみでRelease AARビルド・リソース検証成功。project/includeBuild参照なし |
| consumer依存 | Eliefと推移GoldのAARハッシュが生成元と一致、stable Material3 1.4.0、Pyriteなし |
| API descriptor | 旧UpdateOverviewScreenのdescriptor 1件を保持、欠落0。既存呼び出しと新overloadの両方をconsumerでコンパイル |
| R8実行 | 既存debug鍵で無視対象のローカルコピーに署名、専用emulatorへインストール・cold start成功。version/Back/Download表示とプロセス生存を確認 |

Compose UIは既存UpdateScreenTest 12件、DemoScreenTest 3件、新EliefLayoutTest 7件の計22種類を対象にしました。通常320×640dp・文字倍率1.0で当時の21件が成功しました。文字倍率1.8では20/21件が成功し、1件だけ画像取得のPixelCopy待機timeoutでした。このケースは単独再実行で成功し、追加したM3E Light/Dynamic Lightを含むtheme試験も成功しました。

最終変更後は影響範囲だけを確認しました。通常表示と1000dp幅の各1件、APKなし＋失敗の追加ケースを倍率1.0/1.8で各1件実行し、すべて成功しました。全22件を最終差分後に再一括実行した記録ではありません。単独成功の `OK (1 test)` を既存スクリプトが失敗扱いする集計不具合も修正しました。

機能として戻る・system Back委譲、Cancel、Retry、二重操作guard、permission、Markdown安全性、履歴展開/再生成/scroll、低い300dp領域、320dp幅・倍率1.8、1000dp幅・最大840dp、実IME表示/非表示、WindowInsetsを確認しています。操作はfixtureまたはcallback試験であり、実APKインストール成功の確認ではありません。

今回起動したAPI36の読み取り専用・snapshot非保存エミュレーターだけで実行しました。接続された実機は使用していません。各テストスクリプトはfont、画面サイズ/density、IME設定をfinallyで復元します。

### 成果物サイズ

| 成果物 | bytes |
|---|---:|
| Demo Debug APK | 13,132,165 |
| Demo R8 Release APK（unsigned） | 1,483,746 |
| Gold Release AAR | 280,404 |
| Elief Release AAR | 80,600 |

最終ファイルのSHA-256は検証JSONに記録しました。Release署名済みsmoke用コピーは公開成果物ではありません。既存のJitPack成果物・座標は今回更新していません。

## 作業保護

ShutUp、Scheduler、Glyph、BeastLocator、PyriteのHEAD・branch・status・index、Git対象と未追跡ファイル計1,004エントリー（元から欠落する3件を含む）を開始時のハッシュへ照合し、変更0を確認しました。ShutUp/Pyriteの既存変更、Beastのステージ済み削除3件もそのままです。無視済みキャッシュ全体の照合ではありません。

GoldyのHEAD/index、Gold全体、既存公開state/adapter/Markdown実装、Live/Update Demo本体、READMEも変更していません。今回の必要差分は未ステージのままです。破棄・reset・clean・他アプリのビルドや実行はしていません。

## 残る制限とPhase 3

- 今回のUI差分はAPI26〜35・実機・実配布APKの更新で未検証です。API36でのfixture/Insets/再生成確認から全端末の成功は推測しません。
- 新UIはローカル成果です。第三者がこの新UIをJitPackから取得できる状態ではありません。既存公開版は維持し、公開は次の承認済み段階で確定SHAを用います。
- BeastLocatorはそのまま既存3アプリ用Policyへ載せ替えられません。rev/RCを持つVersionPolicy、ABI選択、APK版binding、ignore/試行時刻設定移行、compileSdk37対応が必要です。前方署名rotationは現Gold契約との差があるため先に判断します。詳細な根拠と9段階の計画は調査書へ分離しました。
- Phase 3は予定どおりShutUpを先行し、その後Scheduler、Glyph、Beastを個別に進めるのが妥当です。Scheduler/Glyphでは画面Coroutineの転送をViewModel所有へ移し、GlyphのNothing字体・権限・履歴なしを保持します。BeastはGoldのみ＋native UI維持も選択肢です。既存アプリへの移行コードは今回含めていません。
