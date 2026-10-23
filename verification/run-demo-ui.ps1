param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [Parameter(Mandatory=$true)][string]$Label,
    [string]$AndroidSdk = "$env:LOCALAPPDATA/Android/Sdk",
    [double]$FontScale = 1.8,
    [int]$WidthDp = 320,
    [string]$TestClasses = 'jp.linkserver.projectgoldy.UpdateScreenTest,jp.linkserver.projectgoldy.DemoScreenTest',
    [switch]$Live
)
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-[0-9]+$') { throw 'Use a disposable emulator.' }
if ($Label -notmatch '^[a-zA-Z0-9-]+$') { throw 'Label must be a simple filename component.' }
$taskRoot = Split-Path $PSScriptRoot -Parent
$destination = Join-Path $taskRoot "build/phase-2-6/$Label"
New-Item -ItemType Directory -Force -Path $destination | Out-Null
$adb = Join-Path $AndroidSdk 'platform-tools/adb.exe'
function Invoke-Adb([string[]]$Arguments) {
    $result = & $adb -s $Serial @Arguments
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $Arguments" }
    return $result
}
$oldFont = (Invoke-Adb @('shell','settings','get','system','font_scale')).Trim()
$oldSize = (Invoke-Adb @('shell','wm','size')) -join "`n"
$densityText = (Invoke-Adb @('shell','wm','density')) -join "`n"
$densities = [regex]::Matches($densityText, '(?:Physical|Override) density: (\d+)')
$density = [int]$densities[$densities.Count - 1].Groups[1].Value
$width = [int]($WidthDp * $density / 160)
try {
    Invoke-Adb @('shell','wm','size',("{0}x{1}" -f $width,($width * 2))) | Out-Null
    Invoke-Adb @('shell','settings','put','system','font_scale',"$FontScale") | Out-Null
    Invoke-Adb @('install','-r',(Join-Path $taskRoot 'app/build/outputs/apk/debug/app-debug.apk'))
    Invoke-Adb @('install','-r',(Join-Path $taskRoot 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))
    $classes = if ($Live) { 'jp.linkserver.projectgoldy.DemoLiveApiTest' } else { $TestClasses }
    $testArgs = @('shell','am','instrument','-w','-r','-e','class',$classes)
    if ($Live) { $testArgs += @('-e','goldyLive','true') }
    $testArgs += 'jp.linkserver.projectgoldy.test/androidx.test.runner.AndroidJUnitRunner'
    $output = Invoke-Adb $testArgs
    $output | Set-Content -Encoding utf8 (Join-Path $destination 'instrumentation.txt')
    Invoke-Adb @('logcat','-d','-s','GoldyDemoVerification:I','*:S') |
        Set-Content -Encoding utf8 (Join-Path $destination 'live-api.txt')
    $text = $output -join "`n"
    if ($text -notmatch 'OK \(\d+ tests\)' -or $text -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed') {
        $output | Select-String 'stack=|FAILURES|Tests run:|INSTRUMENTATION_RESULT' | Write-Output
        throw 'Demo tests failed; inspect the saved instrumentation log.'
    }
    Write-Output ($Label + ': ' + ($text | Select-String 'OK \(\d+ tests\)').Matches.Value)
    if (-not $Live) {
        if ($classes.Contains('UpdateScreenTest')) { Invoke-Adb @('pull','/sdcard/Android/data/jp.linkserver.projectgoldy/files/phase-2-4',$destination) }
        if ($classes.Contains('DemoScreenTest')) { Invoke-Adb @('pull','/sdcard/Android/data/jp.linkserver.projectgoldy/files/phase-2-6',$destination) }
    }
} finally {
    if ($oldFont -eq 'null' -or [string]::IsNullOrWhiteSpace($oldFont)) {
        Invoke-Adb @('shell','settings','delete','system','font_scale') | Out-Null
    } else { Invoke-Adb @('shell','settings','put','system','font_scale',$oldFont) | Out-Null }
    if ($oldSize -match 'Override size: (\d+x\d+)') {
        Invoke-Adb @('shell','wm','size',$Matches[1]) | Out-Null
    } else { Invoke-Adb @('shell','wm','size','reset') | Out-Null }
}
