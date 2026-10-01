param(
    [string[]]$Versions = @(),
    [ValidateSet('fabric', 'forge', 'neoforge')]
    [string[]]$Platforms = @('fabric', 'forge', 'neoforge'),
    [switch]$BuildOnly
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$matrix = Get-Content (Join-Path $root 'gradle/minecraft-versions.json') -Raw | ConvertFrom-Json -AsHashtable
if ($Versions.Count -eq 0) { $Versions = @($matrix.Keys) }
$work = Join-Path $root 'build/verification'
$dist = Join-Path $root 'build/distributions'
New-Item -ItemType Directory -Force $work, $dist | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Find-Java([int]$major) {
    $configured = [Environment]::GetEnvironmentVariable("JAVA${major}_HOME")
    if ($configured) { return Join-Path $configured 'bin/java.exe' }
    $installed = Get-ChildItem 'C:/Program Files/Java' -Directory | Where-Object { $_.Name -like "jdk-$major.*" } | Sort-Object Name -Descending | Select-Object -First 1
    if (!$installed) { throw "Set JAVA${major}_HOME to a JDK $major installation." }
    return Join-Path $installed.FullName 'bin/java.exe'
}

function Run-Process([string]$exe, [string[]]$arguments, [string]$directory, [string]$log, [int]$timeout = 1200) {
    $info = [Diagnostics.ProcessStartInfo]::new($exe)
    $info.WorkingDirectory = $directory
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    foreach ($argument in $arguments) { $info.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $info
    try {
        [void]$process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        $timedOut = !$process.WaitForExit($timeout * 1000)
        if ($timedOut) {
            $process.Kill($true)
            $process.WaitForExit()
        }
        $output = $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult()
        [IO.File]::WriteAllText($log, $output)
        if ($timedOut) { throw "Process timed out: $exe. See $log" }
        if ($process.ExitCode -ne 0) { throw "Process exited with $($process.ExitCode). See $log" }
        return $output
    } finally { $process.Dispose() }
}

function Download([string]$url, [string]$path) {
    if (Test-Path -LiteralPath $path) { return }
    $partial = "$path.part"
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        try {
            Invoke-WebRequest $url -OutFile $partial
            Move-Item -LiteralPath $partial -Destination $path -Force
            return
        } catch {
            if ($attempt -eq 3) { throw }
            Start-Sleep -Seconds 2
        }
    }
}

function New-ProbeJar([string]$smokeJar, [string]$releaseJar, [string]$probeJar, [string]$platform) {
    $smoke = [IO.Compression.ZipFile]::OpenRead($smokeJar)
    $release = [IO.Compression.ZipFile]::OpenRead($releaseJar)
    $stream = [IO.File]::Open($probeJar, [IO.FileMode]::Create)
    $probe = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($entry in $release.Entries | Where-Object { $_.FullName.EndsWith('.class') }) {
            $testEntry = $smoke.GetEntry($entry.FullName)
            if (!$testEntry) { throw "Production class missing from smoke build: $($entry.FullName)" }
            $a = $entry.Open(); $b = $testEntry.Open()
            try {
                $hashA = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($a))
                $hashB = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($b))
                if ($hashA -ne $hashB) { throw "Production class differs in smoke build: $($entry.FullName)" }
            } finally { $a.Dispose(); $b.Dispose() }
        }
        foreach ($entry in $smoke.Entries | Where-Object {
            $_.FullName.StartsWith('com/simplekillcommand/test/') -or $_.FullName -eq 'simplekillcommand-test.mixins.json' -or $_.FullName.EndsWith('-refmap.json')
        }) {
            $copy = $probe.CreateEntry($entry.FullName)
            $copy.LastWriteTime = [DateTimeOffset]::new(1980, 1, 1, 0, 0, 0, [TimeSpan]::Zero)
            $inputStream = $entry.Open(); $outputStream = $copy.Open()
            try { $inputStream.CopyTo($outputStream) } finally { $inputStream.Dispose(); $outputStream.Dispose() }
        }
        if ($platform -eq 'fabric') {
            $name = 'fabric.mod.json'
            $content = @{ schemaVersion = 1; id = 'simplekillcommand_test'; version = '1.0.0'; environment = 'server';
                mixins = @('simplekillcommand-test.mixins.json'); depends = @{ simplekillcommand = '*' } } | ConvertTo-Json -Depth 5
        } else {
            $name = if ($platform -eq 'forge') { 'META-INF/mods.toml' } else { 'META-INF/neoforge.mods.toml' }
            $content = "modLoader=`"javafml`"`nloaderVersion=`"[1,)`"`nlicense=`"MIT`"`n" + (Get-Content (Join-Path $root 'server-test/mods.toml') -Raw)
        }
        $metadata = $probe.CreateEntry($name)
        $metadata.LastWriteTime = [DateTimeOffset]::new(1980, 1, 1, 0, 0, 0, [TimeSpan]::Zero)
        $writer = [IO.StreamWriter]::new($metadata.Open())
        try { $writer.Write($content) } finally { $writer.Dispose() }
    } finally { $probe.Dispose(); $stream.Dispose(); $release.Dispose(); $smoke.Dispose() }
}

$java25 = Find-Java 25
$env:JAVA_HOME = Split-Path (Split-Path $java25 -Parent) -Parent
# Keep Windows AF_UNIX pipe names away from redirected or short-name Temp paths.
$env:JAVA_TOOL_OPTIONS = "$env:JAVA_TOOL_OPTIONS -Djdk.net.unixdomain.tmpdir=$($root)/build".Trim()
$version = (Get-Content (Join-Path $root 'gradle.properties') | Where-Object { $_ -match '^projectVersion=' }) -replace '^projectVersion=', ''
if (!$BuildOnly -and !(Test-Path (Join-Path $work 'client/node_modules/mineflayer/package.json'))) {
    $null = Run-Process 'cmd.exe' @('/d', '/c', 'npm.cmd', 'install', '--prefix', (Join-Path $work 'client'),
        '--no-audit', '--no-fund', '--ignore-scripts', 'mineflayer@4.39.0') $root (Join-Path $work 'client-install.log')
}
$clientRunnerSha = (Get-FileHash (Join-Path $PSScriptRoot 'client-smoke.cjs') -Algorithm SHA256).Hash.ToLowerInvariant()

foreach ($mc in $Versions) {
    if (!$matrix.Contains($mc)) { throw "Unknown Minecraft version: $mc" }
    foreach ($platform in $Platforms) {
        $target = $matrix[$mc]
        if ($platform -eq 'forge' -and !$target.forge) {
            Write-Host "SKIP $platform $mc (loader not published)"
            continue
        }
        $name = @{fabric='Fabric'; forge='Forge'; neoforge='NeoForge'}[$platform]
        $id = "$platform-$mc"
        Write-Host "BUILD $id"
        $base = "SimpleKillCommand-$name-mc$mc-$version"
        $arguments = @('/d', '/c', 'gradlew.bat', "-Pplatform=$platform", "-PminecraftVersion=$mc", ":${platform}:build", '--no-parallel', '--max-workers=2')
        $null = Run-Process 'cmd.exe' $arguments $root (Join-Path $work "$id-build.log")
        $jar = Join-Path $root "$platform/build/$mc/libs/$base.jar"
        Copy-Item -LiteralPath $jar -Destination $dist -Force
        if ($BuildOnly) { continue }
        $null = Run-Process 'cmd.exe' ($arguments + '-PserverTest=true') $root (Join-Path $work "$id-probe-build.log")
        $smokeJar = Join-Path $root "$platform/build/$mc-smoke/libs/$base-smoke.jar"
        $server = Join-Path $work $id
        $mods = Join-Path $server 'mods'
        New-Item -ItemType Directory -Force $mods, (Join-Path $server 'config') | Out-Null
        $deployed = Join-Path $mods "$base.jar"
        Copy-Item -LiteralPath $jar -Destination $deployed -Force
        $probe = Join-Path $mods 'server-test.jar'
        New-ProbeJar $smokeJar $jar $probe $platform
        $sha = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
        $probeSha = (Get-FileHash -LiteralPath $probe -Algorithm SHA256).Hash.ToLowerInvariant()
        $result = Join-Path $server 'verified.json'
        if (Test-Path -LiteralPath $result) {
            $previous = Get-Content $result -Raw | ConvertFrom-Json
            if ($previous.sha256 -eq $sha -and $previous.probeSha256 -eq $probeSha -and $previous.clientRunnerSha256 -eq $clientRunnerSha -and $null -ne $previous.gracefulShutdown) {
                Write-Host "PASS $id (unchanged verified artifacts)"
                continue
            }
        }
        [IO.File]::WriteAllText((Join-Path $server 'eula.txt'), "eula=true`n")
        [IO.File]::WriteAllText((Join-Path $server 'server.properties'), "server-ip=127.0.0.1`nserver-port=0`nonline-mode=false`nview-distance=2`nsimulation-distance=2`nspawn-protection=0`nmax-players=2`nlevel-type=minecraft:flat`ngenerator-settings={`"layers`": [{`"block`":`"minecraft:bedrock`",`"height`":1}],`"biome`":`"minecraft:plains`"}`nsync-chunk-writes=false`n")
        [IO.File]::WriteAllText((Join-Path $server 'config/simplekillcommand.properties'), "command.name=kill`ncommand.aliases=suicide,selfkill`ncommand.permission-level=0`ncommand.override-existing=true`nmessages.already-dead=&4$([char]0xc774)$([char]0xbbf8) $([char]0xc0ac)$([char]0xb9dd)$([char]0xd588)$([char]0xc2b5)$([char]0xb2c8)$([char]0xb2e4).`n")
        $java = Find-Java $target.java
        if ($platform -eq 'fabric') {
            $loader = '0.19.5'
            $launcher = Join-Path $server 'fabric-server-launch.jar'
            Download "https://meta.fabricmc.net/v2/versions/loader/$mc/$loader/1.1.2/server/jar" $launcher
            $manifest = Invoke-RestMethod 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
            $metadata = Invoke-RestMethod ($manifest.versions | Where-Object id -eq $mc | Select-Object -First 1).url
            $vanilla = Join-Path $server 'server.jar'
            if ((Test-Path $vanilla) -and (Get-FileHash $vanilla -Algorithm SHA1).Hash.ToLowerInvariant() -ne $metadata.downloads.server.sha1) {
                Remove-Item -LiteralPath $vanilla
            }
            Download $metadata.downloads.server.url $vanilla
            if ((Get-FileHash $vanilla -Algorithm SHA1).Hash.ToLowerInvariant() -ne $metadata.downloads.server.sha1) { throw "Minecraft server checksum mismatch: $mc" }
            $launch = @('-jar', $launcher, 'nogui')
        } else {
            $loader = $target[$platform]
            $coordinate = if ($platform -eq 'forge') { "$mc-$loader" } else { $loader }
            $repo = if ($platform -eq 'forge') { 'https://maven.minecraftforge.net/net/minecraftforge/forge' } else { 'https://maven.neoforged.net/releases/net/neoforged/neoforge' }
            $artifact = if ($platform -eq 'forge') { 'forge' } else { 'neoforge' }
            $installer = Join-Path $work "$id-installer.jar"
            Download "$repo/$coordinate/$artifact-$coordinate-installer.jar" $installer
            $argsFile = Join-Path $server "libraries/net/$(@{forge='minecraftforge/forge'; neoforge='neoforged/neoforge'}[$platform])/$coordinate/win_args.txt"
            if (!(Test-Path -LiteralPath $argsFile)) {
                Write-Host "INSTALL $id"
                $null = Run-Process $java @('-jar', $installer, '--installServer', $server) $server (Join-Path $work "$id-install.log")
            }
            $launch = @("@$argsFile", 'nogui')
        }
        Write-Host "SERVER $id"
        $smokeResult = Join-Path $server 'smoke-result.json'
        if (Test-Path -LiteralPath $smokeResult) { Remove-Item -LiteralPath $smokeResult }
        $output = Run-Process 'node.exe' (@((Join-Path $PSScriptRoot 'client-smoke.cjs'), $server, $java, $mc,
            '-Xms256M', '-Xmx1536M', "-Dsimplekillcommand.testReport=$smokeResult") + $launch) $server (Join-Path $work "$id-server.log") 600
        if ($output -notmatch 'SKC_SMOKE_PASS' -or !(Test-Path -LiteralPath $smokeResult)) { throw "Server tests failed for $id. See $work/$id-server.log" }
        $checks = Get-Content $smokeResult -Raw | ConvertFrom-Json
        if (!$checks.passed) { throw "Server tests failed for $id" }
        $clientChecks = Get-Content (Join-Path $server 'client-result.json') -Raw | ConvertFrom-Json
        if (!$clientChecks.passed -and !$clientChecks.skipped) { throw "Client tests failed for $id" }
        $shutdown = Get-Content (Join-Path $server 'server-stop-result.json') -Raw | ConvertFrom-Json
        @{minecraft=$mc; platform=$platform; loader=$loader; java=$target.java; sha256=$sha; probeSha256=$probeSha;
            checks=$checks.checks; clientChecks=$clientChecks.checks; clientRunnerSha256=$clientRunnerSha;
            clientSkipped=$clientChecks.skipped;
            gracefulShutdown=$shutdown.graceful;
            verifiedAt=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $result -Encoding utf8
        Write-Host "PASS $id ($($checks.checks.Count) server checks, $($clientChecks.checks.Count) client checks)"
    }
}

Get-ChildItem $dist -Filter '*.jar' | Sort-Object Name | ForEach-Object {
    "$((Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant())  $($_.Name)"
} | Set-Content (Join-Path $dist 'SHA256SUMS.txt') -Encoding ascii
