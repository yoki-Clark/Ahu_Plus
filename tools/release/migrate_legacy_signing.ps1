[CmdletBinding()]
param(
    [string]$SourceKeystore = "$env:USERPROFILE\.android\debug.keystore"
)

$ErrorActionPreference = "Stop"

$expectedFingerprint = "D290B9CF0653C1F80B2DF26EF39B3385C854F9D653793FB34CAE36AC9FB6D463"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$source = (Resolve-Path -LiteralPath $SourceKeystore).Path
$signingRoot = Join-Path $env:USERPROFILE ".ahu-plus\signing"
$destination = Join-Path $signingRoot "ahu-plus-production.p12"
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupOne = Join-Path $signingRoot "backups\ahu-plus-production-$timestamp.p12"
$backupTwo = Join-Path $env:USERPROFILE "AhuPlusSigningBackup\ahu-plus-production-$timestamp.p12"
$localProperties = Join-Path $repoRoot "local.properties"

if ($source.StartsWith($repoRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "The source keystore must be outside the repository."
}
if (Test-Path -LiteralPath $destination) {
    throw "The destination keystore already exists; refusing to overwrite: $destination"
}

$sourceOutput = & keytool -list -v -keystore $source -storepass android -alias androiddebugkey 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "Unable to read the historical debug keystore."
}
$sourceFingerprint = [regex]::Match(
    ($sourceOutput -join "`n"),
    "SHA-?256\)?:\s*((?:[0-9A-Fa-f]{2}:?\s*){32})"
).Groups[1].Value.Replace(":", "").Replace("`r", "").Replace("`n", "").Replace(" ", "").ToUpperInvariant()
if ($sourceFingerprint -ne $expectedFingerprint) {
    throw "The historical keystore fingerprint does not match: $sourceFingerprint"
}

[byte[]]$randomBytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $rng.GetBytes($randomBytes)
} finally {
    $rng.Dispose()
}
$password = [Convert]::ToBase64String($randomBytes)
$env:AHU_MIGRATION_STORE_PASS = $password

try {
    New-Item -ItemType Directory -Force -Path $signingRoot | Out-Null
    & keytool -importkeystore `
        -srckeystore $source `
        -srcstoretype PKCS12 `
        -srcstorepass android `
        -srcalias androiddebugkey `
        -destkeystore $destination `
        -deststoretype PKCS12 `
        -deststorepass:env AHU_MIGRATION_STORE_PASS `
        -destkeypass:env AHU_MIGRATION_STORE_PASS `
        -destalias ahuplus-release `
        -noprompt | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to import the dedicated Release keystore."
    }

    $destinationOutput = & keytool -list -v `
        -keystore $destination `
        -storepass:env AHU_MIGRATION_STORE_PASS `
        -alias ahuplus-release 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to verify the dedicated Release keystore."
    }
    $destinationFingerprint = [regex]::Match(
        ($destinationOutput -join "`n"),
        "SHA-?256\)?:\s*((?:[0-9A-Fa-f]{2}:?\s*){32})"
    ).Groups[1].Value.Replace(":", "").Replace("`r", "").Replace("`n", "").Replace(" ", "").ToUpperInvariant()
    if ($destinationFingerprint -ne $expectedFingerprint) {
        throw "The certificate fingerprint changed during migration."
    }

    New-Item -ItemType Directory -Force -Path (Split-Path $backupOne) | Out-Null
    New-Item -ItemType Directory -Force -Path (Split-Path $backupTwo) | Out-Null
    Copy-Item -LiteralPath $destination -Destination $backupOne
    Copy-Item -LiteralPath $destination -Destination $backupTwo

    $releaseKeys = @(
        "AHU_RELEASE_STORE_FILE",
        "AHU_RELEASE_STORE_PASSWORD",
        "AHU_RELEASE_KEY_ALIAS",
        "AHU_RELEASE_KEY_PASSWORD"
    )
    $existingLines = if (Test-Path -LiteralPath $localProperties) {
        [IO.File]::ReadAllLines($localProperties)
    } else {
        @()
    }
    $preservedLines = $existingLines | Where-Object {
        $line = $_
        -not ($releaseKeys | Where-Object { $line -match "^$([regex]::Escape($_))=" })
    }
    $propertiesPath = $destination.Replace("\", "/")
    $newLines = @($preservedLines) + @(
        "AHU_RELEASE_STORE_FILE=$propertiesPath",
        "AHU_RELEASE_STORE_PASSWORD=$password",
        "AHU_RELEASE_KEY_ALIAS=ahuplus-release",
        "AHU_RELEASE_KEY_PASSWORD=$password"
    )
    [IO.File]::WriteAllLines($localProperties, $newLines, [Text.UTF8Encoding]::new($false))

    Write-Host "Release signing identity migrated: $destinationFingerprint"
    Write-Host "Primary keystore: $destination"
    Write-Host "Backup 1: $backupOne"
    Write-Host "Backup 2: $backupTwo"
    Write-Host "The original debug keystore was retained."
} finally {
    Remove-Item Env:AHU_MIGRATION_STORE_PASS -ErrorAction SilentlyContinue
    if ($password) {
        $password = $null
    }
    [Array]::Clear($randomBytes, 0, $randomBytes.Length)
}
