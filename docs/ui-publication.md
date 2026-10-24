# Goldy UI最終版 — 公開確認

確認日: 2026-10-24。UI/Demo実装確定コミットは `25241039038aa1b6ebf38258b374f662649f7dd1` です。GitHub `Link2011-Act2/Goldy` の `master` へ通常Pushし、リモートのSHA一致を確認しました。

READMEの導入座標は、この実取得・Consumer検証済み実装コミットを固定しています。READMEと本報告を確定する後続コミットは文書だけで、Gold/Elief/Demoの実装は同一です。最終masterの成果物取得・Consumer結果も終了時に別途確認します。

## 確定した仕様と範囲

- Demoは **Material 3 Expressive（初回既定）／Nothing風／通常Material 3** の排他3択です。Light/Dark、Dynamic Color、フォントとスタイルを保存・復元します。
- Nothing風はPyriteのモノクロ配色を使用し、DynamicをOFF/無効表示にしながら他スタイル用の設定を保持します。Pyriteの公開SHA `508b1cf5131679fee7581acbc7ea55a365b6ff2c` はDemoだけが使用します。
- EliefはホストのMaterialTheme/UpdateCardStyleを継承します。戻る矢印、バージョンと非操作Channel表示、背景付きMarkdown、一括履歴、下部操作と小画面適応を維持しています。
- Eliefの元の公開シグネチャは維持されています。更新処理・Coroutine・ファイル寿命・インストーラー操作の所有者はホストです。Goldの署名・package・versionCode・Release照合、HTTPS/CDN制限、FileProvider、InstallRequestを変更していません。
- JitPackの対象はGold/Eliefの2モジュールだけです。Demo APK、タグ、GitHub Releaseは作成・配布していません。
- 今回は前回の未コミット成果を確定し、README/Compose API/Demo説明の古い記述だけを修正しました。新しい更新機能やライブラリAPIは追加していません。

## 公開成果物

[JitPack build.log](https://jitpack.io/com/github/Link2011-Act2/Goldy/25241039038aa1b6ebf38258b374f662649f7dd1/build.log) は成功・exit code 0、状態APIも同じSHA・public・modules=`gold, elief`を返しました。

| モジュール | AAR | sources JAR |
|---|---:|---:|
| Gold | 280,396 bytes | 32,304 bytes |
| Elief | 85,900 bytes | 10,528 bytes |

両モジュールのAAR/POM/metadata/sourcesの計8ファイルを未認証で実取得しました。metadataの参照ファイル・サイズ・SHA-256とPOM座標を確認し、EliefのPOMとAPI/runtime metadataが同SHAのGoldを参照することを確認しました。ローカルSNAPSHOTやPyriteへの公開依存はありません。

Gold 18件、Elief 7件のKotlinソースは、改行を正規化してリポジトリの実装と一致しました。sources JARとAAR内classes.jarのLICENSE/NOTICEも一致しています。Goldの147 classはローカル検証済みAARとバイト一致しました。Elief全26 classの公開JVMシグネチャも一致し、既存の16型・147 descriptorを保持したAPI検証を引き継げます。

## 検証

| 項目 | 結果 |
|---|---|
| Gold / Elief / Demo JVM | 103 / 12 / 6件成功 |
| Demo Debug / UIテストAPK / Release | 成功。前回UI検証時のDebug/Release APKとSHA-256一致 |
| Demo lint Debug/Release | 各エラー0・既存警告9 |
| ローカル独立Compose Consumer | Release AAR成功。両overload、UpdateCardStyle、ホストVM例をコンパイル。stable M3 1.4.0、Pyriteなし |
| JitPack Gold単独Consumer | Release/R8/lint成功。Compose/Markwon/Pyriteなし |
| JitPack Elief Consumer | Release/R8/lint成功。同SHAのGoldへ推移依存、stable M3 1.4.0、Pyriteなし |
| JitPack Elief POM-only Consumer | metadataを使わない依存解決でもRelease/R8/lint成功 |
| 公開Manifest | ホストFileProviderとGoldの限定providerが共存。非export・URI grant・authorityを確認。不要なインストール権限追加なし |

JitPack Consumerは `verification/jitpack-consumer` を別Gradleルートへコピーし、producer project/includeBuild/mavenLocal/ローカルMavenを参照せず実行しました。Goldy座標はexclusive repository設定でJitPackだけから解決しています。標準Elief/POM-onlyのconsumer APKは各1,056,680 bytes、Gold単独は80,190 bytesでした。consumer lintはエラー0、Eliefは警告1、Goldは警告2です。

前回の実UI検証（通常29件、320dp/倍率1.8の重点10件、最終設定3件、R8起動6テーマ）を再利用しています。M3E/Nothing/通常M3×Light/Dark、Dynamic、フォント、再生成、設定復元、戻る・下部操作・履歴・スクロール・キャンセル・再試行・二重操作・IMEを含みます。同じUIテストを無意味に繰り返していません。Demo APKはDebug 13,427,389 bytes、R8 Release unsigned 1,519,118 bytesです。

今回の構造化記録は [ui-publication-results.json](../verification/ui-publication-results.json)、前回のUI記録は [demo-style-selection.md](demo-style-selection.md) と [pyrite-style-review.md](pyrite-style-review.md) です。ローカルの生ログ・取得物・最終master確認は `build/ui-publication/`、元の成果物取得記録は `build/phase-2-5/jitpack/<SHA>/` に保存しています。

## 制限とPhase 3

今回のUIは専用API36エミュレーターでの既存記録を使用しています。実機、API26〜35での今回UI、TalkBack実操作、全壁紙のDynamic Color、実APK自己更新の完了は未検証です。HandedOffはインストーラーへの引き渡しだけを意味します。独立ConsumerのRelease/R8成功も実際のAPK更新完了の証明ではありません。

Phase 3のShutUp移行に進めます。初回は既存設定・無視バージョン・キャッシュの対応、実署名/versionCode、保留InstallRequestの寿命、ホストのテーマ/ナビゲーションをShutUp内で検証してください。ShutUp、NITTC Scheduler、Glyph Barty、BeastLocator、Pyriteは今回変更していません。
