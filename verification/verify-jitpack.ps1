param([Parameter(Mandatory=$true)][ValidatePattern('^[0-9a-f]{7,40}$')][string]$Version)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskDestination = Join-Path $taskRoot ('build/phase-2-5/jitpack/' + $Version)
New-Item -ItemType Directory -Force -Path $taskDestination | Out-Null
$taskStatus = Invoke-RestMethod -Uri ("https://jitpack.io/api/builds/com.github.Link2011-Act2/Goldy/$Version") -TimeoutSec 30
if ($taskStatus.status -ne 'ok') { throw ('JitPack is not successful: ' + $taskStatus.status + ' / ' + $taskStatus.message) }
if (-not $taskStatus.commit.StartsWith($Version)) { throw 'JitPack built a different commit.' }
$taskRecords = @()
foreach ($taskModule in @('gold','elief')) {
    foreach ($taskSuffix in @('.aar','.pom','-sources.jar','.module')) {
        $taskName = "$taskModule-$Version$taskSuffix"
        $taskUrl = "https://jitpack.io/com/github/Link2011-Act2/Goldy/$taskModule/$Version/$taskName"
        $taskFile = Join-Path $taskDestination $taskName
        Invoke-WebRequest -Uri $taskUrl -OutFile $taskFile -TimeoutSec 60
        $taskRecords += [pscustomobject]@{module=$taskModule;file=$taskName;url=$taskUrl;bytes=(Get-Item $taskFile).Length;
            sha256=(Get-FileHash $taskFile -Algorithm SHA256).Hash}
    }
    [xml]$taskPom = Get-Content (Join-Path $taskDestination "$taskModule-$Version.pom") -Raw
    if ($taskPom.project.groupId -ne 'com.github.Link2011-Act2.Goldy' -or $taskPom.project.version -ne $Version) { throw 'Wrong POM coordinates.' }
    $taskMetadata = Get-Content (Join-Path $taskDestination "$taskModule-$Version.module") -Raw | ConvertFrom-Json
    if ($taskMetadata.component -and ($taskMetadata.component.group -ne 'com.github.Link2011-Act2.Goldy' -or $taskMetadata.component.module -ne $taskModule -or $taskMetadata.component.version -ne $Version)) { throw 'Wrong Gradle metadata coordinates.' }
    if ($taskModule -eq 'elief') {
        $taskGold = @($taskPom.project.dependencies.dependency | Where-Object artifactId -eq 'gold')
        if ($taskGold.Count -ne 1 -or $taskGold[0].groupId -ne 'com.github.Link2011-Act2.Goldy' -or $taskGold[0].version -ne $Version) { throw 'Elief POM does not depend on matching public Gold.' }
        foreach ($taskDep in ($taskMetadata.variants.dependencies | Where-Object module -eq 'gold')) {
            if ($taskDep.group -ne 'com.github.Link2011-Act2.Goldy' -or $taskDep.version.requires -ne $Version) { throw 'Stale Gold metadata dependency.' }
        }
    }
    foreach ($taskVariant in $taskMetadata.variants) { foreach ($taskArtifact in $taskVariant.files) {
        $taskArtifactPath = Join-Path $taskDestination $taskArtifact.name
        if (-not (Test-Path $taskArtifactPath)) { throw ('Metadata points to an unexpected/missing artifact: ' + $taskArtifact.name) }
        if (Test-Path $taskArtifactPath) {
            if ((Get-Item $taskArtifactPath).Length -ne $taskArtifact.size) { throw 'Metadata artifact size mismatch.' }
            if ($taskArtifact.sha256 -and (Get-FileHash $taskArtifactPath -Algorithm SHA256).Hash -ne $taskArtifact.sha256) { throw 'Metadata artifact checksum mismatch.' }
        }
    } }
    $taskSources = [IO.Compression.ZipFile]::OpenRead((Join-Path $taskDestination "$taskModule-$Version-sources.jar"))
    try {
        $taskExpected = if ($taskModule -eq 'gold') { 'ApkInstaller.kt' } else { 'UpdateOverviewScreen.kt' }
        if (-not ($taskSources.Entries | Where-Object { $_.FullName.EndsWith($taskExpected) })) { throw 'Missing current source entry.' }
    } finally { $taskSources.Dispose() }
}
Invoke-WebRequest -Uri "https://jitpack.io/com/github/Link2011-Act2/Goldy/$Version/build.log" -OutFile (Join-Path $taskDestination 'build.log') -TimeoutSec 30
[pscustomobject]@{version=$Version;commit=$taskStatus.commit;status=$taskStatus.status;artifacts=$taskRecords} |
    ConvertTo-Json -Depth 6 | Set-Content -Encoding utf8 (Join-Path $taskDestination 'verified.json')
$taskRecords | Format-Table module,file,bytes
