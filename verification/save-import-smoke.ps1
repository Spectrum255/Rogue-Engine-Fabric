param(
    [string]$JavaHome = $env:JAVA_HOME,
    [int]$RconPort = 25577,
    [int]$ServerPort = 25576
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$runDir = Join-Path $repo 'build/save-import-smoke'
$levelName = 'import-' + [Guid]::NewGuid().ToString('N')
$rconPassword = [Guid]::NewGuid().ToString('N')
$worldDir = Join-Path $runDir $levelName
$packRoot = Join-Path $worldDir 'datapacks/rogue-import/data/rogue-engine-fabric/rogue-engine-fabric'
$packProfile = Join-Path $packRoot 'profile'
$packTemplate = Join-Path $packRoot 'worldgen_template'
$packPool = Join-Path $packRoot 'map_pool'
$packScene = Join-Path $packRoot 'map_scene'
New-Item -ItemType Directory -Path $packProfile,$packTemplate,$packPool,$packScene -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $runDir 'mods') -Force | Out-Null

if (-not $JavaHome) {
    throw 'JAVA_HOME must point to JDK 25.'
}
$env:JAVA_HOME = $JavaHome
$generator = Join-Path $PSScriptRoot 'generate-save-import-fixture.py'
$fixture = Join-Path $runDir 'import-source'
& python $generator $fixture
if ($LASTEXITCODE -ne 0) {
    throw 'Fixture generation failed.'
}

@'
{
  "pack": {
    "pack_format": 121,
    "min_format": 121,
    "max_format": 121,
    "description": "Rogue Engine save import smoke data pack"
  }
}
'@ | Set-Content (Join-Path $worldDir 'datapacks/rogue-import/pack.mcmeta') -Encoding utf8

@'
{
  "schema_version": 1,
  "entries": [
    {"scene": "rogue-engine-fabric:imported_scene", "weight": 1}
  ],
  "default_range": {"scenes": [], "tags": []},
  "repeat_policy": "allow",
  "refresh": {
    "triggers": ["manual", "on_empty"],
    "player_reset": "none"
  },
  "budget": {
    "max_chunks_per_tick": 4,
    "max_tick_ms": 5,
    "max_entities": 128,
    "max_block_entities": 256
  }
}
'@ | Set-Content (Join-Path $packPool 'imported_pool.json') -Encoding utf8

@'
{
  "schema_version": 1,
  "generator_kind": "map_pool",
  "base_stem": "minecraft:overworld",
  "map_pool": "rogue-engine-fabric:imported_pool",
  "default_range": {"scenes": [], "tags": []}
}
'@ | Set-Content (Join-Path $packTemplate 'imported_template.json') -Encoding utf8

@'
{
  "schema_version": 2,
  "source": {"kind": "snapshot", "id": "example:imported"},
  "bounds": {"min": [0, 128, 0], "max": [4, 129, 4]},
  "anchor": [0, 128, 0],
  "spawn": [2, 129, 2],
  "rotation": "none",
  "mirror": "none",
  "processors": [],
  "refresh": {
    "triggers": ["manual", "on_empty"],
    "player_reset": "none"
  }
}
'@ | Set-Content (Join-Path $packScene 'imported_scene.json') -Encoding utf8

@'
{
  "schema_version": 2,
  "dimension_type": "minecraft:overworld",
  "generator": {
    "kind": "template",
    "id": "rogue-engine-fabric:imported_template"
  },
  "seed": {"mode": "random"},
  "spawn": {"mode": "fixed", "x": 2.0, "y": 129.0, "z": 2.0, "yaw": 0.0},
  "border": {
    "center_x": 0.0,
    "center_z": 0.0,
    "initial_size": 64.0,
    "warning_blocks": 8.0
  }
}
'@ | Set-Content (Join-Path $packProfile 'imported.json') -Encoding utf8

@"
eula=true
"@ | Set-Content (Join-Path $runDir 'eula.txt') -Encoding utf8

@"
server-port=$ServerPort
enable-rcon=true
rcon.port=$RconPort
rcon.password=$rconPassword
online-mode=false
white-list=false
enforce-secure-profile=false
level-name=$levelName
level-seed=123456789
motd=Rogue Engine Save Import Smoke
"@ | Set-Content (Join-Path $runDir 'server.properties') -Encoding utf8

$rcon = Join-Path $PSScriptRoot 'dimension-probe/rcon.py'
$logDir = Join-Path $env:TEMP ('rogue-import-smoke-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $logDir -Force | Out-Null
$stdout = Join-Path $logDir 'stdout.log'
$stderr = Join-Path $logDir 'stderr.log'
$startInfo = [System.Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = Join-Path $JavaHome 'bin/java.exe'
$startInfo.WorkingDirectory = $repo
$startInfo.UseShellExecute = $false
$startInfo.ArgumentList.Add('-cp')
$startInfo.ArgumentList.Add((Join-Path $repo 'gradle/wrapper/gradle-wrapper.jar'))
$startInfo.ArgumentList.Add('org.gradle.wrapper.GradleWrapperMain')
$startInfo.ArgumentList.Add('-p')
$startInfo.ArgumentList.Add($repo)
$startInfo.ArgumentList.Add('-I')
$startInfo.ArgumentList.Add((Join-Path $PSScriptRoot 'server-runtime-classpath.gradle'))
$startInfo.ArgumentList.Add("-ProgueRunDir=$runDir")
$startInfo.ArgumentList.Add('runServer')
$startInfo.ArgumentList.Add('--no-configuration-cache')
$startInfo.ArgumentList.Add("--args=--nogui --port $ServerPort")
$startInfo.RedirectStandardOutput = $true
$startInfo.RedirectStandardError = $true
$stdoutStream = [System.IO.File]::Create($stdout)
$stderrStream = [System.IO.File]::Create($stderr)
$process = [System.Diagnostics.Process]::Start($startInfo)
$stdoutTask = $process.StandardOutput.BaseStream.CopyToAsync($stdoutStream)
$stderrTask = $process.StandardError.BaseStream.CopyToAsync($stderrStream)

function Invoke-Rcon([string]$Command) {
    $output = & python $rcon $Command --port $RconPort --password $rconPassword 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "RCON failed: $Command`n$output"
    }
    return ($output | Out-String).Trim()
}

function Wait-ForActive([string]$Id) {
    for ($i = 0; $i -lt 60; $i++) {
        $info = Invoke-Rcon "rogue-engine instance info $Id"
        if ($info -like '*state=ACTIVE*') {
            return $info
        }
        if ($info -like '*state=RECOVERY_REQUIRED*') {
            throw "Imported box requires recovery: $info"
        }
        Start-Sleep -Seconds 1
    }
    throw "Imported box did not become active: $Id"
}

try {
    $ready = $false
    for ($i = 0; $i -lt 120; $i++) {
        Start-Sleep -Seconds 1
        try {
            $null = Invoke-Rcon 'list'
            $ready = $true
            break
        } catch {
            if ($process.HasExited) {
                throw "Server exited before RCON was ready (exit $($process.ExitCode)).`n$(Get-Content $stdout -Raw -ErrorAction SilentlyContinue)`n$(Get-Content $stderr -Raw -ErrorAction SilentlyContinue)"
            }
        }
    }
    if (-not $ready) {
        throw 'Timed out waiting for server RCON.'
    }

    $folderImport = Invoke-Rcon 'rogue-engine map snapshot import example:imported "import-source" minecraft:overworld 0 128 0 4 128 4 true true'
    if ($folderImport -notmatch 'sha256=([0-9a-f]{64}) blocks=25 block_entities=1 entities=1') {
        throw "Folder import failed: $folderImport"
    }
    $folderHash = $Matches[1]
    $zipImport = Invoke-Rcon 'rogue-engine map snapshot import example:imported_zip "save-import.zip" minecraft:overworld 0 128 0 4 128 4 true true'
    if ($zipImport -notmatch 'sha256=([0-9a-f]{64}) blocks=25 block_entities=1 entities=1') {
        throw "ZIP import failed: $zipImport"
    }
    $zipHash = $Matches[1]
    if ($folderHash -ne $zipHash) {
        throw "Folder and ZIP import hashes differ: $folderHash != $zipHash"
    }

    $snapshotDir = Join-Path $worldDir 'data/rogue-engine-fabric/map-snapshots/example'
    $folderSnapshot = Join-Path $snapshotDir 'imported.nbt'
    $zipSnapshot = Join-Path $snapshotDir 'imported_zip.nbt'
    if (-not (Test-Path $folderSnapshot) -or -not (Test-Path $zipSnapshot)) {
        throw 'Imported snapshot files are missing.'
    }
    if ((Get-FileHash $folderSnapshot -Algorithm SHA256).Hash.ToLowerInvariant() -ne $folderHash -or
        (Get-FileHash $zipSnapshot -Algorithm SHA256).Hash.ToLowerInvariant() -ne $zipHash) {
        throw 'Imported snapshot file hashes do not match command output.'
    }

    $created = Invoke-Rcon 'rogue-engine instance create rogue-engine-fabric:imported Imported Box'
    if ($created -notmatch 'Preparing box ([0-9a-f-]{36})') {
        throw "Imported box creation failed: $created"
    }
    $id = $Matches[1]
    $null = Wait-ForActive $id

    $catalog = Get-Content (Join-Path $worldDir 'data/rogue-engine-fabric/catalog-v1.json') -Raw | ConvertFrom-Json
    $definition = $catalog.instances | Where-Object { $_.uuid -eq $id } | Select-Object -First 1
    if ($null -eq $definition.definition.scene_snapshot -or
        $definition.definition.scene_snapshot.scene_id -ne 'rogue-engine-fabric:imported_scene' -or
        $definition.definition.scene_snapshot.hashes.source_kind -ne 'snapshot' -or
        $definition.definition.scene_snapshot.hashes.source_id -ne 'example:imported') {
        throw 'Imported snapshot source metadata was not persisted.'
    }

    $reset = Invoke-Rcon "rogue-engine instance reset $id"
    if ($reset -notlike "Resetting box $id") {
        throw "Imported box reset failed: $reset"
    }
    $null = Wait-ForActive $id
    $catalog = Get-Content (Join-Path $worldDir 'data/rogue-engine-fabric/catalog-v1.json') -Raw | ConvertFrom-Json
    $definition = $catalog.instances | Where-Object { $_.uuid -eq $id } | Select-Object -First 1
    if ($definition.definition.scene_snapshot.reset_count -ne 1) {
        throw 'Imported box reset count was not persisted.'
    }

    $archived = Invoke-Rcon "rogue-engine instance archive $id"
    if ($archived -notlike "Archived $id") {
        throw "Imported box archive failed: $archived"
    }
    $restored = Invoke-Rcon "rogue-engine instance restore $id"
    if ($restored -notlike "Restored $id") {
        throw "Imported box restore failed: $restored"
    }
    $null = Wait-ForActive $id

    Write-Output "Save import smoke test passed: $id sha256=$folderHash"
    Write-Output "Logs: $logDir"
} finally {
    if (-not $process.HasExited) {
        try { $null = Invoke-Rcon 'stop' } catch { }
        if (-not $process.WaitForExit(30000)) {
            $process.Kill()
        }
    }
    $process.WaitForExit()
    $null = $stdoutTask.GetAwaiter().GetResult()
    $null = $stderrTask.GetAwaiter().GetResult()
    $stdoutStream.Dispose()
    $stderrStream.Dispose()
}
