param(
    [string[]]$Versions = @(),
    [ValidateSet('paper', 'folia')]
    [string[]]$Platforms = @('paper', 'folia')
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$matrix = Get-Content (Join-Path $root 'gradle/minecraft-versions.json') -Raw | ConvertFrom-Json -AsHashtable
if ($Versions.Count -eq 0) { $Versions = @($matrix.Keys) }
$version = (Get-Content (Join-Path $root 'gradle.properties') | Where-Object { $_ -match '^projectVersion=' }) -replace '^projectVersion=', ''
$jar = Join-Path $root "bukkit/build/libs/SimpleKillCommand-Bukkit-$version.jar"
$work = Join-Path $root 'build/verification'
$headers = @{'User-Agent'="SimpleKillCommand/$version (https://github.com/yesidodo73/Simple-Kill-Command)"}
$manifest = Invoke-RestMethod 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'

foreach ($platform in $Platforms) {
    $project = Invoke-RestMethod -Headers $headers "https://fill.papermc.io/v3/projects/$platform"
    $available = @($project.versions.PSObject.Properties.Value | ForEach-Object { $_ })
    foreach ($mc in $Versions) {
        if ($available -notcontains $mc) { Write-Host "SKIP $platform $mc (not published)"; continue }
        $builds = Invoke-RestMethod -Headers $headers "https://fill.papermc.io/v3/projects/$platform/versions/$mc/builds"
        $build = $builds | Where-Object channel -eq 'STABLE' | Select-Object -First 1
        if (!$build) { $build = $builds | Select-Object -First 1 }
        if (!$build) { Write-Host "SKIP $platform $mc (no build)"; continue }
        $server = Join-Path $work "$platform-$mc"
        New-Item -ItemType Directory -Force (Join-Path $server 'plugins/SimpleKillCommand'), (Join-Path $server 'cache') | Out-Null
        Copy-Item $jar (Join-Path $server 'plugins') -Force
        $download = $build.downloads.'server:default'
        $launcher = Join-Path $server $download.name
        if (!(Test-Path $launcher)) { Invoke-WebRequest -Headers $headers $download.url -OutFile $launcher }
        if ((Get-FileHash $launcher -Algorithm SHA256).Hash.ToLowerInvariant() -ne $download.checksums.sha256) { throw "Server checksum mismatch: $platform $mc" }
        $metadata = Invoke-RestMethod ($manifest.versions | Where-Object id -eq $mc | Select-Object -First 1).url
        $vanilla = Join-Path $server "cache/mojang_$mc.jar"
        if (!(Test-Path $vanilla)) { Invoke-WebRequest $metadata.downloads.server.url -OutFile $vanilla }
        if ((Get-FileHash $vanilla -Algorithm SHA1).Hash.ToLowerInvariant() -ne $metadata.downloads.server.sha1) { throw "Minecraft checksum mismatch: $mc" }
        [IO.File]::WriteAllText((Join-Path $server 'eula.txt'), "eula=true`n")
        [IO.File]::WriteAllText((Join-Path $server 'server.properties'), "server-ip=127.0.0.1`nserver-port=0`nonline-mode=false`nview-distance=2`nsimulation-distance=2`nspawn-protection=0`nmax-players=8`nlevel-type=minecraft:flat`ngenerator-settings={`"layers`": [{`"block`":`"minecraft:bedrock`",`"height`":1}],`"biome`":`"minecraft:plains`"}`ndifficulty=peaceful`n")
        [IO.File]::WriteAllText((Join-Path $server 'plugins/SimpleKillCommand/config.yml'), "command:`n  name: kill`n  aliases: [suicide, selfkill]`n  override-existing: true`n  permission: ''`n")
        $major = $matrix[$mc].java
        $javaHome = [Environment]::GetEnvironmentVariable("JAVA${major}_HOME")
        if (!$javaHome) { $javaHome = (Get-ChildItem 'C:/Program Files/Java' -Directory | Where-Object Name -like "jdk-$major.*" | Sort-Object Name -Descending | Select-Object -First 1).FullName }
        $java = Join-Path $javaHome 'bin/java.exe'
        Write-Host "SERVER $platform $mc build $($build.id)"
        & node (Join-Path $PSScriptRoot 'client-smoke.cjs') $server $java $mc '-Xms256M' '-Xmx1536M' '-jar' $launcher 'nogui' *> (Join-Path $work "$platform-$mc-server.log")
        if ($LASTEXITCODE -ne 0) { throw "Client tests failed: $platform $mc. See build/verification/$platform-$mc-server.log" }
        $client = Get-Content (Join-Path $server 'client-result.json') -Raw | ConvertFrom-Json
        $shutdown = Get-Content (Join-Path $server 'server-stop-result.json') -Raw | ConvertFrom-Json
        $log = Get-Content (Join-Path $work "$platform-$mc-server.log") -Raw
        if ($log -notmatch "Enabling SimpleKillCommand v$([regex]::Escape($version))" -or $log -match 'Failed to (enable|unregister)|Could not (load|register)') { throw "Plugin lifecycle failed: $platform $mc" }
        @{minecraft=$mc; platform=$platform; build=$build.id; channel=$build.channel; sha256=(Get-FileHash $jar -Algorithm SHA256).Hash.ToLowerInvariant();
            clientChecks=$client.checks; clientSkipped=$client.skipped; consoleChecks=$client.consoleChecks;
            gracefulShutdown=$shutdown.graceful;
            verifiedAt=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $server 'verified.json') -Encoding utf8
        Write-Host "PASS $platform $mc ($($client.checks.Count) client checks)"
    }
}
