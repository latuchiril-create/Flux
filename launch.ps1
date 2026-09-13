param(
    [switch]$DryRun,
    [int]$MemoryMb = 3072,
    [string]$Version = "Fabric 1.21.8"
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$GameDir = Join-Path $Root "game"
$ModsDir = Join-Path $GameDir "mods"
$LegacyBase = Join-Path $env:APPDATA ".tlauncher\legacy\Minecraft"
$LegacyGame = Join-Path $LegacyBase "game"
$LegacyMods = Join-Path $LegacyGame "mods"

function Copy-IfMissing($src, $dst) {
    if ((Test-Path $src) -and -not (Test-Path $dst)) {
        $dstParent = Split-Path -Parent $dst
        if (-not (Test-Path $dstParent)) { New-Item -ItemType Directory -Path $dstParent -Force | Out-Null }
        if ((Get-Item $src) -is [System.IO.DirectoryInfo]) {
            Copy-Item $src $dst -Recurse -Force
        } else {
            Copy-Item $src $dst -Force
        }
        Write-Host "  copied: $(Split-Path -Leaf $src)"
    }
}

# 1. Portable game dir
if (-not (Test-Path $ModsDir)) { New-Item -ItemType Directory -Path $ModsDir -Force | Out-Null }

# 2. Settings transfer from Legacy Launcher (only missing files, never overwrite)
Write-Host "[sync] settings legacy -> .\game"
if (Test-Path $LegacyGame) {
    foreach ($f in @("options.txt", "servers.dat", "servers.dat_old", "servers.dat.bak",
                     "usercache.json", "servers.essential.dat", "command_history.txt")) {
        Copy-IfMissing (Join-Path $LegacyGame $f) (Join-Path $GameDir $f)
    }
    foreach ($d in @("config", "resourcepacks", "shaderpacks")) {
        Copy-IfMissing (Join-Path $LegacyGame $d) (Join-Path $GameDir $d)
    }
} else {
    Write-Host "  WARN: legacy game dir not found: $LegacyGame"
}

# 3. Mods sync: fresh built jar + dependency mods from legacy
Write-Host "[sync] mods"
Get-ChildItem $ModsDir -Filter "fluxvisuals*.jar" -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue
$builtJar = $null
foreach ($c in @("build\libs\fluxvisuals-free-1.0.0.jar",
                 "build\libs\fluxvisuals-licensed-1.0.0.jar",
                 "mods\fluxvisuals-1.0.0.jar")) {
    $p = Join-Path $Root $c
    if (Test-Path $p) { $builtJar = $p; break }
}
if ($builtJar) {
    Copy-Item $builtJar (Join-Path $ModsDir (Split-Path -Leaf $builtJar)) -Force
    Write-Host "  mod: $(Split-Path -Leaf $builtJar)"
} else {
    Write-Host "  WARN: no built mod jar found, run gradlew build first"
}
if (Test-Path $LegacyMods) {
    Get-ChildItem $LegacyMods -Filter "*.jar" | Where-Object { $_.Name -notlike "fluxvisuals*" } | ForEach-Object {
        $dst = Join-Path $ModsDir $_.Name
        if (-not (Test-Path $dst)) {
            Copy-Item $_.FullName $dst -Force
            Write-Host "  dep: $($_.Name)"
        }
    }
}

# 4. Java (Legacy JRE first, then system java)
$javaExe = Join-Path $LegacyBase "jre\x64\bin\javaw.exe"
if (-not (Test-Path $javaExe)) { $javaExe = Join-Path $LegacyBase "jre\x64\bin\java.exe" }
if (-not (Test-Path $javaExe)) {
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCmd) { $javaExe = $javaCmd.Source } else { throw "java not found" }
}
Write-Host "[java] $javaExe"

# 5. Auth: account + uuid from legacy profiles
$playerName = "ghoul_rus99"
$playerUuid = "92b5418ab05934e989513588c113846c"
$tlProfiles = Join-Path $LegacyGame "tlauncher_profiles.json"
if (Test-Path $tlProfiles) {
    try {
        $prof = Get-Content $tlProfiles -Raw -Encoding UTF8 | ConvertFrom-Json
        $sel = $prof.userSet.selected.username
        if ($sel) { $playerName = $sel }
        $entry = $prof.userSet.list | Where-Object { $_.username -eq $playerName } | Select-Object -First 1
        if ($entry -and $entry.uuid) { $playerUuid = $entry.uuid -replace "-", "" }
    } catch { Write-Host "  WARN: profiles parse failed, defaults used" }
}
$uuidDashed = $playerUuid -replace '^(.{8})(.{4})(.{4})(.{4})(.{12})$', '$1-$2-$3-$4-$5'
Write-Host "[auth] $playerName $uuidDashed"

# 6. Version json -> classpath
$versionDir = Join-Path $LegacyGame "versions\$Version"
$versionJson = Join-Path $versionDir "$Version.json"
$versionJar = Join-Path $versionDir "$Version.jar"
if (-not (Test-Path $versionJson)) { throw "version json not found: $versionJson" }
if (-not (Test-Path $versionJar)) { throw "version jar not found: $versionJar" }
$ver = Get-Content $versionJson -Raw -Encoding UTF8 | ConvertFrom-Json
$libsDir = Join-Path $LegacyGame "libraries"
$nativesDir = Join-Path $versionDir "natives"

function Test-RuleApplies($rule) {
    if ($rule.features) {
        if ($rule.features.is_demo_user -eq $true) { return $false }
        if ($rule.features.has_quick_plays_support) { return $false }
        if ($rule.features.is_quick_play_singleplayer) { return $false }
        if ($rule.features.is_quick_play_multiplayer) { return $false }
        if ($rule.features.is_quick_play_realms) { return $false }
        # has_custom_resolution = true (we always pass width/height)
    }
    if ($rule.os) {
        if ($rule.os.name -and $rule.os.name -ne "windows") { return $false }
    }
    return $true
}

function Compare-Versions($a, $b) {
    # Tolerant numeric compare: 9.10.1 > 9.6, 4.1.118.Final == 4.1.118.Final
    $pa = ($a -split "[^0-9]+") | Where-Object { $_ -ne "" } | ForEach-Object { [int]$_ }
    $pb = ($b -split "[^0-9]+") | Where-Object { $_ -ne "" } | ForEach-Object { [int]$_ }
    $n = [Math]::Max($pa.Count, $pb.Count)
    for ($i = 0; $i -lt $n; $i++) {
        $x = if ($i -lt $pa.Count) { $pa[$i] } else { 0 }
        $y = if ($i -lt $pb.Count) { $pb[$i] } else { 0 }
        if ($x -ne $y) { return $x - $y }
    }
    return [string]::Compare($a, $b, $true)
}

$cp = New-Object System.Collections.Generic.List[string]
$skipped = 0
$dedup = @{}  # group:artifact -> @{ver; path}
foreach ($lib in $ver.libraries) {
    $allowed = $true
    if ($lib.rules) {
        $allowed = $false
        foreach ($r in $lib.rules) {
            if (Test-RuleApplies $r) { $allowed = ($r.action -eq "allow") }
        }
    }
    if (-not $allowed) { $skipped++; continue }
    if ($lib.natives -and -not $lib.downloads.artifact) { continue } # natives already extracted
    $path = $null; $key = $null; $libver = ""
    if ($lib.name) {
        $parts = $lib.name -split ":"
        if ($parts.Count -ge 3) { $key = "$($parts[0]):$($parts[1])"; $libver = $parts[2] }
    }
    if ($lib.downloads -and $lib.downloads.artifact -and $lib.downloads.artifact.path) {
        $path = Join-Path $libsDir ($lib.downloads.artifact.path -replace "/", "\")
    } elseif ($key) {
        $g = ($lib.name -split ":")[0] -replace "\.", "\"
        $a = ($lib.name -split ":")[1]
        $path = Join-Path $libsDir "$g\$a\$libver\$a-$libver.jar"
    }
    if ($path -and (Test-Path $path)) {
        if ($key -and $dedup.ContainsKey($key)) {
            # Same artifact twice (e.g. asm 9.6 vanilla vs 9.10.1 fabric):
            # Knot aborts on duplicates, keep highest version.
            if ((Compare-Versions $libver $dedup[$key].ver) -gt 0) {
                Write-Host "  dedup: $key $($dedup[$key].ver) -> $libver"
                $dedup[$key] = @{ ver = $libver; path = $path }
            }
        } elseif ($key) {
            $dedup[$key] = @{ ver = $libver; path = $path }
        } else {
            if (-not $cp.Contains($path)) { $cp.Add($path) | Out-Null }
        }
    } else {
        Write-Host "  WARN: lib missing: $($lib.name)"
    }
}
foreach ($e in $dedup.Values) { $cp.Add($e.path) | Out-Null }
$cp.Add($versionJar) | Out-Null
Write-Host "[cp] libs: $($cp.Count), skipped(os): $skipped"
$classpath = $cp -join ";"

# 7. Window size from tl.properties (minecraft.size=925;530)
$width = "925"; $height = "530"
$tlProps = Join-Path $LegacyBase "tl.properties"
if (Test-Path $tlProps) {
    $m = Select-String -Path $tlProps -Pattern "^minecraft\.size=(.+)" | Select-Object -First 1
    if ($m -and $m.Matches.Groups[1].Value -match "(\d+);(\d+)") {
        $width = $Matches[1]; $height = $Matches[2]
    }
}

$assetIndex = $ver.assetIndex.id
if (-not $assetIndex) { $assetIndex = $ver.assets }
$assetsDir = Join-Path $LegacyGame "assets"
$xms = [int]($MemoryMb / 2)

$jvmArgs = @(
    "-Xms${xms}M", "-Xmx${MemoryMb}M",
    "-Xss1M",
    "-Djava.library.path=$nativesDir",
    "-Djna.tmpdir=$nativesDir",
    "-Dorg.lwjgl.system.SharedLibraryExtractPath=$nativesDir",
    "-Dio.netty.native.workdir=$nativesDir",
    "-Dminecraft.launcher.brand=Legacy",
    "-Dminecraft.launcher.version=1.169.4+legacy",
    "-DFabricMcEmu= net.minecraft.client.main.Main "
)
$versionType = "modified"
if ($ver.type) { $versionType = $ver.type }
$gameArgs = @(
    "--username", $playerName,
    "--version", $Version,
    "--gameDir", $GameDir,
    "--assetsDir", $assetsDir,
    "--assetIndex", "$assetIndex",
    "--uuid", $uuidDashed,
    "--accessToken", "0",
    "--clientId", "0",
    "--xuid", "0",
    "--userType", "legacy",
    "--versionType", $versionType,
    "--width", $width,
    "--height", $height
)

if ($DryRun) {
    Write-Host "--- DRY RUN ---"
    Write-Host "java: $javaExe"
    Write-Host "main: $($ver.mainClass)"
    Write-Host "jvm: $($jvmArgs -join ' ')"
    Write-Host "game: $($gameArgs -join ' ')"
    Write-Host "cp entries: $($cp.Count)"
    Write-Host "gamedir: $GameDir"
    return
}

Write-Host "[launch] $playerName @ $Version, ${MemoryMb}MB, ${width}x${height}"
Write-Host "[launch] console stays open, game logs below. Close game window to exit."
$allArgs = $jvmArgs + @("-cp", $classpath, $ver.mainClass) + $gameArgs
# Foreground launch with console java so logs stream into this window
# and any startup error is visible instead of a silent background fail.
$consoleJava = $javaExe
if ($consoleJava -like "*javaw.exe") {
    $candidate = Join-Path (Split-Path -Parent $consoleJava) "java.exe"
    if (Test-Path $candidate) { $consoleJava = $candidate }
}
Set-Location $Root
& $consoleJava @allArgs
$code = $LASTEXITCODE
Write-Host "Minecraft exited with code $code"
