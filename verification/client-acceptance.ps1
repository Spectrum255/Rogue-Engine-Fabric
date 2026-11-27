param(
    [string]$JavaHome = $env:JAVA_HOME,
    [int]$RconPort = 25587,
    [int]$ServerPort = 25586
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$runDir = Join-Path $repo 'build/client-acceptance'
$serverDir = Join-Path $runDir 'server'
$clientDir = Join-Path $runDir 'client'
$levelName = 'acceptance-' + [Guid]::NewGuid().ToString('N')
$worldDir = Join-Path $serverDir $levelName
$rconPassword = [Guid]::NewGuid().ToString('N')
$resultFile = Join-Path $clientDir 'acceptance-result.txt'
$packRoot = Join-Path $worldDir 'datapacks/rogue-acceptance/data/rogue-engine-fabric/rogue-engine-fabric'
$packScene = Join-Path $packRoot 'map_scene'
$packPool = Join-Path $packRoot 'map_pool'
$packTemplate = Join-Path $packRoot 'worldgen_template'
$packProfile = Join-Path $packRoot 'profile'
New-Item -ItemType Directory -Path $packScene,$packPool,$packTemplate,$packProfile,(Join-Path $serverDir 'mods') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $clientDir 'mods') -Force | Out-Null
if (Test-Path $resultFile) { Remove-Item $resultFile -Force }
$debugFile = Join-Path $clientDir 'acceptance-debug.log'
if (Test-Path $debugFile) { Remove-Item $debugFile -Force }
@'
onboardAccessibility:false
skipMultiplayerWarning:true
tutorialStep:none
'@ | Set-Content (Join-Path $clientDir 'options.txt') -Encoding utf8

if (-not $JavaHome) {
    throw 'JAVA_HOME must point to JDK 25.'
}
$env:JAVA_HOME = $JavaHome

& (Join-Path $repo 'gradlew.bat') -p (Join-Path $repo 'verification/client-acceptance') build
if ($LASTEXITCODE -ne 0) {
    throw 'Client acceptance harness build failed.'
}
& (Join-Path $repo 'gradlew.bat') build
if ($LASTEXITCODE -ne 0) {
    throw 'Main mod build failed.'
}
$acceptanceJar = Get-ChildItem (Join-Path $repo 'verification/client-acceptance/build/libs') -Filter '*.jar' |
    Where-Object { $_.Name -notlike '*-sources.jar' } | Select-Object -First 1
if ($null -eq $acceptanceJar) {
    throw 'Client acceptance JAR is missing.'
}
Copy-Item $acceptanceJar.FullName (Join-Path $clientDir 'mods/rogue-engine-client-acceptance.jar') -Force

@'
{
  "pack": {
    "pack_format": 121,
    "min_format": 121,
    "max_format": 121,
    "description": "Rogue Engine client acceptance pack"
  }
}
'@ | Set-Content (Join-Path $worldDir 'datapacks/rogue-acceptance/pack.mcmeta') -Encoding utf8

@'
{
  "schema_version": 2,
  "source": {"kind": "snapshot", "id": "rogue-engine-fabric:example_room"},
  "bounds": {"min": [0, 128, 0], "max": [4, 131, 4]},
  "anchor": [0, 128, 0],
  "spawn": [2, 129, 2],
  "rotation": "none",
  "mirror": "none",
  "processors": [],
  "refresh": {"triggers": ["manual"], "player_reset": "full"}
}
'@ | Set-Content (Join-Path $packScene 'acceptance_full.json') -Encoding utf8

@'
{
  "schema_version": 1,
  "entries": [{"scene": "rogue-engine-fabric:acceptance_full", "weight": 1}],
  "default_range": {"scenes": [], "tags": []},
  "repeat_policy": "allow",
  "refresh": {"triggers": ["manual"], "player_reset": "full"},
  "budget": {
    "max_chunks_per_tick": 4,
    "max_tick_ms": 5,
    "max_entities": 128,
    "max_block_entities": 256
  }
}
'@ | Set-Content (Join-Path $packPool 'acceptance_full_pool.json') -Encoding utf8

@'
{
  "schema_version": 1,
  "generator_kind": "map_pool",
  "base_stem": "minecraft:overworld",
  "map_pool": "rogue-engine-fabric:acceptance_full_pool",
  "default_range": {"scenes": [], "tags": []}
}
'@ | Set-Content (Join-Path $packTemplate 'acceptance_full_template.json') -Encoding utf8

@'
{
  "schema_version": 2,
  "dimension_type": "minecraft:overworld",
  "generator": {"kind": "template", "id": "rogue-engine-fabric:acceptance_full_template"},
  "seed": {"mode": "random"},
  "spawn": {"mode": "fixed", "x": 2.5, "y": 129.0, "z": 2.5, "yaw": 0.0},
  "border": {"center_x": 0.0, "center_z": 0.0, "initial_size": 128.0, "warning_blocks": 8.0}
}
'@ | Set-Content (Join-Path $packProfile 'acceptance_full.json') -Encoding utf8

@'
{
  "schema_version": 2,
  "source": {"kind": "snapshot", "id": "example:captured"},
  "bounds": {"min": [0, 128, 0], "max": [4, 129, 4]},
  "anchor": [0, 128, 0],
  "spawn": [2, 129, 2],
  "rotation": "none",
  "mirror": "none",
  "processors": [],
  "refresh": {"triggers": ["manual"], "player_reset": "none"}
}
'@ | Set-Content (Join-Path $packScene 'acceptance_snapshot.json') -Encoding utf8

@'
{
  "schema_version": 1,
  "entries": [{"scene": "rogue-engine-fabric:acceptance_snapshot", "weight": 1}],
  "default_range": {"scenes": [], "tags": []},
  "repeat_policy": "allow",
  "refresh": {"triggers": ["manual"], "player_reset": "none"},
  "budget": {
    "max_chunks_per_tick": 4,
    "max_tick_ms": 5,
    "max_entities": 128,
    "max_block_entities": 256
  }
}
'@ | Set-Content (Join-Path $packPool 'acceptance_snapshot_pool.json') -Encoding utf8

@'
{
  "schema_version": 1,
  "generator_kind": "map_pool",
  "base_stem": "minecraft:overworld",
  "map_pool": "rogue-engine-fabric:acceptance_snapshot_pool",
  "default_range": {"scenes": [], "tags": []}
}
'@ | Set-Content (Join-Path $packTemplate 'acceptance_snapshot_template.json') -Encoding utf8

@'
{
  "schema_version": 2,
  "dimension_type": "minecraft:overworld",
  "generator": {"kind": "template", "id": "rogue-engine-fabric:acceptance_snapshot_template"},
  "seed": {"mode": "random"},
  "spawn": {"mode": "fixed", "x": 2.5, "y": 129.0, "z": 2.5, "yaw": 0.0},
  "border": {"center_x": 0.0, "center_z": 0.0, "initial_size": 128.0, "warning_blocks": 8.0}
}
'@ | Set-Content (Join-Path $packProfile 'acceptance_snapshot.json') -Encoding utf8

@"
eula=true
"@ | Set-Content (Join-Path $serverDir 'eula.txt') -Encoding utf8

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
motd=Rogue Engine Client Acceptance
"@ | Set-Content (Join-Path $serverDir 'server.properties') -Encoding utf8

@'
[
  {
    "uuid": "8f3bb5e7-f7f0-36be-9f51-30fb26218b0e",
    "name": "RogueAcceptance",
    "level": 4,
    "usesBypassPlayerLimit": true
  }
]
'@ | Set-Content (Join-Path $serverDir 'ops.json') -Encoding utf8

$rcon = Join-Path $repo 'verification/dimension-probe/rcon.py'
$logDir = Join-Path $env:TEMP ('rogue-client-acceptance-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $logDir -Force | Out-Null
$serverStdout = Join-Path $logDir 'server-stdout.log'
$serverStderr = Join-Path $logDir 'server-stderr.log'
$clientStdout = Join-Path $logDir 'client-stdout.log'
$clientStderr = Join-Path $logDir 'client-stderr.log'

function Start-ProcessWithLogs([string]$FileName, [string]$WorkingDirectory, [string[]]$Arguments, [string]$Stdout, [string]$Stderr) {
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $FileName
    $startInfo.WorkingDirectory = $WorkingDirectory
    $startInfo.UseShellExecute = $false
    foreach ($argument in $Arguments) {
        $startInfo.ArgumentList.Add($argument)
    }
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $stdoutStream = [System.IO.FileStream]::new($Stdout, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write, [System.IO.FileShare]::ReadWrite)
    $stderrStream = [System.IO.FileStream]::new($Stderr, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write, [System.IO.FileShare]::ReadWrite)
    $process = [System.Diagnostics.Process]::Start($startInfo)
    return [pscustomobject]@{
        Process = $process
        StdoutStream = $stdoutStream
        StderrStream = $stderrStream
        StdoutTask = $process.StandardOutput.BaseStream.CopyToAsync($stdoutStream)
        StderrTask = $process.StandardError.BaseStream.CopyToAsync($stderrStream)
    }
}

function Start-GradleProcess([string]$Stdout, [string]$Stderr, [string[]]$Arguments) {
    $gradleArguments = @('-cp', (Join-Path $repo 'gradle/wrapper/gradle-wrapper.jar'), 'org.gradle.wrapper.GradleWrapperMain')
    $gradleArguments += $Arguments
    return Start-ProcessWithLogs (Join-Path $JavaHome 'bin/java.exe') $repo $gradleArguments $Stdout $Stderr
}

function Invoke-Rcon([string]$Command) {
    $output = & python $rcon $Command --port $RconPort --password $rconPassword 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "RCON failed: $Command`n$output"
    }
    return ($output | Out-String).Trim()
}

function Stop-GradleProcess($Handle) {
    if ($null -eq $Handle -or $Handle.Process.HasExited) { return }
    try { $null = Invoke-Rcon 'stop' } catch { }
    if (-not $Handle.Process.WaitForExit(30000)) {
        $Handle.Process.Kill()
    }
    $Handle.Process.WaitForExit()
    $null = $Handle.StdoutTask.GetAwaiter().GetResult()
    $null = $Handle.StderrTask.GetAwaiter().GetResult()
    $Handle.StdoutStream.Dispose()
    $Handle.StderrStream.Dispose()
}

$server = $null
$client = $null
try {
    $launchConfig = Join-Path $repo '.gradle/loom-cache/launch.cfg'
    $serverArgFile = Join-Path $repo 'build/loom-cache/argFiles/runServer'
    $serverArguments = @(
        "-Dfabric.dli.config=$launchConfig",
        '-Dfabric.dli.env=server',
        '-Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotServer',
        "-Duser.dir=$serverDir",
        "@$serverArgFile",
        '--sun-misc-unsafe-memory-access=allow',
        '--enable-native-access=ALL-UNNAMED',
        '-Dfile.encoding=UTF-8',
        '-Duser.country=CN',
        '-Duser.language=zh',
        '-Duser.variant=',
        'net.fabricmc.devlaunchinjector.Main',
        '--nogui',
        '--port',
        "$ServerPort"
    )
    $server = Start-ProcessWithLogs (Join-Path $JavaHome 'bin/java.exe') $serverDir $serverArguments $serverStdout $serverStderr
    $ready = $false
    for ($i = 0; $i -lt 120; $i++) {
        Start-Sleep -Seconds 1
        try {
            $null = Invoke-Rcon 'list'
            $ready = $true
            break
        } catch {
            if ($server.Process.HasExited) {
                throw "Server exited before RCON was ready.`n$(Get-Content $serverStdout -Raw -ErrorAction SilentlyContinue)`n$(Get-Content $serverStderr -Raw -ErrorAction SilentlyContinue)"
            }
        }
    }
    if (-not $ready) {
        throw 'Timed out waiting for acceptance server RCON.'
    }

    $clientArgs = @(
        '-p', $repo,
        "-ProgueClientDir=$clientDir",
        "-ProgueAcceptanceWorldDir=$worldDir",
        "-ProgueAcceptanceResult=$resultFile",
        "-ProgueAcceptanceServer=127.0.0.1:$ServerPort",
        'runClient',
        '--no-configuration-cache',
        "--args=--username RogueAcceptance"
    )
    $client = Start-GradleProcess $clientStdout $clientStderr $clientArgs
    $completed = $false
    for ($i = 0; $i -lt 600; $i++) {
        Start-Sleep -Seconds 1
        if (Test-Path $resultFile) {
            $completed = $true
            break
        }
        if ($client.Process.HasExited) {
            break
        }
    }
    if (-not $completed) {
        throw "Client acceptance did not finish.`n$(Get-Content $clientStdout -Raw -ErrorAction SilentlyContinue)`n$(Get-Content $clientStderr -Raw -ErrorAction SilentlyContinue)"
    }
    $result = (Get-Content $resultFile -Raw).Trim()
    if (-not $result.StartsWith('PASS')) {
        throw "Client acceptance failed: $result"
    }
    Write-Output "Client acceptance passed: $result"
    Write-Output "Logs: $logDir"
} finally {
    if ($null -ne $client) {
        if (-not $client.Process.HasExited) {
            $client.Process.Kill()
        }
        $client.Process.WaitForExit()
        $null = $client.StdoutTask.GetAwaiter().GetResult()
        $null = $client.StderrTask.GetAwaiter().GetResult()
        $client.StdoutStream.Dispose()
        $client.StderrStream.Dispose()
    }
    if ($null -ne $server) {
        Stop-GradleProcess $server
    }
}
