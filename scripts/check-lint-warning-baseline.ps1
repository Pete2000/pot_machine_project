param(
    [int]$MaximumWarnings = 18,
    [string]$ReportPath = "app/build/reports/lint-results-debug.txt"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $ReportPath)) {
    throw "Android Lint report not found: $ReportPath"
}

$report = Get-Content -LiteralPath $ReportPath -Raw -Encoding UTF8
$summary = [regex]::Match($report, '(?m)^(\d+) errors, (\d+) warnings\s*$')
if (-not $summary.Success) {
    throw "Unable to parse Android Lint summary from $ReportPath"
}

$errors = [int]$summary.Groups[1].Value
$warnings = [int]$summary.Groups[2].Value
Write-Host "Android Lint baseline: errors=$errors warnings=$warnings maximumWarnings=$MaximumWarnings"

if ($errors -gt 0) {
    throw "Android Lint reported $errors error(s)."
}
if ($warnings -gt $MaximumWarnings) {
    throw "Android Lint warnings increased from the baseline $MaximumWarnings to $warnings."
}
