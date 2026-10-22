param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [Parameter(Mandatory=$true)][string]$Label,
    [double]$FontScale = 1.0,
    [int]$WidthDp = 320
)
$ErrorActionPreference = 'Stop'
if (-not $Serial.StartsWith('emulator-')) { throw 'Use a dedicated disposable emulator.' }
if ($Label -notmatch '^[a-zA-Z0-9-]+$') { throw 'Label must be a simple filename component.' }
$taskRoot = Split-Path $PSScriptRoot -Parent
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$oldFont = (& $adb -s $Serial shell settings get system font_scale).Trim()
$oldSize = (& $adb -s $Serial shell wm size) -join "`n"
$densityText = (& $adb -s $Serial shell wm density) -join "`n"
$densityMatches = [regex]::Matches($densityText, '(?:Physical|Override) density: (\d+)')
$density = [int]$densityMatches[$densityMatches.Count - 1].Groups[1].Value
$width = [int]($WidthDp * $density / 160)
$height = $width * 2
$destination = Join-Path $taskRoot ('build/phase-2-4/' + $Label)
New-Item -ItemType Directory -Force -Path $destination | Out-Null
try {
    & $adb -s $Serial shell wm size ("{0}x{1}" -f $width,$height) | Out-Null
    & $adb -s $Serial shell settings put system font_scale $FontScale | Out-Null
    & $adb -s $Serial install -r (Join-Path $taskRoot 'app/build/outputs/apk/debug/app-debug.apk')
    if ($LASTEXITCODE -ne 0) { throw 'Sample install failed' }
    & $adb -s $Serial install -r (Join-Path $taskRoot 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
    if ($LASTEXITCODE -ne 0) { throw 'Test install failed' }
    $output = & $adb -s $Serial shell am instrument -w -r -e class jp.linkserver.projectgoldy.UpdateScreenTest jp.linkserver.projectgoldy.test/androidx.test.runner.AndroidJUnitRunner
    $output | Set-Content -Encoding utf8 (Join-Path $PSScriptRoot ('phase-2-4-' + $Label + '.txt'))
    & $adb -s $Serial pull /sdcard/Android/data/jp.linkserver.projectgoldy/files/phase-2-4 $destination
    $text = $output -join "`n"
    if ($text -notmatch 'OK \(\d+ tests\)' -or $text -match 'FAILURES!!!|INSTRUMENTATION_FAILED') {
        $output | Select-String 'stack=|FAILURES|Tests run:|INSTRUMENTATION_RESULT' | Write-Output
        throw 'Compose tests failed; inspect the saved log.'
    }
    Write-Output ($Label + ': ' + ($text | Select-String 'OK \(\d+ tests\)').Matches.Value)
} finally {
    if ($oldFont -eq "null" -or [string]::IsNullOrWhiteSpace($oldFont)) {
        & $adb -s $Serial shell settings delete system font_scale | Out-Null
    } else { & $adb -s $Serial shell settings put system font_scale $oldFont | Out-Null }
    if ($oldSize -match 'Override size: (\d+x\d+)') {
        & $adb -s $Serial shell wm size $Matches[1] | Out-Null
    } else { & $adb -s $Serial shell wm size reset | Out-Null }
}
