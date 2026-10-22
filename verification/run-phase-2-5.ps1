param(
    [ValidateSet('Local','Publication','CoreConsumer','ComposeConsumer','Probe')][string]$Mode = 'Local',
    [string]$JavaHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
if (-not $JavaHome -or -not (Test-Path (Join-Path $JavaHome 'bin/java.exe'))) { throw 'Pass -JavaHome pointing to JDK 21.' }
$env:JAVA_HOME = $JavaHome
$env:PATH = (Join-Path $JavaHome 'bin') + [IO.Path]::PathSeparator + $env:PATH
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskOutput = Join-Path $taskRoot 'build/phase-2-5'
New-Item -ItemType Directory -Force -Path $taskOutput | Out-Null
$taskArguments = switch ($Mode) {
    'Local' { @(':gold:testDebugUnitTest',':elief:testDebugUnitTest',':app:testDebugUnitTest',
        ':gold:assembleDebug',':gold:assembleRelease',':elief:assembleDebug',':elief:assembleRelease',
        ':app:assembleDebug',':app:assembleRelease',':app:assembleDebugAndroidTest',':gold:assembleDebugAndroidTest',
        ':gold:lintDebug',':gold:lintRelease',':elief:lintDebug',':elief:lintRelease',':app:lintDebug',':app:lintRelease',
        ':gold:publishReleasePublicationToConsumerRepository',':elief:publishReleasePublicationToConsumerRepository') }
    'Publication' { @(':gold:publishReleasePublicationToConsumerRepository',':elief:publishReleasePublicationToConsumerRepository',
        '-PgoldyGroup=com.github.Link2011-Act2.Goldy','-PgoldyVersion=phase-2-5-preflight') }
    'CoreConsumer' { @('-p','verification/consumer',':consumer:testDebugUnitTest',':consumer:lintDebug',':consumer:assembleRelease',':consumer:verifyCoreDependencies') }
    'ComposeConsumer' { @('-p','verification/compose-consumer',':consumer:assembleRelease',':consumer:verifyPublishedUi') }
    'Probe' { @('-p','verification/android-probe','verifyInstallerManifest') }
}
Push-Location $taskRoot
try {
    $taskLog = Join-Path $taskOutput ($Mode.ToLowerInvariant() + '.log')
    & ./gradlew.bat @taskArguments --offline --console=plain *> $taskLog
    $taskExit = $LASTEXITCODE
    Get-Content -LiteralPath $taskLog -Tail 35
    if ($taskExit -ne 0) { throw ('Gradle failed; see build/phase-2-5/' + $Mode.ToLowerInvariant() + '.log') }
} finally { Pop-Location }
