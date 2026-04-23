param(
    [string]$Serial = "",
    [string]$AdbPath = "",
    [string]$GradleTask = ":app:connectedDebugAndroidTest",
    [string]$TestClass = "",
    [string[]]$ExtraGradleArgs = @()
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

$scriptRoot = Split-Path -Parent $PSCommandPath
$androidAppDir = (Resolve-Path -LiteralPath (Join-Path $scriptRoot "..\packages\android-app")).Path
$adb = Resolve-AdbPath -RequestedPath $AdbPath
$serial = Resolve-Serial -Adb $adb -RequestedSerial $Serial

$gradleArgs = @($GradleTask)
if ($TestClass.Trim().Length -gt 0) {
    $gradleArgs += "-Pandroid.testInstrumentationRunnerArguments.class=$TestClass"
}
if ($ExtraGradleArgs.Count -gt 0) {
    $gradleArgs += $ExtraGradleArgs
}

Write-Host "Using adb: $adb"
Write-Host "Using emulator: $serial"
Write-Host "Running in: $androidAppDir"
Write-Host "Gradle: ./gradlew $($gradleArgs -join ' ')"

$env:ANDROID_SERIAL = $serial

Push-Location $androidAppDir
try {
    & ./gradlew @gradleArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle command failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}
