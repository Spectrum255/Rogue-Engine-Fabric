param(
    [string]$JavaHome = $env:JAVA_HOME,
    [int]$RconPort = 25567,
    [int]$ServerPort = 25566
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$runDir = Join-Path $repo 'build/server-smoke'
$levelName = 'smoke-' + [Guid]::NewGuid().ToString('N')
$rconPassword = [Guid]::NewGuid().ToString('N')
$worldDir = Join-Path $runDir $levelName
$packDir = Join-Path $worldDir 'datapacks/rogue-smoke/data/rogueengine/rogueengine/profile'
New-Item -ItemType Directory -Path $packDir -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $runDir 'mods') -Force | Out-Null
@'
{
  "pack": {
    "pack_format": 121,
    "min_format": 121,
    "max_format": 121,
    "description": "Rogue Engine server smoke data pack"
  }
}
'@ | Set-Content (Join-Path $worldDir 'datapacks/rogue-smoke/pack.mcmeta') -Encoding utf8

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
motd=Rogue Engine Smoke
"@ | Set-Content (Join-Path $runDir 'server.properties') -Encoding utf8

@"
{
  "schema_version": 1,
  "dimension_type": "minecraft:overworld",
  "generator": {"kind": "stem", "id": "minecraft:overworld"},
  "seed": {"mode": "fixed", "value": 12345},
  "spawn": {"mode": "search", "radius": 16},
  "border": {"center_x": 0.0, "center_z": 0.0, "initial_size": 256.0, "warning_blocks": 16.0}
}
"@ | Set-Content (Join-Path $packDir 'smoke.json') -Encoding utf8

@"
{
  "schema_version": 1,
  "dimension_type": "minecraft:overworld",
  "generator": {"kind": "stem", "id": "minecraft:overworld"},
  "seed": {"mode": "fixed", "value": 54321},
  "spawn": {"mode": "search", "radius": 8},
  "border": {"center_x": 0.0, "center_z": 0.0, "initial_size": 128.0, "warning_blocks": 8.0},
  "inventory": {"bring_on_enter": false, "take_on_exit": false}
}
"@ | Set-Content (Join-Path $packDir 'isolated.json') -Encoding utf8

$localPackRoot = Join-Path $runDir 'local-pack-source'
$localTemplateDir = Join-Path $localPackRoot 'data/example/rogueengine/worldgen_template'
$localProfileDir = Join-Path $localPackRoot 'data/example/rogueengine/profile'
New-Item -ItemType Directory -Path $localTemplateDir,$localProfileDir -Force | Out-Null
@'
{
  "pack": {
    "pack_format": 121,
    "min_format": 121,
    "max_format": 121,
    "description": "Local import smoke pack"
  }
}
'@ | Set-Content (Join-Path $localPackRoot 'pack.mcmeta') -Encoding utf8
@'
{
  "schema_version": 1,
  "generator_kind": "noise_chaos",
  "base_stem": "minecraft:overworld"
}
'@ | Set-Content (Join-Path $localTemplateDir 'local_smoke.json') -Encoding utf8
@'
{
  "schema_version": 2,
  "dimension_type": "minecraft:overworld",
  "generator": {"kind": "template", "id": "example:local_smoke"},
  "seed": {"mode": "fixed", "value": 123},
  "spawn": {"mode": "search", "radius": 16},
  "border": {"center_x": 0.0, "center_z": 0.0, "initial_size": 128.0, "warning_blocks": 8.0}
}
'@ | Set-Content (Join-Path $localProfileDir 'local_smoke.json') -Encoding utf8
Compress-Archive -Path (Join-Path $localPackRoot '*') -DestinationPath (Join-Path $runDir 'local-pack.zip') -Force

if (-not $JavaHome) {
    throw 'JAVA_HOME must point to JDK 25.'
}
$env:JAVA_HOME = $JavaHome
$gradlew = Join-Path $repo 'gradlew.bat'
$rcon = Join-Path $PSScriptRoot 'dimension-probe/rcon.py'
$logDir = Join-Path $env:TEMP ('rogue-smoke-' + [Guid]::NewGuid().ToString('N'))
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

    $profiles = Invoke-Rcon 'rogueengine profile list'
    if ($profiles -notlike '*rogueengine:smoke*' -or $profiles -notlike '*rogueengine:isolated*' -or $profiles -notlike '*rogueengine:noise_chaos*' -or $profiles -notlike '*rogueengine:full_chaos*' -or $profiles -notlike '*rogueengine:preset_chaos*' -or $profiles -notlike '*rogueengine:bounded_noise*' -or $profiles -notlike '*rogueengine:central_island*' -or $profiles -notlike '*rogueengine:map_pool*') {
        throw "Profiles were not loaded: $profiles"
    }
    $localZip = (Join-Path $runDir 'local-pack.zip').Replace('\', '/')
    $localImport = Invoke-Rcon "rogueengine admin datapack import local_smoke `"$localZip`""
    if ($localImport -notlike 'Imported local datapack local_smoke') {
        throw "Local datapack import failed: $localImport"
    }
    $localCreated = Invoke-Rcon 'rogueengine instance create example:local_smoke Local Pack Box'
    if ($localCreated -notmatch 'Created box ([0-9a-f-]{36})') {
        throw "Local datapack box creation failed: $localCreated"
    }
    $localId = $Matches[1]
    $localArchived = Invoke-Rcon "rogueengine instance archive $localId"
    if ($localArchived -notlike "Archived $localId") {
        throw "Local datapack box archive failed: $localArchived"
    }
    $created = Invoke-Rcon 'rogueengine instance create rogueengine:smoke Smoke Box'
    if ($created -notmatch 'Created box ([0-9a-f-]{36})') {
        throw "Box creation failed: $created"
    }
    $id = $Matches[1]
    $info = Invoke-Rcon "rogueengine instance info $id"
    if ($info -notlike '*state=ACTIVE*') {
        throw "Created box is not active: $info"
    }
    $archived = Invoke-Rcon "rogueengine instance archive $id"
    if ($archived -notlike "Archived $id") {
        throw "Box archive failed: $archived"
    }
    $restored = Invoke-Rcon "rogueengine instance restore $id"
    if ($restored -notlike "Restored $id") {
        throw "Box restore failed: $restored"
    }
    $isolated = Invoke-Rcon 'rogueengine instance create rogueengine:isolated Isolated Box'
    if ($isolated -notmatch 'Created box ([0-9a-f-]{36})') {
        throw "Isolated box creation failed: $isolated"
    }
    $isolatedId = $Matches[1]
    $catalog = Get-Content (Join-Path $worldDir 'data/rogueengine/catalog-v1.json') -Raw | ConvertFrom-Json
    $definition = $catalog.instances | Where-Object { $_.uuid -eq $isolatedId } | Select-Object -First 1
    $snapshot = $definition.definition.profile_json | ConvertFrom-Json
    if ($snapshot.inventory.bring_on_enter -ne $false -or $snapshot.inventory.take_on_exit -ne $false) {
        throw 'Isolated inventory policy was not captured in the box definition snapshot.'
    }
    $isolatedArchived = Invoke-Rcon "rogueengine instance archive $isolatedId"
    if ($isolatedArchived -notlike "Archived $isolatedId") {
        throw "Isolated box archive failed: $isolatedArchived"
    }
    $templateIds = @()
    foreach ($templateName in @('noise_chaos', 'full_chaos', 'preset_chaos', 'bounded_noise', 'central_island', 'central_island_layers')) {
        $templateCreated = Invoke-Rcon "rogueengine instance create rogueengine:$templateName $templateName Box"
        if ($templateCreated -notmatch 'Created box ([0-9a-f-]{36})') {
            throw "$templateName box creation failed: $templateCreated"
        }
        $templateId = $Matches[1]
        $templateIds += $templateId
        $catalog = Get-Content (Join-Path $worldDir 'data/rogueengine/catalog-v1.json') -Raw | ConvertFrom-Json
        $templateDefinition = $catalog.instances | Where-Object { $_.uuid -eq $templateId } | Select-Object -First 1
        $templateSnapshot = $templateDefinition.definition.profile_json | ConvertFrom-Json
        if ($templateSnapshot.generator.kind -ne 'inline') {
            throw "$templateName generator was not materialized into the box snapshot."
        }
        if ($templateName -eq 'bounded_noise') {
            if (-not $templateDefinition.definition.level_stem_json.Contains('rogueengine:bounded_noise')) {
                throw 'Bounded noise generator was not persisted in the level stem snapshot.'
            }
            if ($templateSnapshot.border.initial_size -ne 256) {
                throw 'Bounded noise hard world border was not persisted independently.'
            }
        }
        if ($templateName -like 'central_island*') {
            if (-not $templateDefinition.definition.level_stem_json.Contains('rogueengine:central_island')) {
                throw 'Central island generator was not persisted in the level stem snapshot.'
            }
            if ($templateSnapshot.border.initial_size -ne 512) {
                throw 'Central island hard world border was not persisted independently.'
            }
        }
        if ($templateName -eq 'central_island_layers') {
            $stemJson = $templateDefinition.definition.level_stem_json
            if (-not $stemJson.Contains('minecraft:grass_block') -or -not $stemJson.Contains('minecraft:dirt') -or
                -not $stemJson.Contains('minecraft:coal_ore')) {
                throw 'Central island custom layers were not persisted in the level stem snapshot.'
            }
        }
        $templateArchived = Invoke-Rcon "rogueengine instance archive $templateId"
        if ($templateArchived -notlike "Archived $templateId") {
            throw "$templateName box archive failed: $templateArchived"
        }
        $templateRestored = Invoke-Rcon "rogueengine instance restore $templateId"
        if ($templateRestored -notlike "Restored $templateId") {
            throw "$templateName box restore failed: $templateRestored"
        }
        $templateRearchived = Invoke-Rcon "rogueengine instance archive $templateId"
        if ($templateRearchived -notlike "Archived $templateId") {
            throw "$templateName re-archive failed: $templateRearchived"
        }
    }
    $mapCreated = Invoke-Rcon 'rogueengine instance create rogueengine:map_pool create-map rogueengine:example_room'
    if ($mapCreated -notmatch 'Preparing box ([0-9a-f-]{36})') {
        throw "Map pool box creation failed: $mapCreated"
    }
    $mapId = $Matches[1]
    $mapActive = $false
    for ($i = 0; $i -lt 60; $i++) {
        $mapInfo = Invoke-Rcon "rogueengine instance info $mapId"
        if ($mapInfo -like '*state=ACTIVE*') {
            $mapActive = $true
            break
        }
        if ($mapInfo -like '*state=RECOVERY_REQUIRED*') {
            throw "Map pool box requires recovery: $mapInfo"
        }
        Start-Sleep -Seconds 1
    }
    if (-not $mapActive) {
        throw 'Map pool box did not become active.'
    }
    $catalog = Get-Content (Join-Path $worldDir 'data/rogueengine/catalog-v1.json') -Raw | ConvertFrom-Json
    $mapDefinition = $catalog.instances | Where-Object { $_.uuid -eq $mapId } | Select-Object -First 1
    if ($null -eq $mapDefinition.definition.scene_snapshot -or $mapDefinition.definition.scene_snapshot.scene_id -ne 'rogueengine:example_room') {
        throw 'Map pool selection was not persisted in the box definition.'
    }
    $reset = Invoke-Rcon "rogueengine instance reset $mapId"
    if ($reset -notlike "Resetting box $mapId") {
        throw "Map reset failed: $reset"
    }
    $mapActive = $false
    for ($i = 0; $i -lt 60; $i++) {
        $mapInfo = Invoke-Rcon "rogueengine instance info $mapId"
        if ($mapInfo -like '*state=ACTIVE*') {
            $mapActive = $true
            break
        }
        if ($mapInfo -like '*state=RECOVERY_REQUIRED*') {
            throw "Map reset requires recovery: $mapInfo"
        }
        Start-Sleep -Seconds 1
    }
    if (-not $mapActive) {
        throw 'Map pool box did not become active after reset.'
    }
    $catalog = Get-Content (Join-Path $worldDir 'data/rogueengine/catalog-v1.json') -Raw | ConvertFrom-Json
    $mapDefinition = $catalog.instances | Where-Object { $_.uuid -eq $mapId } | Select-Object -First 1
    if ($mapDefinition.definition.scene_snapshot.reset_count -ne 1) {
        throw 'Map reset count was not persisted.'
    }
    $mapArchived = Invoke-Rcon "rogueengine instance archive $mapId"
    if ($mapArchived -notlike "Archived $mapId") {
        throw "Map pool box archive failed: $mapArchived"
    }
    $mapRestored = Invoke-Rcon "rogueengine instance restore $mapId"
    if ($mapRestored -notlike "Restored $mapId") {
        throw "Map pool box restore failed: $mapRestored"
    }
    $snapshotCreated = Invoke-Rcon 'rogueengine instance create rogueengine:map_pool_snapshot Snapshot Box'
    if ($snapshotCreated -notmatch 'Preparing box ([0-9a-f-]{36})') {
        throw "Snapshot box creation failed: $snapshotCreated"
    }
    $snapshotId = $Matches[1]
    $snapshotActive = $false
    for ($i = 0; $i -lt 60; $i++) {
        $snapshotInfo = Invoke-Rcon "rogueengine instance info $snapshotId"
        if ($snapshotInfo -like '*state=ACTIVE*') {
            $snapshotActive = $true
            break
        }
        if ($snapshotInfo -like '*state=RECOVERY_REQUIRED*') {
            throw "Snapshot box requires recovery: $snapshotInfo"
        }
        Start-Sleep -Seconds 1
    }
    if (-not $snapshotActive) {
        throw 'Snapshot box did not become active.'
    }
    $catalog = Get-Content (Join-Path $worldDir 'data/rogueengine/catalog-v1.json') -Raw | ConvertFrom-Json
    $snapshotDefinition = $catalog.instances | Where-Object { $_.uuid -eq $snapshotId } | Select-Object -First 1
    if ($snapshotDefinition.definition.scene_snapshot.hashes.source_kind -ne 'snapshot' -or
        $snapshotDefinition.definition.scene_snapshot.hashes.source_id -ne 'rogueengine:example_room') {
        throw 'Snapshot source metadata was not persisted.'
    }
    $snapshotReset = Invoke-Rcon "rogueengine instance reset $snapshotId"
    if ($snapshotReset -notlike "Resetting box $snapshotId") {
        throw "Snapshot reset failed: $snapshotReset"
    }
    $snapshotActive = $false
    for ($i = 0; $i -lt 60; $i++) {
        $snapshotInfo = Invoke-Rcon "rogueengine instance info $snapshotId"
        if ($snapshotInfo -like '*state=ACTIVE*') {
            $snapshotActive = $true
            break
        }
        if ($snapshotInfo -like '*state=RECOVERY_REQUIRED*') {
            throw "Snapshot reset requires recovery: $snapshotInfo"
        }
        Start-Sleep -Seconds 1
    }
    if (-not $snapshotActive) {
        throw 'Snapshot box did not become active after reset.'
    }
    $catalog = Get-Content (Join-Path $worldDir 'data/rogueengine/catalog-v1.json') -Raw | ConvertFrom-Json
    $snapshotDefinition = $catalog.instances | Where-Object { $_.uuid -eq $snapshotId } | Select-Object -First 1
    if ($snapshotDefinition.definition.scene_snapshot.reset_count -ne 1) {
        throw 'Snapshot reset count was not persisted.'
    }
    $snapshotArchived = Invoke-Rcon "rogueengine instance archive $snapshotId"
    if ($snapshotArchived -notlike "Archived $snapshotId") {
        throw "Snapshot box archive failed: $snapshotArchived"
    }
    $snapshotRestored = Invoke-Rcon "rogueengine instance restore $snapshotId"
    if ($snapshotRestored -notlike "Restored $snapshotId") {
        throw "Snapshot box restore failed: $snapshotRestored"
    }
    $null = Invoke-Rcon 'rogueengine admin stats'
    Write-Output "Server smoke test passed: $id (isolated=$isolatedId, templates=$($templateIds -join ','), map=$mapId, snapshot=$snapshotId)"
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
