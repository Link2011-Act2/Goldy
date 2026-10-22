# Goldy: APKのインストーラー引き渡し

Goldyは更新を調べ、APKを検証し、Androidのシステムインストーラーへ安全に引き渡す要求を準備します。画面遷移、ユーザー同意、Activityの起動と結果表示はホストアプリが所有します。Compose/Pyriteは必要ありません。

## ホストのManifest

インストール機能を使うホストだけが次を宣言してください。GoldyのAARはこの権限を自動で追加しません。

```xml
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

Goldyは `jp.linkserver.goldy.GoldyFileProvider` をマージします。authorityは `${applicationId}.goldy.fileprovider` です。ホストの既存 `androidx.core.content.FileProvider` とは別クラス・別authorityなので共存できます。Goldy Providerのexported=false、grantUriPermissions=true、専用pathsは維持してください。削除・置換した場合は準備時に安全に拒否される場合があります。

共有範囲はfilesDir/goldy-apks/installだけです。ダウンロード用cacheDir/goldy-apks、ホストのその他のファイル、内部の記録ディレクトリは共有しません。URIへの一時読み取りだけを付け、書き込み・永続・prefix grantは付けません。

## 発見から引き渡しまで

```kotlin
// Activity/Activity Result launcherを所有するホストのMainスコープで実行します。
val repository = GitHubRepository("Link2011-Act2", "ShutUp")
val goldy = Goldy.create(context, GoldyConfig(repository, UpdatePolicies.shutUp()))
val downloader = ApkDownloader.create(context, ApkDownloadConfig(
    repository,
    selection = ApkSelectionPolicies.ShutUp,
    versionBinding = ApkVersionBindings.ShutUp,
))
val installer = ApkInstaller.create(context)

when (val update = goldy.check(CheckTrigger.Manual)) {
    is CheckOutcome.Available -> {
        val apk = downloader.download(update.update.release) { progress ->
            // このコールバックはIOスレッド。StateFlow等へ保存します。
            downloadProgress.value = progress
        }
        pendingDownload.value = apk // ホストが許可画面から戻るまで保持
        when (val prepared = installer.prepare(apk)) {
            is InstallPreparation.Ready -> {
                // 共有用コピーは非キャッシュ領域に確保済みです。
                apk.file.delete()
                pendingDownload.value = null
                pendingInstall.value = prepared.request
                when (val launched = installer.launch(prepared.request) { intent ->
                    installActivityLauncher.launch(intent)
                }) {
                    InstallLaunchResult.HandedOff -> showInstallerOpened()
                    is InstallLaunchResult.PermissionRequired -> {
                        // 許可が準備後に取り消された場合。設定画面から戻って再度launchします。
                        settingsActivityLauncher.launch(launched.settingsIntent)
                    }
                    is InstallLaunchResult.Failed -> showInstallError(launched.error)
                }
                // prepared.requestを保持します。ここでreleaseしません。
            }
            is InstallPreparation.PermissionRequired -> {
                settingsActivityLauncher.launch(prepared.settingsIntent)
                // 設定から戻ってpermissionStatus()を確認し、同じapkでprepareし直します。
                // 許可が拒否されたら案内を表示します。自動で設定を変更しません。
            }
            is InstallPreparation.Failed -> showInstallError(prepared.error)
        }
    }
    else -> showCheckOutcome(update)
}
```

上記の表示関数・StateFlow・Activity Result launcherと、ダウンロード済みAPKの保持はホスト側の例です。downloadが投げるApkExceptionはホストで捕捉してreasonを表示してください。Coroutineキャンセルは通常どおり上位へ伝播させてください。設定画面のlauncherについてもActivityNotFoundException等をホストで処理してください。Goldyは `Context.startActivity` を呼びません。Application Contextから起動する場合に必要なNEW_TASK等もホストが判断します。Goldyが生成するインストールIntent自体のflagはREAD_URI_PERMISSIONだけです。

NITTC Scheduler / Glyph Bartyは既存のrepository、UpdatePoliciesと、対応するApkSelectionPolicies / ApkVersionBindingsに差し替えます。メタデータ・通知無視・版比較の仕組みはPhase 2-2のAPIを維持しています。

## 公開API

| API | 動作 |
| --- | --- |
| ApkInstaller.create(context) | Application Contextを保持するインストール要求マネージャー |
| permissionStatus() | Allowed / UserPermissionRequired / MissingManifestPermission / Unavailable |
| unknownSourcesSettingsIntent() | 自分のpackageだけを対象にする許可設定Intent |
| suspend prepare(VerifiedApk) | 権限・ファイル・現在のインストール済み情報を検査し、共有用コピーを作る |
| InstallPreparation | Ready(request) / PermissionRequired(settingsIntent) / Failed(error) |
| suspend launch(request, startActivity) | 全検査をもう一度実行し、ホストのコールバックへ明示的なシステムActivityを指定したIntentを渡す |
| InstallLaunchResult | HandedOff / PermissionRequired / Failed。Installedという成功状態は提供しない |
| InstallRequest | 永続化された要求のid、content URI、APK metadata。コンストラクターは非公開 |
| suspend pendingRequests() | プロセス再生成後も未解放の要求を復元。復元自体はインストール成功を意味しない |
| suspend release(request) | ホストが使用終了を確認した要求だけ、URI grantを失効して共有用コピーと記録を削除 |
| InstallError | reasonと、APK検証に由来する場合のapkFailure |

主な失敗理由は、権限宣言不足、権限確認不能、Provider設定不備、システムインストーラー不在、ファイル消失・変更、APK検証拒否、保存失敗、起動拒否・起動失敗です。起動後に許可を取り消した場合も再判定します。pendingRequests/releaseのファイルシステム異常は例外として伝播するため、ホストで処理してください。

## 再検証とファイル寿命

VerifiedApkのSHA-256、ファイルサイズ、全APK情報、実際のインストール済みpackageName・現在の署名者集合・versionCodeを再確認します。VerifiedApkに記録したAPK metadata全体とも厳密に照合します。元のReleaseとAPKの版対応は、ダウンロード時に検証した全バイトのハッシュとmetadataをそのまま固定することで維持します。比較関数の0による同一視や、未確認の命名変換は追加していません。

prepareは元ファイルと共有用コピーの両方を検証し、launchは共有用コピーを再検証します。FileProviderも実際のopenFile時にハッシュ・APK・現在のインストール済み情報を検証します。単独署名と複数署名は現在の集合全体を照合し、履歴の一部一致や署名ローテーションの自動承認はありません。

共有用コピーと検証記録はfilesDirの専用領域に置きます。OSのcache清掃や元のVerifiedApk.file削除に依存しません。起動成功・起動例外・ActivityのonResume・Activity Resultの受信では、自動で共有ファイルを消しません。Android/OEMによって結果返却とファイル読み取り完了の関係が異なるためです。

ホストがインストーラーを閉じて使用終了したことを確認した後、またはユーザーが要求を明示的に破棄した後にreleaseを呼んでください。実インストールの成否はPackageManagerの現在の版情報等で別に確認してください。成功表示をHandedOffやActivity Resultだけで決めないでください。

```kotlin
// インストーラーがこのURIをもう使わないことをホストが確認した後だけ実行します。
installer.release(request)
// プロセス再生成後には:
val stillRetained = installer.pendingRequests()
```

prepare前のVerifiedApkはメモリー上の検証記録です。許可設定中にプロセスが終了して記録を失った場合は再ダウンロード・再検証してください。prepare後のInstallRequestはpendingRequestsから復元できます。

未解放の共有用APKに時刻だけで期限を設けません。ホストは使用終了した要求を解放し、不要な元ダウンロードファイルも削除してください。コピー途中の.partは失敗・キャンセルで削除し、プロセス終了で残ったUUID.partは次のprepareで清掃します。同一プロセス内の操作を直列化しており、複数プロセスから同時に更新処理をする構成は初版の対象外です。

filesDirの共有用コピーは通常のアプリデータです。バックアップするホストではgoldy-apksをバックアップ対象から外す方針も検討してください。共有ファイルを同一UIDの他コードで書き換えることは避けてください。root、Shizuku、サイレントインストール、PackageInstaller session、Compose UIは実装していません。

## Androidの参照仕様

[FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider)、[PackageManager.canRequestPackageInstalls](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls())、[REQUEST_INSTALL_PACKAGES](https://developer.android.com/reference/android/Manifest.permission#REQUEST_INSTALL_PACKAGES)の仕様を参照しています。システムAPK用IntentへのqueryをManifestへ限定して追加し、QUERY_ALL_PACKAGESや全handlerへの一括URI grantは使っていません。システムアプリであるインストーラーだけを選び、OEM独自の非システムhandlerは安全側に倒して対象にしません。
