# Simple Kill Command

Configurable self-kill command for Minecraft servers.

By default, players can run `/kill` without OP permission. The command only kills the player who runs it; it does not accept targets.

## Build

Install JDK 21 and JDK 25, and run Gradle with JDK 25.

```shell
./gradlew build
```

This builds Bukkit and the default Minecraft `1.21.11` mod variants. For another Minecraft version:

```shell
./gradlew "-Pplatform=fabric" "-PminecraftVersion=26.3" :fabric:build
```

Replace `fabric` with `forge` or `neoforge` for that loader. On Windows, use `gradlew.bat`.

Omit `-Pplatform` to build all available platforms for the selected Minecraft version. Forge is skipped for `1.21.2`.

Jars are written to `bukkit/build/libs/` and `<loader>/build/<MC>/libs/`.

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

### Fabric, Forge and NeoForge

`config/simplekillcommand.properties`

```properties
command.name=kill
command.aliases=
command.override-existing=true
command.permission-level=0
```

`command.aliases` is comma-separated: `command.aliases=suicide,selfkill`.

`command.permission-level` is the Minecraft command permission level, clamped to `0` through `4`. Leave it at `0` to allow every player to use the command.

Messages can be changed in the generated UTF-8 config. Color and formatting codes such as `&c` and `&l` are supported. Restart the server after editing the config. Invalid mod configuration prevents startup instead of silently reverting permission settings.

## License

MIT. See [LICENSE](LICENSE).
