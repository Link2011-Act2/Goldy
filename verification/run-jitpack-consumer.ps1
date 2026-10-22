param(
    [string]$GoldyVersion,
    [ValidateSet('baseline','gold','elief')][string]$Feature = 'elief',
    [ValidateSet('shutup','scheduler','glyph')][string]$HostProfile = 'scheduler',
    [switch]$Expressive,
    [switch]$PomOnly,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$AndroidHome = $env:ANDROID_HOME
)
$ErrorActionPreference = 'Stop'
if ($Feature -ne 'baseline' -and $GoldyVersion -notmatch '^[0-9a-f]{7,40}$') { throw 'Pass the actually pushed commit SHA.' }
if (-not (Test-Path (Join-Path $JavaHome 'bin/java.exe'))) { throw 'JDK 21 is required.' }
if (-not (Test-Path (Join-Path $AndroidHome 'platforms'))) { throw 'Set ANDROID_HOME to the installed Android SDK.' }
$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $AndroidHome
$env:PATH = (Join-Path $JavaHome 'bin') + [IO.Path]::PathSeparator + $env:PATH
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskSource = Join-Path $PSScriptRoot 'jitpack-consumer'
$taskIsolated = Join-Path $taskRoot 'build/phase-2-5/isolated-consumer'
$taskLabel = "$HostProfile-$Feature" + $(if ($Expressive) { '-expressive' } else { '' }) + $(if ($PomOnly) { '-pom' } else { '' })
$taskResults = Join-Path $taskRoot ('build/phase-2-5/consumers/' + $taskLabel)
New-Item -ItemType Directory -Force -Path $taskIsolated,$taskResults | Out-Null
foreach ($taskFile in Get-ChildItem -LiteralPath $taskSource -Recurse -File) {
    $taskRelative = [IO.Path]::GetRelativePath($taskSource,$taskFile.FullName)
    if ($taskRelative -match '(^|[\\/])(build|\.gradle|\.kotlin)([\\/]|$)' -or $taskRelative -eq 'local.properties') { continue }
    $taskTarget = Join-Path $taskIsolated $taskRelative
    New-Item -ItemType Directory -Force -Path (Split-Path $taskTarget -Parent) | Out-Null
    Copy-Item -LiteralPath $taskFile.FullName -Destination $taskTarget
}
New-Item -ItemType Directory -Force -Path (Join-Path $taskIsolated 'gradle/wrapper') | Out-Null
Copy-Item -LiteralPath (Join-Path $taskRoot 'gradlew.bat') -Destination $taskIsolated
Copy-Item -Path (Join-Path $taskRoot 'gradle/wrapper/*') -Destination (Join-Path $taskIsolated 'gradle/wrapper')
$taskArgs = @(':app:assembleRelease',':app:verifyPublishedDependencies',':app:verifyPublishedSources',':app:verifyHostManifest',':app:lintRelease',
    "-Pfeature=$Feature","-PhostProfile=$HostProfile",'--console=plain','--no-configuration-cache')
if ($GoldyVersion) { $taskArgs += "-PgoldyVersion=$GoldyVersion" }
if ($Expressive) { $taskArgs += '-Pexpressive=true' }
if ($PomOnly) { $taskArgs += '-PpomOnly=true' }
Push-Location $taskIsolated
try {
    & ./gradlew.bat @taskArgs *> (Join-Path $taskResults 'build.log')
    $taskExit = $LASTEXITCODE
    Get-Content (Join-Path $taskResults 'build.log') -Tail 24
    if ($taskExit -ne 0) { throw "Independent consumer failed: $taskLabel" }
    $taskApk = Join-Path $taskIsolated 'app/build/outputs/apk/release/app-release-unsigned.apk'
    Copy-Item -LiteralPath $taskApk -Destination (Join-Path $taskResults 'app-release-unsigned.apk')
    $taskMapping = Join-Path $taskIsolated 'app/build/outputs/mapping/release/mapping.txt'
    if (-not (Test-Path $taskMapping)) { throw 'Expected R8 mapping was not generated.' }
    if (-not (Select-String -LiteralPath $taskMapping -Pattern '^example\.jitpackconsumer\.MainActivity ->' -Quiet)) {
        throw 'The host Activity was not compiled into the R8 output.'
    }
    [xml]$taskLint = Get-Content (Join-Path $taskIsolated 'app/build/reports/lint-results-release.xml') -Raw
    $taskLintErrors = @($taskLint.issues.issue | Where-Object severity -in @('Error','Fatal')).Count
    if ($taskLintErrors -ne 0) { throw 'Consumer lint has errors.' }
    [pscustomobject]@{version=$GoldyVersion;feature=$Feature;host=$HostProfile;expressive=[bool]$Expressive;pomOnly=[bool]$PomOnly;
        apkBytes=(Get-Item $taskApk).Length;apkSha256=(Get-FileHash $taskApk -Algorithm SHA256).Hash;r8Mapping=$true;
        hostActivityCompiled=$true;lintErrors=$taskLintErrors;lintWarnings=@($taskLint.issues.issue | Where-Object severity -eq 'Warning').Count;passed=$true} |
        ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $taskResults 'result.json')
} finally { Pop-Location }
