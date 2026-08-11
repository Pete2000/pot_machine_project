[CmdletBinding()]
param(
    [string]$DeviceSerial = "",
    [string]$OutputDirectory = "artifacts\plc-recovery",
    [switch]$SkipBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

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
$instrumentationLog = Join-Path $resolvedOutputDirectory "plc-recovery-$timestamp-instrumentation.txt"
$logcatFile = Join-Path $resolvedOutputDirectory "plc-recovery-$timestamp-logcat.txt"
$reportFile = Join-Path $resolvedOutputDirectory "plc-recovery-$timestamp-report.txt"
$testClass = "com.example.plccontroller.runtime.recovery.RealPlcRecoveryInstrumentedTest"

function Invoke-RecoveryStage {
    param([string]$MethodName)

    $stageOutput = & $script:adb -s $script:DeviceSerial shell am instrument -w -r `
        -e class "$script:testClass#$MethodName" `
        -e plcRecoveryEnabled true `
        com.example.plccontroller.test/androidx.test.runner.AndroidJUnitRunner 2>&1
    $stageExitCode = $LASTEXITCODE
    $stageOutput | Tee-Object -FilePath $script:instrumentationLog -Append
    if ($stageExitCode -ne 0 -or
        ($stageOutput -join [Environment]::NewLine) -match "FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed") {
        throw "Recovery stage $MethodName failed."
    }
}

Push-Location $projectRoot
try {
    if (-not $SkipBuild) {
        & .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle build failed with exit code $LASTEXITCODE."
        }
    }

    $appApk = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"
    $testApk = Join-Path $projectRoot "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
    if (-not (Test-Path $appApk) -or -not (Test-Path $testApk)) {
        throw "Test APKs were not found. Remove -SkipBuild to rebuild them."
    }

    & $adb -s $DeviceSerial install -r $appApk
    if ($LASTEXITCODE -ne 0) { throw "App APK installation failed." }
    & $adb -s $DeviceSerial install -r $testApk
    if ($LASTEXITCODE -ne 0) { throw "Test APK installation failed." }

    & $adb -s $DeviceSerial logcat -c
    & $adb -s $DeviceSerial shell run-as com.example.plccontroller rm -f `
        files/plc-recovery-report.txt files/plc-recovery-checkpoint.txt

    Invoke-RecoveryStage -MethodName "captureBaselineBeforeRestart"
    & $adb -s $DeviceSerial shell am force-stop com.example.plccontroller
    Start-Sleep -Seconds 3
    Invoke-RecoveryStage -MethodName "verifyAfterProcessRestart"

    $deviceReport = & $adb -s $DeviceSerial shell run-as com.example.plccontroller `
        cat files/plc-recovery-report.txt
    $deviceReport | Out-File -FilePath $reportFile -Encoding utf8
    if (($deviceReport -join [Environment]::NewLine) -notmatch '(?m)^status=Succeeded\s*$') {
        throw "On-device recovery report did not confirm success."
    }
}
finally {
    $logcatArgs = @(
        "-s", $DeviceSerial,
        "logcat", "-d", "-v", "threadtime",
        "PlcRecovery:D", "SerialModbus:D", "PlcPolling:D", "AndroidRuntime:E", "*:S"
    )
    & $adb @logcatArgs | Out-File -FilePath $logcatFile -Encoding utf8
    Pop-Location
}

Write-Host "PLC automated recovery test completed."
Write-Host "Report: $reportFile"
Write-Host "Instrumentation: $instrumentationLog"
Write-Host "Logcat: $logcatFile"
