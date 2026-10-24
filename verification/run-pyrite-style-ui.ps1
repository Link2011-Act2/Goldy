param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [Parameter(Mandatory=$true)][string]$Label,
    [string]$AndroidSdk = "$env:LOCALAPPDATA/Android/Sdk",
    [double]$FontScale = 1.0,
    [int]$WidthDp = 320,
    [int]$HeightDp = 640,
    [int]$DensityDpi = 0,
    [string]$TestClasses = 'jp.linkserver.projectgoldy.PyriteStyleTest'
)
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-[0-9]+$') { throw 'Use a dedicated disposable emulator.' }
if ($Label -notmatch '^[a-zA-Z0-9-]+$') { throw 'Invalid label.' }
$taskRoot = Split-Path $PSScriptRoot -Parent
$destination = Join-Path $taskRoot "build/pyrite-style-review/$Label"
New-Item -ItemType Directory -Force -Path $destination | Out-Null
$adb = Join-Path $AndroidSdk 'platform-tools/adb.exe'
function Invoke-Adb([string[]]$Arguments) {
    $result = & $adb -s $Serial @Arguments
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $Arguments" }
    return $result
}
$oldFont = (Invoke-Adb @('shell','settings','get','system','font_scale')).Trim()
$oldKeyboard = (Invoke-Adb @('shell','settings','get','secure','show_ime_with_hard_keyboard')).Trim()
$oldSize = (Invoke-Adb @('shell','wm','size')) -join "`n"
$oldDensity = (Invoke-Adb @('shell','wm','density')) -join "`n"
$densityMatches = [regex]::Matches($oldDensity, '(?:Physical|Override) density: (\d+)')
$density = if ($DensityDpi -gt 0) { $DensityDpi } else { [int]$densityMatches[$densityMatches.Count - 1].Groups[1].Value }
try {
    if ($DensityDpi -gt 0) { Invoke-Adb @('shell','wm','density',"$DensityDpi") | Out-Null }
    Invoke-Adb @('shell','wm','size',("{0}x{1}" -f [int]($WidthDp*$density/160),[int]($HeightDp*$density/160))) | Out-Null
    Invoke-Adb @('shell','settings','put','system','font_scale',"$FontScale") | Out-Null
    Invoke-Adb @('shell','settings','put','secure','show_ime_with_hard_keyboard','1') | Out-Null
    Invoke-Adb @('install','--no-incremental','-r',(Join-Path $taskRoot 'app/build/outputs/apk/debug/app-debug.apk'))
    Invoke-Adb @('install','--no-incremental','-r',(Join-Path $taskRoot 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))
    $output = Invoke-Adb @('shell','am','instrument','-w','-r','-e','class',$TestClasses,
        'jp.linkserver.projectgoldy.test/androidx.test.runner.AndroidJUnitRunner')
    $output | Set-Content -Encoding utf8 (Join-Path $destination 'instrumentation.txt')
    $text = $output -join "`n"
    $paths = @{}
    if ($TestClasses.Contains('UpdateScreenTest')) { $paths['phase-2-4'] = $true }
    if ($TestClasses.Contains('DemoScreenTest')) { $paths['phase-2-6'] = $true }
    if ($TestClasses.Contains('EliefLayoutTest')) { $paths['elief-ui-review'] = $true }
    if ($TestClasses.Contains('PyriteStyleTest')) { $paths['pyrite-style-review'] = $true }
    if ($TestClasses.Contains('NotificationScreenTest')) { $paths['unified-updates'] = $true }
    foreach ($path in $paths.Keys) {
        Invoke-Adb @('pull',"/sdcard/Android/data/jp.linkserver.projectgoldy/files/$path",$destination)
    }
    if ($text -notmatch 'OK \(\d+ tests?\)' -or $text -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed') {
        $output | Select-String 'stack=|FAILURES|Tests run:|INSTRUMENTATION_RESULT' | Write-Output
        throw 'Elief tests failed; inspect the saved log.'
    }
    Write-Output ($Label + ': ' + ($text | Select-String 'OK \(\d+ tests?\)').Matches.Value)
} finally {
    foreach ($setting in @(@('system','font_scale',$oldFont),@('secure','show_ime_with_hard_keyboard',$oldKeyboard))) {
        if ($setting[2] -eq 'null' -or [string]::IsNullOrWhiteSpace($setting[2])) {
            Invoke-Adb @('shell','settings','delete',$setting[0],$setting[1]) | Out-Null
        } else { Invoke-Adb @('shell','settings','put',$setting[0],$setting[1],$setting[2]) | Out-Null }
    }
    if ($oldSize -match 'Override size: (\d+x\d+)') { Invoke-Adb @('shell','wm','size',$Matches[1]) | Out-Null }
    else { Invoke-Adb @('shell','wm','size','reset') | Out-Null }
    if ($oldDensity -match 'Override density: (\d+)') { Invoke-Adb @('shell','wm','density',$Matches[1]) | Out-Null }
    else { Invoke-Adb @('shell','wm','density','reset') | Out-Null }
}

