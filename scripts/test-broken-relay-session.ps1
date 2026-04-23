param(
    [string]$Serial = "",
    [string]$Package = "com.yepanywhere.android.debug",
    [string]$AdbPath = "",
    [switch]$SkipLaunch = $false,
    [int]$PostLaunchWaitSeconds = 5
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
        [string[]]$Arguments
    )

    $output = & $Adb @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        $joined = $Arguments -join " "
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

    $devicesText = Invoke-Adb -Adb $Adb -Arguments @("devices")
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

function Replace-Once {
    param(
        [string]$InputText,
        [string]$Pattern,
        [string]$Replacement
    )

    $regex = [regex]$Pattern
    if (-not $regex.IsMatch($InputText)) {
        return [pscustomobject]@{
            Replaced = $false
            Value = $InputText
        }
    }

    return [pscustomobject]@{
        Replaced = $true
        Value = $regex.Replace($InputText, $Replacement, 1)
    }
}

$adb = Resolve-AdbPath -RequestedPath $AdbPath
$serial = Resolve-Serial -Adb $adb -RequestedSerial $Serial

Write-Host "Using adb: $adb"
Write-Host "Using device: $serial"
Write-Host "Using package: $Package"

$pkgList = Invoke-Adb -Adb $adb -Arguments @("-s", $serial, "shell", "pm", "list", "packages", $Package)
if ($pkgList -notmatch [regex]::Escape("package:$Package")) {
    throw "Package '$Package' is not installed on device '$serial'."
}

$prefsRelPath = "shared_prefs/relay_auth_state.xml"
$rawPrefs = Invoke-Adb -Adb $adb -Arguments @("-s", $serial, "exec-out", "run-as", $Package, "cat", $prefsRelPath)

if ($rawPrefs -notmatch "name=`"stored_session`"") {
    throw "No stored_session entry found. First do a successful login/reconnect."
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupPath = Join-Path -Path $env:TEMP -ChildPath "relay_auth_state-$($serial.Replace(':', '_'))-$timestamp.xml"
Set-Content -LiteralPath $backupPath -Value $rawPrefs -Encoding UTF8
Write-Host "Backup saved: $backupPath"

$brokenSessionId = "broken-session-id-$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())"
$brokenSessionKey = "broken-session-key-$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())"

$current = $rawPrefs
$idReplace = Replace-Once -InputText $current -Pattern '(&quot;sessionId&quot;:&quot;)[^&"]+' -Replacement "`$1$brokenSessionId"
$current = $idReplace.Value
if (-not $idReplace.Replaced) {
    $idReplacePlain = Replace-Once -InputText $current -Pattern '("sessionId":")[^"]+' -Replacement "`$1$brokenSessionId"
    $current = $idReplacePlain.Value
    $idReplaced = $idReplacePlain.Replaced
} else {
    $idReplaced = $true
}

$keyReplace = Replace-Once -InputText $current -Pattern '(&quot;sessionKey&quot;:&quot;)[^&"]+' -Replacement "`$1$brokenSessionKey"
$current = $keyReplace.Value
if (-not $keyReplace.Replaced) {
    $keyReplacePlain = Replace-Once -InputText $current -Pattern '("sessionKey":")[^"]+' -Replacement "`$1$brokenSessionKey"
    $current = $keyReplacePlain.Value
    $keyReplaced = $keyReplacePlain.Replaced
} else {
    $keyReplaced = $true
}

if (-not $idReplaced -or -not $keyReplaced) {
    throw "Failed to patch stored_session JSON in relay_auth_state.xml."
}

$current | & $adb -s $serial shell "run-as $Package sh -c 'cat > $prefsRelPath'"
if ($LASTEXITCODE -ne 0) {
    throw "Failed to write modified prefs back to device."
}

$verifyPatched = Invoke-Adb -Adb $adb -Arguments @("-s", $serial, "exec-out", "run-as", $Package, "cat", $prefsRelPath)
if ($verifyPatched -notmatch [regex]::Escape($brokenSessionId)) {
    throw "Patched sessionId was not persisted."
}

Write-Host "Injected broken persisted session:"
Write-Host "  sessionId=$brokenSessionId"
Write-Host "  sessionKey=$brokenSessionKey"

if ($SkipLaunch) {
    Write-Host "SkipLaunch enabled. Launch app manually to test fallback."
    exit 0
}

Invoke-Adb -Adb $adb -Arguments @("-s", $serial, "shell", "am", "force-stop", $Package) | Out-Null
Invoke-Adb -Adb $adb -Arguments @("-s", $serial, "shell", "monkey", "-p", $Package, "-c", "android.intent.category.LAUNCHER", "1") | Out-Null

if ($PostLaunchWaitSeconds -gt 0) {
    Start-Sleep -Seconds $PostLaunchWaitSeconds
}

$postLaunch = Invoke-Adb -Adb $adb -Arguments @("-s", $serial, "exec-out", "run-as", $Package, "cat", $prefsRelPath)
if ($postLaunch -match [regex]::Escape($brokenSessionId)) {
    Write-Warning "Broken stored_session is still present after launch. Check app state/logcat."
    exit 0
}

Write-Host "stored_session rotated after launch. Resume fallback appears to be working."
