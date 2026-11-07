# Goldy 最新版の公開配布検証 — 2026-11-06

開始 22:08 JST。ローカル・GitHub master ともに `e1d9ecd88e75f28b855ecd568e2e675486d8750e`。
開始時 Goldy は clean。ShutUp は `main` / `1a18497cfbe62eac20ecf3ba8264cb8daf163fac` で、既存 Pyrite 作業を別途保護した。
Windows / PowerShell、Temurin JDK 21.0.7、Gradle 9.6.0、AGP 9.4.1、SDK 37.0、ライブラリ Kotlin API/language 2.2、JVM 11。

## 公開配布

正しい座標は `com.github.Link2011-Act2.Goldy:gold:<SHA>` と `com.github.Link2011-Act2.Goldy:elief:<SHA>`。
[公開ビルドログ](https://jitpack.io/com/github/Link2011-Act2/Goldy/e1d9ecd88e75f28b855ecd568e2e675486d8750e/build.log) は成功。
初回状態 API は `none`、初回モジュール POM 取得は60秒でタイムアウトした。その後のビルド完了・実取得を確認したので、初回タイムアウトを成功扱いしていない。
通常サンドボックス内の GitHub DNS 解決失敗と、ネットワークが利用できる権限での実通信を区別した。

両モジュールの AAR/POM/Gradle Module Metadata/sources 計8ファイルを公開 URL から取得。
Gold AAR 271,165 bytes、Elief AAR 112,004 bytes。POM 座標、同 SHA の Gold 推移依存、metadata のファイル・サイズ・SHA-256、sources の LICENSE/NOTICE を既存 `verification/verify-jitpack.ps1` で検証した。
公開 sources の Gold 18 / Elief 8 Kotlin ファイルは、改行正規化後に HEAD と完全一致。
公開 AAR とローカル最新版 AAR の全クラス公開 JVM シグネチャも一致した。

## 独立 Consumer

既存 `verification/jitpack-consumer` を新しい独立 Gradle ルートへコピー。producer project/includeBuild/mavenLocal/ローカル Maven を使わず、Goldy 座標は JitPack exclusive repository だけから解決した。
全ケース `--refresh-dependencies --info` で実行し、通信・解決ログを保存した。ツールチェーン等の既存キャッシュは使用しているが、旧 Goldy SHA の成功に代替していない。

| ShutUp profile (minSdk28/JVM17) | Debug | Release / R8 | Debug/Release Lint | Manifest |
|---|---|---|---|---|
| Gold 単独 | 成功 | 成功 | エラー0 | 成功 |
| Elief → Gold / stable Material3 | 成功 | 成功 | エラー0 | 成功 |
| Elief / Expressive Material3 | 成功 | 成功 | エラー0 | 成功 |
| Elief / POM-only | 成功 | 成功 | エラー0 | 成功 |

R8 mapping にホスト Activity が存在。Gold 単独では Compose/Markwon/Pyrite なし、全 Elief ケースで Pyrite なし、同 SHA の Gold が推移依存として存在。
ホスト FileProvider と GoldyFileProvider が共存し、固有 authority・非 export・URI grant を確認。ライブラリから不要な install permission/service を追加していない。
AndroidX PrintHelper 由来の D8 locals-table 情報警告は残る。ビルド失敗ではない。

## API / ABI

README の旧公開 SHA `25241039038aa1b6ebf38258b374f662649f7dd1` と比較し、Gold 103型/733 descriptor、Elief 22型/178 descriptor の欠落なし。
旧 AAR に対して Java11 クライアントをコンパイルし、最新版の公開 AAR だけへ差し替えてリンク・実行成功。旧 `VersionPolicies.ShutUp` の比較・channel結果も一致した。
ただし `Standard` / `NittcScheduler` / `GlyphBarty` は最新版で `Common` 規則へ変更されており、比較と channel のサンプル出力44行に差がある。これは API/ABI 欠落とは区別する。ShutUp 導入では `UpdatePolicies.shutUp()` と旧アプリの候補・履歴の対照試験を別途行う。

Gold/Elief の既存 Unit Test は `--rerun` で実行、両 Debug/Release Lint は成功。件数等の集計は `verification/publication-2026-11-06.json` を参照。
生ログ・公開取得物・Consumer APK/mapping・互換性クライアントは ignored `build/latest-publication-20261106/` と `build/phase-2-5/jitpack/e1d9ecd88e75f28b855ecd568e2e675486d8750e/` に保存。

README の検証済み SHA は上記成功後の22:30 JSTごろに更新した。Commit/Push/Release/Tag は作成していない。
Consumer のビルド成功は実際のアプリ更新・署名 rotation・インストール完了を証明しない。
