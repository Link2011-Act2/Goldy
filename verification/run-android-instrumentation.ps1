param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$AndroidSdk = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$SkipNetwork
)
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-[0-9]+$') { throw 'Use a disposable emulator; this script installs test APKs and controls test-only app ops.' }
$root = Split-Path -Parent $PSScriptRoot
$work = Join-Path $root 'build/phase-2-3'
New-Item -ItemType Directory -Force -Path $work | Out-Null
$adb = Join-Path $AndroidSdk 'platform-tools/adb.exe'
$java = Join-Path $JavaHome 'bin/java.exe'
$keytool = Join-Path $JavaHome 'bin/keytool.exe'
$signer = Join-Path $AndroidSdk 'build-tools/36.0.0/lib/apksigner.jar'
$apk = Join-Path $root 'gold/build/outputs/apk/androidTest/debug/gold-debug-androidTest.apk'
$receiver = Join-Path $root 'verification/android-probe/build/outputs/apk/debug/GoldyAndroidProbe-debug.apk'
function Invoke-Checked([string]$Executable, [string[]]$Arguments) {
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Failed: $Executable" }
}
function Invoke-Test([string]$Mode) {
    $output = & $adb -s $Serial shell am instrument -w -e mode $Mode jp.linkserver.goldy.test/jp.linkserver.goldy.GoldyTestRunner
    $output | Tee-Object -FilePath (Join-Path $work "$Serial-$Mode.txt")
    if ($LASTEXITCODE -ne 0 -or ($output -join "
") -notmatch 'INSTRUMENTATION_CODE: 0' -or ($output -join "
") -match 'INSTRUMENTATION_RESULT: failure=') { throw "Instrumentation failed: $Mode" }
}
foreach ($file in @($apk, $receiver, $signer, $java, $keytool)) { if (-not (Test-Path -LiteralPath $file)) { throw "Missing $file. Build the gold test APK and independent probe first." } }
if ((& $adb -s $Serial shell getprop sys.boot_completed).Trim() -ne '1') { throw 'Emulator is not ready' }
$key = Join-Path $work 'test-only.jks'
if (-not (Test-Path -LiteralPath $key)) {
    Invoke-Checked $keytool @('-genkeypair','-keystore',$key,'-storepass','android','-keypass','android','-alias','probe','-dname','CN=Goldy disposable test key','-keyalg','RSA','-validity','30','-noprompt')
}
Invoke-Checked $java @('-jar',$signer,'sign','--ks',$key,'--ks-pass','pass:android','--key-pass','pass:android','--ks-key-alias','probe','--v4-signing-enabled','false','--out',"$work/foreign-signer.apk",$apk)
$debugKey = Join-Path $env:USERPROFILE '.android/debug.keystore'
Invoke-Checked $java @('-jar',$signer,'sign','--ks',$debugKey,'--ks-pass','pass:android','--key-pass','pass:android','--ks-key-alias','androiddebugkey','--next-signer','--ks',$key,'--ks-pass','pass:android','--key-pass','pass:android','--ks-key-alias','probe','--v3-signing-enabled','false','--v4-signing-enabled','false','--out',"$work/multi-signers.apk",$apk)
Invoke-Checked 'python' @((Join-Path $PSScriptRoot 'prepare-corrupt-apk.py'),$apk,"$work/corrupt.apk")
Invoke-Checked $adb @('-s',$Serial,'install','-r','-t',$apk)
Invoke-Checked $adb @('-s',$Serial,'install','-r',$receiver)
foreach ($name in @('foreign-signer.apk','multi-signers.apk','corrupt.apk')) {
    Invoke-Checked $adb @('-s',$Serial,'push',"$work/$name","/data/local/tmp/goldy-$name")
    Invoke-Checked $adb @('-s',$Serial,'shell',"cat /data/local/tmp/goldy-$name | run-as jp.linkserver.goldy.test sh -c 'mkdir -p files; cat > files/$name'")
}
Invoke-Checked $adb @('-s',$Serial,'shell','appops','set','jp.linkserver.goldy.test','REQUEST_INSTALL_PACKAGES','deny')
Invoke-Test 'permission'
Invoke-Checked $adb @('-s',$Serial,'shell','input','keyevent','4')
Invoke-Checked $adb @('-s',$Serial,'shell','appops','set','jp.linkserver.goldy.test','REQUEST_INSTALL_PACKAGES','allow')
Invoke-Test 'installer'
# Close the installer before explicitly releasing its URI. Never press Install.
Invoke-Checked $adb @('-s',$Serial,'shell','input','keyevent','4')
Invoke-Test 'cleanup'
Invoke-Test 'provider-disabled'
if (-not $SkipNetwork) { Invoke-Test 'network' }
