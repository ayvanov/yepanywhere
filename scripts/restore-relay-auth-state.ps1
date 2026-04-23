param(
    [string]$BackupPath = "",
    [string]$Serial = "",
    [string]$Package = "com.yepanywhere.android.debug",
    [string]$AdbPath = "",
    [switch]$SkipLaunch = $false,
    [int]$PostLaunchWaitSeconds = 2
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-AdbPath {
    param([string]$RequestedPath)

    if ($RequestedPath -and (Test-Path -LiteralPath $RequestedPath)) {
        return (Resolve-Path -LiteralPath $RequestedPath).Path
    }

    $candidates = @(
        "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe",
        "$env:ANDROID_HOME\platform-tools\adb.exe",
        "D:\android-sdk\platform-tools\adb.exe",
        "adb"
    ) | Where-Object { $_ -and $_.Trim().Length -gt 0 }

    foreach ($candidate in $candidates) {
        if ($candidate -eq "adb") {
            $cmd = Get-Command adb -ErrorAction SilentlyContinue
            if ($cmd) {
                return $cmd.Source
            }
            continue
        }

        if (Test-Path -LiteralPath $candidate) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    throw "adb not found. Pass -AdbPath explicitly."
}

function Invoke-Adb {
    param(
        [string]$Adb,
        [string[]]$Args
    )

    $output = & $Adb @Args 2>&1
    if ($LASTEXITCODE -ne 0) {
        $joined = $Args -join " "
        $text = ($output | Out-String).Trim()
        throw "adb command failed ($LASTEXITCODE): adb $joined`n$text"
    }
    return ($output | Out-String)
}

function Resolve-Serial {
    param(
        [string]$Adb,
        [string]$RequestedSerial
    )

    if ($RequestedSerial.Trim().Length -gt 0) {
        return $RequestedSerial
    }

    $devicesText = Invoke-Adb -Adb $Adb -Args @("devices")
    $serials = @(
        $devicesText -split "`r?`n" |
            Where-Object { $_ -match "^\S+\s+device$" } |
            ForEach-Object { ($_ -split "\s+")[0] }
    )

    if ($serials.Count -eq 0) {
        throw "No connected adb devices."
    }

    $emulatorSerials = @($serials | Where-Object { $_ -like "emulator-*" })
    if ($emulatorSerials.Count -eq 1) {
        return $emulatorSerials[0]
    }

    if ($emulatorSerials.Count -gt 1) {
        throw "Multiple emulators found: $($emulatorSerials -join ", "). Pass -Serial."
    }

    throw "No emulator found among connected adb devices: $($serials -join ", "). Pass -Serial explicitly."
}

function Resolve-BackupPath {
    param(
        [string]$RequestedPath,
        [string]$ResolvedSerial
    )

    if ($RequestedPath.Trim().Length -gt 0) {
        if (-not (Test-Path -LiteralPath $RequestedPath)) {
            throw "Backup path does not exist: $RequestedPath"
        }
        return (Resolve-Path -LiteralPath $RequestedPath).Path
    }

    $escapedSerial = $ResolvedSerial.Replace(":", "_")
    $serialPattern = "relay_auth_state-$escapedSerial-*.xml"
    $genericPattern = "relay_auth_state-*.xml"

    $serialMatches = Get-ChildItem -Path $env:TEMP -Filter $serialPattern -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTimeUtc -Descending
    if ($serialMatches -and $serialMatches.Count -gt 0) {
        return $serialMatches[0].FullName
    }

    $genericMatches = Get-ChildItem -Path $env:TEMP -Filter $genericPattern -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTimeUtc -Descending
    if ($genericMatches -and $genericMatches.Count -gt 0) {
        return $genericMatches[0].FullName
    }

    throw "No backup file found in $env:TEMP. Pass -BackupPath explicitly."
}

$adb = Resolve-AdbPath -RequestedPath $AdbPath
$serial = Resolve-Serial -Adb $adb -RequestedSerial $Serial
$resolvedBackupPath = Resolve-BackupPath -RequestedPath $BackupPath -ResolvedSerial $serial

Write-Host "Using adb: $adb"
Write-Host "Using device: $serial"
Write-Host "Using package: $Package"
Write-Host "Using backup: $resolvedBackupPath"

$pkgList = Invoke-Adb -Adb $adb -Args @("-s", $serial, "shell", "pm", "list", "packages", $Package)
if ($pkgList -notmatch [regex]::Escape("package:$Package")) {
    throw "Package '$Package' is not installed on device '$serial'."
}

$backupContent = Get-Content -LiteralPath $resolvedBackupPath -Raw
if ($backupContent -notmatch "<map") {
    throw "Backup file does not look like SharedPreferences XML: $resolvedBackupPath"
}

$prefsRelPath = "shared_prefs/relay_auth_state.xml"
$backupContent | & $adb -s $serial shell "run-as $Package sh -c 'cat > $prefsRelPath'"
if ($LASTEXITCODE -ne 0) {
    throw "Failed to restore relay_auth_state.xml from backup."
}

$devicePrefs = Invoke-Adb -Adb $adb -Args @("-s", $serial, "exec-out", "run-as", $Package, "cat", $prefsRelPath)
if ($devicePrefs -notmatch "<map") {
    throw "Post-restore validation failed: app prefs XML missing on device."
}

Write-Host "relay_auth_state.xml restored successfully."

if ($SkipLaunch) {
    Write-Host "SkipLaunch enabled. Launch app manually when needed."
    exit 0
}

Invoke-Adb -Adb $adb -Args @("-s", $serial, "shell", "am", "force-stop", $Package) | Out-Null
Invoke-Adb -Adb $adb -Args @("-s", $serial, "shell", "monkey", "-p", $Package, "-c", "android.intent.category.LAUNCHER", "1") | Out-Null

if ($PostLaunchWaitSeconds -gt 0) {
    Start-Sleep -Seconds $PostLaunchWaitSeconds
}

Write-Host "App relaunched after restore."
