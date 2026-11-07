param([Parameter(Mandatory=$true)][ValidatePattern('^emulator-[0-9]+$')][string]$Serial)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
$taskOut=Join-Path $taskRoot "build/rotation-20261107/$Serial"
New-Item -ItemType Directory -Force -Path $taskOut | Out-Null
function Invoke-Adb([string[]]$Arguments) {
    $taskText=(& adb -s $Serial @Arguments 2>&1 | Out-String)
    if($LASTEXITCODE -ne 0){throw $taskText}
    return $taskText
}
$taskSdk=(Invoke-Adb @('shell','getprop','ro.build.version.sdk')).Trim()
if((Invoke-Adb @('shell','getprop','sys.boot_completed')).Trim() -ne '1'){throw 'Emulator not booted'}
Invoke-Adb @('install','-r','-t',(Join-Path $taskRoot 'gold/build/outputs/apk/androidTest/debug/gold-debug-androidTest.apk')) | Out-Null
Invoke-Adb @('push',(Join-Path $taskRoot 'gold/src/test/resources/signatures'),'/data/local/tmp/goldy-rotation-fixtures') | Out-Null
Invoke-Adb @('shell','run-as','jp.linkserver.goldy.test','mkdir','-p','files/rotation-fixtures') | Out-Null
foreach($taskFile in Get-ChildItem (Join-Path $taskRoot 'gold/src/test/resources/signatures') -Filter '*.apk') {
    Invoke-Adb @('shell','run-as','jp.linkserver.goldy.test','cp',"/data/local/tmp/goldy-rotation-fixtures/$($taskFile.Name)","files/rotation-fixtures/$($taskFile.Name)") | Out-Null
}
$taskInstrumentation=Invoke-Adb @('shell','am','instrument','-w','-r','-e','mode','rotation','jp.linkserver.goldy.test/jp.linkserver.goldy.GoldyTestRunner')
$taskInstrumentation | Set-Content -Encoding utf8 (Join-Path $taskOut 'instrumentation.txt')
if($taskInstrumentation -notmatch 'PASS: real APK' -or $taskInstrumentation -match 'failure='){throw $taskInstrumentation}
$taskRecords=@()
function Test-Install([string]$Name,[bool]$Allowed) {
    $taskOutput=(& adb -s $Serial install -r -t (Join-Path $taskRoot "gold/src/test/resources/signatures/$Name.apk") 2>&1 | Out-String)
    $taskSuccess=$LASTEXITCODE -eq 0 -and $taskOutput -match 'Success'
    $script:taskRecords += @{file=$Name;expected=$Allowed;accepted=$taskSuccess;output=$taskOutput.Trim()}
    if($taskSuccess -ne $Allowed -or (-not $Allowed -and $taskOutput -notmatch 'INSTALL_(FAILED|PARSE_FAILED)')){throw "Unexpected OS result $Name : $taskOutput"}
}
try {
    Test-Install 'a' $true
    Test-Install 'ab-denied' $false
    Test-Install 'b-unrelated' $false
    Test-Install 'ab-v3' $true
    Test-Install 'a' $false
    Test-Install 'abc-v3' $true
    Test-Install 'ab-v3' $false
    Test-Install 'tampered-lineage' $false
    Invoke-Adb @('uninstall','jp.linkserver.goldy.rotationfixture') | Out-Null
    Test-Install 'multi-ab' $true
    Test-Install 'multi-ab' $true
    Test-Install 'multi-ac' $false
    Test-Install 'ab-v3' $false
    Invoke-Adb @('uninstall','jp.linkserver.goldy.rotationfixture') | Out-Null
    Test-Install 'a' $true
    Test-Install 'ab-v31' $true
} finally {
    & adb -s $Serial uninstall jp.linkserver.goldy.rotationfixture 2>&1 | Out-Null
    @{sdk=$taskSdk;serial=$Serial;instrumentation=$taskInstrumentation;osInstalls=$taskRecords} |
        ConvertTo-Json -Depth 5 | Set-Content -Encoding utf8 (Join-Path $taskOut 'result.json')
}
"API $taskSdk : authenticated fixture inspection and $($taskRecords.Count) OS install cases passed"
