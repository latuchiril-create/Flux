param(
    [string]$LogPath = "$env:USERPROFILE\Desktop\FugaClient-Bots-Debug.log"
)

if (-not (Test-Path -LiteralPath $LogPath)) {
    Write-Error "FAIL: bot debug log not found: $LogPath"
    exit 2
}

$lines = Get-Content -LiteralPath $LogPath
$lastReconfiguration = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match '\[event=SESSION_RECONFIGURATION\]') {
        $lastReconfiguration = $i
    }
}

if ($lastReconfiguration -lt 0) {
    Write-Output 'PASS: no bot reconfiguration in this log.'
    exit 0
}

$joined = $false
for ($i = $lastReconfiguration + 1; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match '\[event=DEBUG_FLY_GAME_JOIN_V1\]') {
        $joined = $true
        break
    }
}

if (-not $joined) {
    Write-Output 'FAIL: transfer entered reconfiguration but never reached the next GameJoin.'
    Write-Output ($lines[$lastReconfiguration] -replace '(?i)(password|pass|пароль)=[^, ]+', '$1=<REDACTED>')
    exit 1
}

Write-Output 'PASS: the latest reconfiguration reached GameJoin.'
exit 0
