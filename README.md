# Simple Kill Command

Configurable self-kill command for Minecraft servers.

By default, players can run `/kill` without OP permission. The command only kills the player who runs it; it does not accept targets.

## Artifacts

| Platform | Jar | Output | Target |
| --- | --- | --- | --- |
| Bukkit-compatible | `SimpleKillCommand-Bukkit` | `bukkit/build/libs/` | Bukkit, CraftBukkit, Spigot, Paper, Purpur, Pufferfish, Leaf/Leaves, Folia |
| Fabric | `SimpleKillCommand-Fabric` | `fabric/build/libs/` | Fabric Loader `0.19.3+`, Minecraft `1.21.11` |
| Forge | `SimpleKillCommand-Forge` | `forge/build/libs/` | Forge `61+`, Minecraft `1.21.11` |

The Bukkit-compatible jar is intended for Minecraft `1.8.9` through `26.2` on servers that run Java 17 or newer. Fabric and Forge builds are version-specific and should be rebuilt for each additional Minecraft version.

Forge `61` requires Java 21. Bukkit-compatible and Fabric artifacts target Java 17 bytecode.

Fabric and Forge artifacts are server-side only. Players do not need to install them on their clients.

## Build

Building all modules requires JDK 21.

```shell
./gradlew build
```

On Windows:

```powershell
.\gradlew.bat build
```

## Configuration

The default command name is `kill`. Set `command.name` to use another command instead.

`command.override-existing` controls whether an existing server command with the same label should be replaced. This is enabled by default because Minecraft already has a `/kill` command.

### Bukkit

`plugins/SimpleKillCommand/config.yml`

```yaml
command:
  name: kill
  # YAML list. Example: [suicide, selfkill]
  aliases: []
  override-existing: true
  permission: ""
```

`command.permission` is a Bukkit permission node. Leave it empty to allow every player to use the command.

### Fabric and Forge

`config/simplekillcommand.properties`

```properties
command.name=kill
command.aliases=
command.override-existing=true
command.permission=
command.permission-level=0
```

`command.aliases` is comma-separated in the Fabric and Forge properties file. Example: `command.aliases=suicide,selfkill`.

`command.permission-level` is the Minecraft command permission level, clamped to `0` through `4`. Leave it at `0` to allow every player to use the command.

`command.permission` is not used by the Fabric and Forge builds.

Example:

```properties
command.name=suicide
command.aliases=kill
```

Messages can also be changed in the generated config.

## License

MIT. See [LICENSE](LICENSE).
