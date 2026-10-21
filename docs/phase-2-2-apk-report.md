# Project Goldy Phase 2-2 実装報告

2026-10-21。正式モジュール名は `:gold`、Maven座標は `jp.linkserver.goldy:gold:0.1.0-SNAPSHOT`。Kotlinパッケージ `jp.linkserver.goldy` とPhase 2-1の公開APIは維持しました。将来のComposeモジュール名は `:elief` です。今回は作成していません。

## 実装範囲

- 旧モジュールのディレクトリ、settings、publication、独立consumer、設計文書の参照をgoldへ変更しました。過去の検証JSONと作業前baselineは当時の状態を示す証拠として旧名を残しています。
- 更新メタデータ取得、比較、チャンネル判定、通知無視、キャッシュの既存ソースと互換テストは内容を変更せず移動しました。
- APK選択、転送、進捗、サイズ上限、転送の直列化、キャンセル、再試行、一時ファイル清掃、APKの版・パッケージ・署名検証を追加しました。
- Pyrite、Compose、インストーラー、FileProvider、バックグラウンドサービスの依存・実装は追加していません。

## 公開APIと利用例

| API | 用途 |
| --- | --- |
| ApkDownloadConfig | repository、selection、versionBinding、maxBytes、maxAttempts、userAgent |
| ApkDownloader.create(context, config) | アプリ専用キャッシュと実際のインストール済みAPK情報を使用 |
| suspend download(release, onProgress) | 検証済みのVerifiedApkを返す。キャンセルは呼び出し元Jobをキャンセル |
| suspend cleanPartialFiles() | Goldy所有の放置された.partだけを清掃 |
| ApkProgress(bytes, total, attempt) | totalは不明ならnull。再試行ごとにbytesは0から |
| VerifiedApk | file、metadata、sha256、選択したasset |
| ApkSelectionPolicy / ApkSelectionPolicies | 単一APKの標準方針と3アプリ用方針 |
| ApkVersionBinding / ApkVersionBindings | 比較用VersionPolicyと独立した版同一性の方針 |
| ApkInspector / AndroidApkInspector | プラットフォーム依存の署名付きAPK解析境界 |
| ApkVerifier.verify | 純粋なpackage、署名者集合、code下限、版対応検証 |
| ApkException / ApkFailure | 呼び出し元向けの型付き失敗理由。HTTP時はstatusも取得可能 |

```kotlin
val repository = GitHubRepository("Link2011-Act2", "ShutUp")
val downloader = ApkDownloader.create(context, ApkDownloadConfig(
    repository = repository,
    selection = ApkSelectionPolicies.ShutUp,
    versionBinding = ApkVersionBindings.ShutUp,
))
val job = scope.launch {
    try {
        val verified = downloader.download(candidate.release) { progress ->
            // このコールバックはIOスレッド。StateFlow等へ格納し、UIはホスト側で描画します。
            downloadProgress.value = progress
        }
        // ファイル所有権はホスト側。内容を書き換えず、使用後にfile.delete()します。
        // 今回のライブラリにはインストール呼び出しはありません。
    } catch (error: CancellationException) {
        throw error
    } catch (error: ApkException) {
        downloadFailure.value = error.reason
    }
}
// キャンセル: job.cancel()
```

| アプリ | repository.name | selection | versionBinding |
| --- | --- | --- | --- |
| ShutUp | ShutUp | ApkSelectionPolicies.ShutUp | ApkVersionBindings.ShutUp |
| NITTC Scheduler | NITTC_Scheduler | ApkSelectionPolicies.NittcScheduler | ApkVersionBindings.NittcScheduler |
| Glyph Barty | GlyphBarty | ApkSelectionPolicies.GlyphBarty | ApkVersionBindings.GlyphBarty |

既定selectionはAPKが1個だけの場合に選び、複数ならNoAssetです。アプリ方針は確認済みのファイル名prefix（shutup_ / nittcsc_ / glyphbarty_）を優先し、一意に決まらない場合は拒否します。Schedulerのai-runtime-タグはAPK選択時にも除外します。ABI等の選択が必要ならselectionだけ差し替えられます。差し替えた選択結果もReleaseのasset集合への所属、URL、全検証を通ります。

versionBindingの既定値はExactです。3アプリ方針は公開APKで確認した3要素の数値版とチャンネル構文だけを許可し、小文字vを1文字だけ除いてversionName全体を厳密照合します。ShutUp/GlyphはRelease、SchedulerはRelease/Beta/RC数字のみです。コンポーネント数、大小文字、revision、build番号を捨てず、比較関数の0を同一性判定に使いません。

## 公式配布APKの調査

[ShutUp Releases](https://github.com/Link2011-Act2/ShutUp/releases)、[NITTC Scheduler Releases](https://github.com/Link2011-Act2/NITTC_Scheduler/releases)、[Glyph Barty Releases](https://github.com/Link2011-Act2/GlyphBarty/releases)の公開一覧を各100件で取得し、一覧が100件未満であることを確認しました。確認時点ではアプリAPKは3+17+8=28件です。Schedulerのai-runtime-0.4.0-r1はAPKではないため別扱いです。

公式URLからHTTPSでZIP内の実AndroidManifest.xmlを取得し、SDK 36.0.0のaapt2 dump badgingで解析しました。全APKをインストールせず調査しました。SDKツールのバージョン、取得方法、URL、Release/asset ID、GitHubのsize/digest、manifestの版情報をfixtureに記録しています。

全28件で、ReleaseタグとAPK versionNameの差はタグ側の小文字vだけでした。

| Repository | Releaseタグ | APK versionName | versionCode |
| --- | --- | --- | ---: |
| ShutUp | v1.1.0-Release | 1.1.0-Release | 4 |
| ShutUp | v1.0.1-Release | 1.0.1-Release | 3 |
| ShutUp | v1.0.0-Release | 1.0.0-Release | 2 |
| NITTC_Scheduler | v1.1.1-Release | 1.1.1-Release | 21 |
| NITTC_Scheduler | v1.1.0-Release | 1.1.0-Release | 20 |
| NITTC_Scheduler | v1.0.0-Release | 1.0.0-Release | 18 |
| NITTC_Scheduler | v1.0.0-RC6 | 1.0.0-RC6 | 17 |
| NITTC_Scheduler | v1.0.0-RC5 | 1.0.0-RC5 | 16 |
| NITTC_Scheduler | v1.0.0-RC4 | 1.0.0-RC4 | 15 |
| NITTC_Scheduler | v1.0.0-RC3 | 1.0.0-RC3 | 14 |
| NITTC_Scheduler | v1.0.0-RC2 | 1.0.0-RC2 | 13 |
| NITTC_Scheduler | v1.0.0-RC1 | 1.0.0-RC1 | 12 |
| NITTC_Scheduler | v1.0.0-RC0 | 1.0.0-RC0 | 11 |
| NITTC_Scheduler | v0.9.6-Beta | 0.9.6-Beta | 9 |
| NITTC_Scheduler | v0.9.5-Beta | 0.9.5-Beta | 8 |
| NITTC_Scheduler | v0.9.4-Beta | 0.9.4-Beta | 7 |
| NITTC_Scheduler | v0.9.3-Beta | 0.9.3-Beta | 6 |
| NITTC_Scheduler | v0.9.2-Beta | 0.9.2-Beta | 5 |
| NITTC_Scheduler | v0.9.1-Beta | 0.9.1-Beta | 4 |
| NITTC_Scheduler | v0.9.0-Beta | 0.9.0-Beta | 3 |
| GlyphBarty | v2.1.0-Release | 2.1.0-Release | 15 |
| GlyphBarty | v2.0.1-Release | 2.0.1-Release | 11 |
| GlyphBarty | v2.0.0-Release | 2.0.0-Release | 10 |
| GlyphBarty | v1.3.1-Release | 1.3.1-Release | 6 |
| GlyphBarty | v1.3.0-Release | 1.3.0-Release | 5 |
| GlyphBarty | v1.2.0-Release | 1.2.0-Release | 3 |
| GlyphBarty | v1.1.0-Release | 1.1.0-Release | 2 |
| GlyphBarty | v1.0.0-Release | 1.0.0-Release | 1 |

全体サイズ・SHA-256をGitHub metadataと照合し、apksigner verify --verbose --print-certsを実行した完全取得APKはShutUp全3件、Scheduler最新、Glyph最新の5件です。残る23件は公式APKのmanifestをHTTPS Rangeで直接取り出して解析し、全体digest・全体署名検証までは行っていません。この違いは各fixtureのmethod / wholeApkSignatureVerifiedに明記しています。Range解析用に作ったmanifest-only ZIPは正規APKとして検証・配布していません。

| 命名規則 | 今回確認できたこと / 判定 |
| --- | --- |
| v接頭辞 | タグにv、versionNameには無し。公開28件で確認 |
| Release / Stable | Releaseのみ確認。Stable別名変換は許可しない |
| Beta / RC | SchedulerでBetaとRC0〜6の文字列を維持する対応を確認 |
| IntDev / _rev / ビルド番号 | 公開APKで対応を確認できず、アプリ用bindingではVersionMismatchとして拒否 |
| prefix / compact | APKファイル名にはcompact表記があるが、公開Releaseタグ・manifestは通常表記。ファイル名から版を復元しない |
| 比較上等価な版 | 例としてReleaseとStable、revision違い、2.0と2.0.0を同一APK版として扱わない |

Glyphの現行ソースのIntDev_rev0のような開発用命名が存在することと、公式Releaseへの正当な対応が確認済みであることは区別しています。今後必要になった対応は、配布APKを確認してfixtureを追加してからbindingを拡張します。

fixture: gold/src/test/resources/release-apk-identity.json。詳細な原記録: verification/release-apk-observations.json。

## 転送と安全性

- アプリのcacheDir/goldy-apksを使い、UUIDの.partへ保存します。成功した場合だけ同じディレクトリの.apkへrenameします。失敗・キャンセルでは.partと未引き渡しファイルを清掃します。プロセス終了で残った.partは次回転送時またはcleanPartialFilesで清掃します。
- 同一プロセス全体のMutexで転送は1本に直列化します。待機中もキャンセル可能です。複数プロセス間の調停は初版の対象外です。
- 既定上限256 MiB、既定最大3試行（設定範囲1〜5）、接続15秒・読み取り30秒です。ネットワーク例外、途中EOF、HTTP408/5xxを短い待機後に先頭から再試行します。HTTP429、恒久HTTP失敗、digest・署名・版不一致は自動再試行しません。
- Content-Lengthが無い場合もstreamingします。asset.sizeがあればそれを進捗のtotalに使い、両方無ければnullです。header/asset/実受信数を照合し、読み取り中も上限と宣言値超過を拒否します。Content-Encodingはidentityのみです。
- 初期URLは指定repository、対象タグ、対象asset名に結び付けたHTTPSのGitHub Releases URLのみです。リポジトリ名だけはGitHubの大文字小文字非依存を維持し、タグとファイル名は厳密照合します。
- 自動redirectを無効にし、各hopを検査します。CDNはrelease-assets.githubusercontent.com、objects.githubusercontent.com、github-releases.githubusercontent.comの完全一致のみ。HTTPS、443/既定port、userinfo無し、危険なpath無し、最大5回です。今回の実配信はrelease-assets.githubusercontent.comへの302と署名付きqueryを確認し、queryを維持して取得しました。HTTPや任意hostへのredirectを許可していません。
- SHA-256が提供されたら全体を照合します。提供されたdigestの形式が不明・壊れている場合も拒否します。digestが無くてもAPK解析・package・署名・code・版対応の検証は必須です。[GitHub asset API](https://docs.github.com/en/rest/releases/assets)のsize/digestを使用します。
- AndroidApkInspectorはGET_SIGNING_CERTIFICATES（API26/27はGET_SIGNATURES）を要求するPackageManagerのarchive解析を使います。API28/29はShutUpの互換対策と同様にGET_SIGNATURESも加えます。解析不能、署名情報無し、split情報があるAPKを拒否します。[AOSPのPackageManager](https://android.googlesource.com/platform/prebuilts/fullsdk/sources/android-28/+/refs/heads/androidx-master-release/android/content/pm/PackageManager.java)と[証明書収集経路](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/content/pm/PackageParser.java)を参照しました。
- packageNameは実際のインストール済みアプリと一致必須。署名は現在の署名者のSHA-256集合全体を一致させます。単独署名・複数署名の整合性も検査します。履歴の一部一致や、複数署名の一人だけ一致では許可しません。[SigningInfoの署名者集合の定義](https://developer.android.com/reference/android/content/pm/SigningInfo)に従います。
- versionCodeは実際のインストール済みcode以上を必須にします。等値を許可するShutUpの下限条件を維持し、タグとversionNameの厳密対応は別途必須です。開発用の比較無視設定でこれらの検証を迂回できません。
- 署名ローテーションを自動承認する機能はありません。正当なローテーションでも現在の署名者集合が異なればSignerMismatchとして拒否します。

## 検証結果

最終数値はverification/phase-2-2-results.jsonを参照してください。

- gold JVM: 90テスト、失敗0。旧49テストを保持し、APK関連41テストを追加。旧11094比較・129チャンネル・140選択シナリオを維持。
- 28件のmanifest fixtureと、未確認命名規則を拒否するテスト。
- 転送異常、未知サイズ、上限、HTTP、CDN、不正URL、切断・途中EOFと再試行、キャンセル時切断・清掃、検証中キャンセル、複数インスタンス直列化、保存失敗、一時ファイル清掃。
- 純粋な検証境界で別package、別署名、不完全署名集合、downgrade、版不一致、解析不能を拒否。
- Release AARとsources JARをビルドし、Goldy内のローカルMaven repositoryだけへ出力。外部publish無し。
- includeBuild/project依存を使わない独立consumer: 公開AARを取得して4テスト、Release consumer AAR、lint、producerとのSHA-256一致、Pyrite/Compose/Markwon不在を確認。
- gold Debug/Release lint: エラー・警告0。SDK番号を引数にする純粋なフラグ選択関数だけは、境界テストを付けてInlinedApiの理由を局所的に注記。
- read-only API28 / API36エミュレーターでPackageManager検証。公式3アプリのAPKはインストールせずデータとして読み取りました。インストールしたのは専用の使い捨てprobeだけです。
- API28: 自己APK・ShutUpの署名付き解析、別package拒否、改ざん・途中切断拒否、同じpackageの別鍵拒否、複数署名解析と一部一致拒否。GlyphはminSdk33未満として安全に拒否。SchedulerのAPI28解析は未実施。
- API36: 公式3アプリのpackage/versionName/versionCode/current signerを解析。自己APK受理、別package・別鍵・改ざん・途中切断・署名の一部一致を拒否。

再検証コマンド（JDK25、Android SDK compileSdk37、Gradle9.6.0、AGP9.4.1）:

```powershell
.\gradlew.bat :gold:testDebugUnitTest :gold:lintDebug :gold:lintRelease :gold:publishReleasePublicationToConsumerRepository --offline --console=plain
.\gradlew.bat -p verification/consumer :consumer:testDebugUnitTest :consumer:lintDebug :consumer:assembleRelease :consumer:verifyCoreDependencies --offline --console=plain
.\gradlew.bat -p verification/android-probe assembleDebug --offline --console=plain
```

## 残る検証範囲・次段階

API26/27、29、30〜35、実機/OEMでの実API経路は未検証です。API26/27のlegacy flagはJVM境界テストとlintで確認しています。署名ローテーションの自動受理、新しい命名規則、新しいCDNが必要になった場合は実物を確認して追加します。未確認hostや版対応は安全に拒否します。

転送の異常系は差し替えたtransportによる決定的なJVMテストです。本番Android HttpURLConnection経由の実インターネット転送試験は未実施で、公式配信の取得・redirect観察は調査ツールで行いました。呼び出し元がキャンセルしても同期PackageManager解析自体は即時中断できないため、解析が戻った時点でキャンセルを再確認してファイルを清掃します。

成功後のVerifiedApk.fileはアプリ専用cacheの通常ファイルです。ホストは書き換えず使用し、使用後に削除してください。長期保存、他プロセス共有、インストーラーへの受け渡しと直前の再検証は次段階で設計・実装します。今回の結果にインストール成功の保証は含めていません。

READMEは変更していません。3アプリのHEAD・作業ツリー・未追跡ファイルは作業前の全ファイルハッシュと照合します。Goldyの既存差分は改名と必要な参照更新以外を保護し、既存メタデータソースと互換テストを維持しました。Pyriteには操作していません。コミット・Push・Releaseは行っていません。
