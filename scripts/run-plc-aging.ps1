[CmdletBinding()]
param(
    [string]$DeviceSerial = "",
    [ValidateRange(1, 1000)]
    [int]$Cycles = 20,
    [ValidateRange(1, 100)]
    [int]$ActionTicks = 10,
    [ValidateRange(1000, 60000)]
    [int]$IntervalMs = 5000,
    [string]$Scenarios = "WaterOutlet1,WaterOutlet2,WaterOutlet3,WaterOutlet4,AllWaterOutlets",
    [string]$OutputDirectory = "artifacts\plc-aging",
    [switch]$SkipBuild,
    [switch]$ConfirmPhysicalTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Read-AgingReport {
    & $script:adb connect $script:DeviceSerial | Out-Null
    $content = & $script:adb -s $script:DeviceSerial shell run-as com.example.plccontroller `
        cat files/plc-aging-report.txt 2>$null
    if ($LASTEXITCODE -ne 0) {
        return $null
    }
    return ($content -join [Environment]::NewLine)
}

function Wait-AgingReport {
    param([int]$TimeoutSeconds)

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $report = Read-AgingReport
        if ($report -match '(?m)^status=(Succeeded|Failed|Stopped)\s*$') {
            return $report
        }
        Start-Sleep -Seconds 5
    } while ((Get-Date) -lt $deadline)
    return $report
}

if (-not $ConfirmPhysicalTest) {
    throw "This script actuates real PLC water valves. Pass -ConfirmPhysicalTest only after the site is safe."
}

$projectRoot = Split-Path -Parent $PSScriptRoot
$adbCommand = Get-Command adb -ErrorAction Stop
$adb = $adbCommand.Source

if ([string]::IsNullOrWhiteSpace($DeviceSerial)) {
    $connectedDevices = @(
        & $adb devices |
            Select-String "^[^\s]+\s+device$" |
            ForEach-Object { ($_ -split "\s+")[0] }
    )
    if ($connectedDevices.Count -ne 1) {
        throw "DeviceSerial was not specified and the available ADB device count is $($connectedDevices.Count)."
    }
    $DeviceSerial = $connectedDevices[0]
}

$resolvedOutputDirectory = Join-Path $projectRoot $OutputDirectory
New-Item -ItemType Directory -Force -Path $resolvedOutputDirectory | Out-Null
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$instrumentationLog = Join-Path $resolvedOutputDirectory "plc-aging-$timestamp-instrumentation.txt"
$logcatFile = Join-Path $resolvedOutputDirectory "plc-aging-$timestamp-logcat.txt"

Push-Location $projectRoot
try {
    if (-not $SkipBuild) {
        & .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle build failed with exit code $LASTEXITCODE."
        }
    }

    $appApk = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"
    $testApk = Join-Path $projectRoot "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
    if (-not (Test-Path $appApk) -or -not (Test-Path $testApk)) {
        throw "Test APKs were not found. Remove -SkipBuild or run assembleDebugAndroidTest first."
    }

    & $adb -s $DeviceSerial install -r $appApk
    if ($LASTEXITCODE -ne 0) {
        throw "App APK installation failed."
    }
    & $adb -s $DeviceSerial install -r $testApk
    if ($LASTEXITCODE -ne 0) {
        throw "Test APK installation failed."
    }

    & $adb -s $DeviceSerial logcat -c
    & $adb -s $DeviceSerial shell run-as com.example.plccontroller rm -f files/plc-aging-report.txt
    Write-Host "Starting real water-valve aging: device=$DeviceSerial, cycles=$Cycles, action=$($ActionTicks * 100)ms, interval=${IntervalMs}ms"
    Write-Host "Required: D210 is 1, 3, or 7; M300=1 means the NC emergency-stop loop is healthy; heaters and other outputs are off."

    $instrumentationArgs = @(
        "-s", $DeviceSerial,
        "shell", "am", "instrument", "-w", "-r",
        "-e", "class", "com.example.plccontroller.runtime.aging.RealPlcAgingInstrumentedTest",
        "-e", "plcAgingEnabled", "true",
        "-e", "cycles", $Cycles.ToString(),
        "-e", "actionTicks", $ActionTicks.ToString(),
        "-e", "intervalMs", $IntervalMs.ToString(),
        "-e", "scenarios", $Scenarios,
        "com.example.plccontroller.test/androidx.test.runner.AndroidJUnitRunner"
    )
    $instrumentationOutput = & $adb @instrumentationArgs 2>&1
    $instrumentationExitCode = $LASTEXITCODE
    $instrumentationOutput | Tee-Object -FilePath $instrumentationLog
    $maximumWaitSeconds = [Math]::Ceiling(
        $Cycles * (($ActionTicks * 100 + $IntervalMs + 5000) / 1000.0) + 60
    )
    $deviceReport = Wait-AgingReport -TimeoutSeconds $maximumWaitSeconds
    $deviceReport | Out-File -FilePath $instrumentationLog -Encoding utf8 -Append
    $deviceSucceeded = $deviceReport -match '(?m)^status=Succeeded\s*$'
    if (-not $deviceSucceeded) {
        throw "Real PLC aging failed. Inspect $instrumentationLog and $logcatFile."
    }
    if ($instrumentationExitCode -ne 0 -or
        ($instrumentationOutput -join [Environment]::NewLine) -match "waiting for device|INSTRUMENTATION_FAILED|Process crashed") {
        Write-Warning "ADB transport was interrupted, but the on-device final report confirms success."
    }
}
finally {
    $logcatArgs = @(
        "-s", $DeviceSerial,
        "logcat", "-d", "-v", "threadtime",
        "PlcAging:D", "SerialModbus:D", "PlcPolling:D",
        "MachineCoordinator:D", "AndroidRuntime:E", "*:S"
    )
    & $adb @logcatArgs | Out-File -FilePath $logcatFile -Encoding utf8
    Pop-Location
}

Write-Host "PLC aging completed."
Write-Host "Instrumentation result: $instrumentationLog"
Write-Host "PLC and serial log: $logcatFile"

