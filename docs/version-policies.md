# バージョン比較とホスト別ポリシー

Goldyの4つのホスト向けpresetは共通のバージョン比較を使います。
取得対象、公開channel、APKの選択と本人性の検証は独立した契約です。

| ホスト | Metadata policy | 比較 |
|---|---|---|
| ShutUp | `UpdatePolicies.shutUp()` | `VersionPolicies.Common` |
| NITTC Scheduler | `UpdatePolicies.nittcScheduler()` | `VersionPolicies.Common` |
| Glyph Barty | `UpdatePolicies.glyphBarty()` | `VersionPolicies.Common` |
| BeastLocator | `UpdatePolicies.beastLocator()` | `VersionPolicies.Common` |

ホスト固有のfeed、release filter、APK命名、ABI選択はpresetまたはホストの設定で維持します。
SchedulerのAI Runtime除外と、Glyphのlatest feedを、比較順序から推測して変更しないでください。

## 共通比較の確定仕様

`VersionPolicies.Common` は`compareReleaseVersions()` の比較規則を使用します。テストでは固定した参照実装、実タグ、境界値を照合します。`_` / `-` / `.` で区切り、文字と数字の境界で分割してrevを数値比較します。

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

実タグ・Gradle現在版の出典は [four-app-versions.json](../gold/src/test/resources/four-app-versions.json) にあります。これはGitHub上の全Releaseや配布APKの全数調査ではありません。

### 公開APIと旧規則の差

`VersionPolicies.Standard/NittcScheduler/GlyphBarty/BeastLocator` はCommonを指します。既存getterのJVM署名を維持します。`VersionPolicies.ShutUp` も比較結果は同じで、従来のchannel表示分類を直接使うホスト向けにその分類を残します。4つの `UpdatePolicies` presetはCommonの解析に基づくchannel分類を使います。

Schedulerのrev無視・RCをBetaより下にする順序、GlyphのBeta2/Beta10同値、compactの `100 == 1.0.0` や独自prefix許容は共通APIでは終了します。`100` は数値100として扱い、`scheduler_120` のような構文外tagは拒否します。Beastのchannel連番=rev同値化、missing=0、RC<Betaも採用しません。旧Scheduler/Glyph比較実装は、共通APIとの違いを確認するテスト参照実装として保持しています。

presetのidにより、異なる比較規則で選択したcacheを区別します。無視tagは保持します。CommonはBeta/IntDevという単語をsuffixの途中やmetadataに含むだけのtagをそのchannelと誤認しません。通常の既存ShutUpタグに対する比較・installed-track条件は回帰検証します。


## ホストへの接続

- 通信、更新状態、通知の非表示とタグの無視はGoldのAPIへ接続します。
- 転送とインストールの操作はホストが所有し、画面遷移やUI再描画から重複実行しないようにします。
- パッケージ、完全な署名集合、versionCode、tagとAPKの対応、size/digestを検証します。比較の同値性はAPK本人性の代わりになりません。
- `InstallRequest`のURI寿命とインストール結果の照合は[インストーラーAPI](installer-api.md)に従います。

公開APIの所有権は[API契約](public-api-contracts.md)、通知UIの接続は[更新通知](update-notifications.md)を参照してください。
