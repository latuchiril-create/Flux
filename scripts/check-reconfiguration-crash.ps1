param(
    [string]$CrashDirectory = 'D:\FugaClient\game\crash-reports'
)

$report = Get-ChildItem -LiteralPath $CrashDirectory -Filter 'crash-*-client.txt' -File |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if ($null -eq $report) {
    Write-Output 'PASS: no client crash report found.'
    exit 0
}

$text = Get-Content -LiteralPath $report.FullName -Raw
if ($text -match 'Cannot invoke .*field_1724.* is null') {
    Write-Output "FAIL: reconfiguration crashed because Minecraft handled input with a null player ($($report.Name))."
    exit 1
}

Write-Output "PASS: latest crash report is not the null-player reconfiguration crash ($($report.Name))."
exit 0
