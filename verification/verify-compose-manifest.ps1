$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$records = @()
foreach ($variant in @('debug','release')) {
    $relative = 'app/build/intermediates/merged_manifests/' + $variant + '/process' + (Get-Culture).TextInfo.ToTitleCase($variant) + 'Manifest/AndroidManifest.xml'
    [xml]$document = Get-Content -Raw (Join-Path $taskRoot $relative)
    $ns = New-Object System.Xml.XmlNamespaceManager($document.NameTable)
    $ns.AddNamespace('android','http://schemas.android.com/apk/res/android')
    if ($document.SelectNodes('//uses-permission[@android:name="android.permission.REQUEST_INSTALL_PACKAGES"]', $ns).Count -ne 0) { throw 'Unexpected install permission' }
    if ($document.SelectNodes('//service', $ns).Count -ne 0) { throw 'Unexpected background service' }
    $provider = $document.SelectNodes('//provider[@android:name="jp.linkserver.goldy.GoldyFileProvider"]', $ns)
    if ($provider.Count -ne 1) { throw 'Gold FileProvider missing or duplicated' }
    $authority = $provider[0].GetAttribute('authorities','http://schemas.android.com/apk/res/android')
    if ($authority -ne 'jp.linkserver.projectgoldy.goldy.fileprovider') { throw 'Unexpected authority' }
    if ($provider[0].GetAttribute('exported','http://schemas.android.com/apk/res/android') -ne 'false') { throw 'Provider exported' }
    $records += [pscustomobject]@{variant=$variant; authority=$authority; installPermission=$false; services=0}
}
$records | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $PSScriptRoot 'phase-2-4-manifest.json')
$records | Format-Table | Out-String | Write-Output
