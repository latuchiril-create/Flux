param(
    [string]$LogPath = "$env:USERPROFILE\Desktop\FugaClient-Bots-Debug.log"
)

$samples = Select-String -LiteralPath $LogPath -Pattern '\[event=FLIGHT_DIAGNOSTIC\].*active=true.*below=\{.*block=block\.minecraft\.air, collision=false\}.*mode=ADVENTURE'

if ($samples) {
    $latest = $samples | Select-Object -Last 1
    Write-Output "FAIL: active bot has no floor collision"
    Write-Output $latest.Line
    exit 1
}

Write-Output "PASS: no active-bot missing-floor sample found"
exit 0
