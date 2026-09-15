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
$candidateJars = @(
    (Join-Path $Root "build\libs\fluxvisuals-licensed-1.0.0.jar"),
    (Join-Path $Root "build\libs\fluxvisuals-free-1.0.0.jar"),
    (Join-Path $Root "mods\fluxvisuals-1.0.0.jar")
) | Where-Object { Test-Path $_ } | Sort-Object { (Get-Item $_).LastWriteTime } -Descending

$builtJar = if ($candidateJars.Count -gt 0) { $candidateJars[0] } else { $null }
if ($builtJar) {
    # Stage the archive outside the live mods directory. A direct Copy-Item
    # can leave a half-written JAR when the launcher is interrupted, which
    # makes Fabric fail later with "invalid LOC header".
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    function Test-JarArchive($path) {
        $archive = $null
        try {
            $archive = [System.IO.Compression.ZipFile]::OpenRead($path)
            $buffer = New-Object byte[] 8192
            foreach ($entry in $archive.Entries) {
                $stream = $entry.Open()
                try {
                    while ($stream.Read($buffer, 0, $buffer.Length) -gt 0) { }
                } finally {
                    $stream.Dispose()
                }
            }
            return $true
        } catch {
            return $false
        } finally {
            if ($archive) { $archive.Dispose() }
        }
    }
    $stagedJar = Join-Path $Root ("build\fluxvisuals-sync-" + [Guid]::NewGuid().ToString("N") + ".jar")
    Copy-Item -LiteralPath $builtJar -Destination $stagedJar -Force
    if ((Get-Item -LiteralPath $stagedJar).Length -ne (Get-Item -LiteralPath $builtJar).Length) {
        Remove-Item -LiteralPath $stagedJar -Force -ErrorAction SilentlyContinue
        throw "Staged mod archive size does not match the build output"
    }
    if (-not (Test-JarArchive $stagedJar)) {
        Remove-Item -LiteralPath $stagedJar -Force -ErrorAction SilentlyContinue
        throw "Staged mod archive failed ZIP validation"
    }
    $liveJar = Join-Path $ModsDir (Split-Path -Leaf $builtJar)
    if (Test-Path -LiteralPath $liveJar) {
        # Do not replace an identical archive. Some launchers and antivirus
        # scanners keep a read handle on the active JAR even after Minecraft
        # exits, and replacing it would make a harmless relaunch fail.
        $liveHash = (Get-FileHash -LiteralPath $liveJar -Algorithm SHA256).Hash
        $builtHash = (Get-FileHash -LiteralPath $builtJar -Algorithm SHA256).Hash
        if ($liveHash -eq $builtHash) {
            Remove-Item -LiteralPath $stagedJar -Force -ErrorAction SilentlyContinue
            $stagedJar = $null
        } else {
            # Remove only after the staged archive has passed validation. The
            # launcher starts the game immediately afterwards, so no game
            # process can observe a partially copied archive.
            try {
                [System.IO.File]::Delete($liveJar)
            } catch {
                throw "Live mod archive is in use. Close Minecraft and run launch.ps1 again."
            }
        }
    }
    if ($stagedJar) {
        Move-Item -LiteralPath $stagedJar -Destination $liveJar -Force
    }
    # Keep exactly one FLUX archive in the live mods directory. Older builds
    # (for example fluxvisuals-free-1.0.0.jar) can otherwise win mod loading
    # or leave the user testing a stale command suggester.
    Get-ChildItem $ModsDir -Filter "fluxvisuals*.jar" -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -ne $liveJar } |
        ForEach-Object {
            try {
                [System.IO.File]::Delete($_.FullName)
            } catch {
                throw "Old FLUX archive is in use: $($_.Name). Close Minecraft and run launch.ps1 again."
            }
        }
    Write-Host "  mod: $(Split-Path -Leaf $builtJar) (validated sync)"
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
