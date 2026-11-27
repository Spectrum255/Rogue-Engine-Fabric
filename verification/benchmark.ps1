param(
    [int[]]$DimensionCounts = @(1, 4, 8, 16),
    [string]$JavaHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$runDir = Join-Path $repo 'build/benchmark'
$levelName = 'benchmark-' + [Guid]::NewGuid().ToString('N')
$rconPassword = [Guid]::NewGuid().ToString('N')
$worldDir = Join-Path $runDir $levelName
$packDir = Join-Path $worldDir 'datapacks/rogue-benchmark/data/rogue-engine-fabric/rogue-engine-fabric/profile'
New-Item -ItemType Directory -Path $packDir -Force | Out-Null
@'
{
  "pack": {
    "pack_format": 121,
    "min_format": 121,
    "max_format": 121,
    "description": "Rogue Engine benchmark data pack"
  }
}
'@ | Set-Content (Join-Path $worldDir 'datapacks/rogue-benchmark/pack.mcmeta') -Encoding utf8
@'
{
  "schema_version": 1,
  "max_active_instances": 64,
  "max_pending_operations": 64,
  "preload_radius": 0,
  "preload_chunks_per_tick": 1,
  "preload_tick_budget_ms": 5,
  "preload_timeout_seconds": 60,
  "archive_min_tick_budget_ms": 20,
  "destroy_confirmation_ttl_seconds": 30,
  "quarantine_retention_days": 7,
  "fallback_dimension": "minecraft:overworld"
}
'@ | Set-Content (Join-Path $worldDir 'datapacks/rogue-benchmark/data/rogue-engine-fabric/engine.json') -Encoding utf8
@'
{
  "schema_version": 1,
  "dimension_type": "minecraft:overworld",
  "generator": {"kind": "stem", "id": "minecraft:overworld"},
  "seed": {"mode": "random"},
  "spawn": {"mode": "search", "radius": 1},
  "border": {"center_x": 0.0, "center_z": 0.0, "initial_size": 64.0, "warning_blocks": 4.0}
}
'@ | Set-Content (Join-Path $packDir 'benchmark.json') -Encoding utf8
@"
eula=true
"@ | Set-Content (Join-Path $runDir 'eula.txt') -Encoding utf8
@"
server-port=25566
enable-rcon=true
rcon.port=25567
rcon.password=$rconPassword
online-mode=false
level-name=$levelName
level-seed=123456789
"@ | Set-Content (Join-Path $runDir 'server.properties') -Encoding utf8
if (-not $JavaHome) { throw 'JAVA_HOME must point to JDK 25.' }
$env:JAVA_HOME = $JavaHome
$startInfo = [System.Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = Join-Path $JavaHome 'bin/java.exe'
$startInfo.WorkingDirectory = $repo
$startInfo.UseShellExecute = $false
$startInfo.ArgumentList.Add('-cp')
$startInfo.ArgumentList.Add((Join-Path $repo 'gradle/wrapper/gradle-wrapper.jar'))
$startInfo.ArgumentList.Add('org.gradle.wrapper.GradleWrapperMain')
$startInfo.ArgumentList.Add('-p'); $startInfo.ArgumentList.Add($repo)
$startInfo.ArgumentList.Add("-ProgueRunDir=$runDir")
$startInfo.ArgumentList.Add('runServer')
$startInfo.ArgumentList.Add('--args=--nogui')
$startInfo.ArgumentList.Add('--no-configuration-cache')
$startInfo.RedirectStandardOutput = $true
$startInfo.RedirectStandardError = $true
$stdoutStream = [System.IO.StreamWriter]::new((Join-Path $runDir 'benchmark-stdout.log'))
$stderrStream = [System.IO.StreamWriter]::new((Join-Path $runDir 'benchmark-stderr.log'))
$process = [System.Diagnostics.Process]::Start($startInfo)
$stdoutTask = $process.StandardOutput.BaseStream.CopyToAsync($stdoutStream.BaseStream)
$stderrTask = $process.StandardError.BaseStream.CopyToAsync($stderrStream.BaseStream)
$rcon = Join-Path $PSScriptRoot 'dimension-probe/rcon.py'
function Invoke-Rcon([string]$Command) {
    $output = & python $rcon $Command --password $rconPassword 2>&1
    if ($LASTEXITCODE -ne 0) { throw "RCON failed: $Command`n$output" }
    return ($output | Out-String).Trim()
}
try {
    $ready = $false
    for ($i = 0; $i -lt 120; $i++) {
        Start-Sleep -Seconds 1
        try { $null = Invoke-Rcon 'list'; $ready = $true; break } catch { }
    }
    if (-not $ready) {
        throw 'Timed out waiting for benchmark server RCON.'
    }
    foreach ($count in $DimensionCounts) {
        $timer = [System.Diagnostics.Stopwatch]::StartNew()
        $roundIds = @()
        for ($i = 0; $i -lt $count; $i++) {
            $created = Invoke-Rcon 'rogue-engine instance create rogue-engine-fabric:benchmark Benchmark'
            if ($created -notmatch 'Created box ([0-9a-f-]{36})') {
                throw "Benchmark box creation failed: $created"
            }
            $roundIds += $Matches[1]
        }
        $timer.Stop()
        Write-Output "dimensions=$count createTotalMs=$($timer.ElapsedMilliseconds)"
        foreach ($id in $roundIds) {
            $archived = Invoke-Rcon "rogue-engine instance archive $id"
            if ($archived -notlike "Archived $id") {
                throw "Benchmark box archive failed: $archived"
            }
        }
    }
    $null = Invoke-Rcon 'rogue-engine admin stats'
} finally {
    if (-not $process.HasExited) {
        try { $null = Invoke-Rcon 'stop' } catch { }
        if (-not $process.WaitForExit(30000)) { $process.Kill() }
    }
    $process.WaitForExit()
    [void]$stdoutTask.Wait(5000)
    [void]$stderrTask.Wait(5000)
    $stdoutStream.Dispose()
    $stderrStream.Dispose()
}
