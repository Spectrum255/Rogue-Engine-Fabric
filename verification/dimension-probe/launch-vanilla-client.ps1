param(
    [int]$SourceProcessId = 0,
    [string]$InstanceRoot = 'D:\mc\.minecraft\versions\26.3-Fabric',
    [string]$GameDirectory = 'D:\minecraft mod\Rogue Engine Fabric\verification\dimension-probe\run\vanilla-client',
    [string]$ServerAddress = '127.0.0.1:25566',
    [string]$PlayerName = 'RogueProbeClient',
    [switch]$Wait
)

$ErrorActionPreference = 'Stop'

if ($SourceProcessId -eq 0) {
    $source = Get-CimInstance Win32_Process |
        Where-Object { $_.Name -eq 'javaw.exe' -and $_.CommandLine -like '*26.3-Fabric.jar*KnotClient*' } |
        Sort-Object CreationDate -Descending |
        Select-Object -First 1
    if ($null -eq $source) {
        throw 'No running 26.3-Fabric client was found to reuse its library classpath.'
    }
    $SourceProcessId = [int]$source.ProcessId
}

$source = Get-CimInstance Win32_Process -Filter "ProcessId=$SourceProcessId"
if ($null -eq $source -or $source.CommandLine -notlike '*26.3-Fabric.jar*KnotClient*') {
    throw 'The source process is not a 26.3-Fabric client.'
}

$classpathMatch = [regex]::Match($source.CommandLine, '-cp\s+(?<classpath>.+?)\s+net\.fabricmc\.loader\.impl\.launch\.knot\.KnotClient')
if (-not $classpathMatch.Success) {
    throw 'Could not extract the client library classpath.'
}
$classpath = $classpathMatch.Groups['classpath'].Value.Trim('"')

$modsDirectory = Join-Path $GameDirectory 'mods'
$configDirectory = Join-Path $GameDirectory 'config'
New-Item -ItemType Directory -Path $modsDirectory, $configDirectory -Force | Out-Null
if (@(Get-ChildItem -LiteralPath $modsDirectory -File).Count -ne 0) {
    throw "The vanilla test mods directory must remain empty: $modsDirectory"
}

$java = 'D:\mc\jre-x64\bin\javaw.exe'
$jar = Join-Path $InstanceRoot '26.3-Fabric.jar'
$natives = Join-Path $InstanceRoot 'natives-windows-x86_64'
$startInfo = [System.Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = $java
$startInfo.WorkingDirectory = $GameDirectory
$startInfo.UseShellExecute = $false
$startInfo.CreateNoWindow = $true
$startInfo.ArgumentList.Add('-Xmx2G')
$startInfo.ArgumentList.Add('-Dfile.encoding=UTF-8')
$startInfo.ArgumentList.Add("-Dlog4j.configurationFile=$InstanceRoot\log4j2.xml")
$startInfo.ArgumentList.Add("-Dminecraft.client.jar=$jar")
$startInfo.ArgumentList.Add("-Djava.library.path=$natives\java")
$startInfo.ArgumentList.Add("-Dorg.lwjgl.system.SharedLibraryExtractPath=$natives\lwjgl")
$startInfo.ArgumentList.Add("-Dio.netty.native.workdir=$natives\netty")
$startInfo.ArgumentList.Add('--sun-misc-unsafe-memory-access=allow')
$startInfo.ArgumentList.Add('--enable-native-access=ALL-UNNAMED')
$startInfo.ArgumentList.Add('-cp')
$startInfo.ArgumentList.Add($classpath)
$startInfo.ArgumentList.Add('net.fabricmc.loader.impl.launch.knot.KnotClient')
$startInfo.ArgumentList.Add('--username')
$startInfo.ArgumentList.Add($PlayerName)
$startInfo.ArgumentList.Add('--version')
$startInfo.ArgumentList.Add('26.3-Fabric')
$startInfo.ArgumentList.Add('--gameDir')
$startInfo.ArgumentList.Add($GameDirectory)
$startInfo.ArgumentList.Add('--assetsDir')
$startInfo.ArgumentList.Add('D:\mc\.minecraft\assets')
$startInfo.ArgumentList.Add('--assetIndex')
$startInfo.ArgumentList.Add('34')
$startInfo.ArgumentList.Add('--uuid')
$startInfo.ArgumentList.Add('00000000-0000-0000-0000-000000000042')
$startInfo.ArgumentList.Add('--accessToken')
$startInfo.ArgumentList.Add('0')
$startInfo.ArgumentList.Add('--clientId')
$startInfo.ArgumentList.Add('0')
$startInfo.ArgumentList.Add('--xuid')
$startInfo.ArgumentList.Add('0')
$startInfo.ArgumentList.Add('--versionType')
$startInfo.ArgumentList.Add('Rogue Probe')
$startInfo.ArgumentList.Add('--width')
$startInfo.ArgumentList.Add('854')
$startInfo.ArgumentList.Add('--height')
$startInfo.ArgumentList.Add('480')
$startInfo.ArgumentList.Add('--quickPlayMultiplayer')
$startInfo.ArgumentList.Add($ServerAddress)

$process = [System.Diagnostics.Process]::Start($startInfo)
Write-Output "Client PID: $($process.Id)"
Write-Output "Player: $PlayerName"
Write-Output "Game directory: $GameDirectory"
Write-Output "Mods directory file count: $(@(Get-ChildItem -LiteralPath $modsDirectory -File).Count)"
if ($Wait) {
    $process.WaitForExit()
}
