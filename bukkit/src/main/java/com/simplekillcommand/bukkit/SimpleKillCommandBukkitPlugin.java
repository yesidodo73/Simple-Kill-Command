package com.simplekillcommand.bukkit;

import com.simplekillcommand.common.SimpleKillCommandSettings;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.function.Consumer;

public final class SimpleKillCommandBukkitPlugin extends JavaPlugin {

    private CommandMap commandMap;
    private SimpleKillCommandSettings settings;
    private KillCommand command;
    private final Map<String, Command> replacedCommands = new LinkedHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        try {
            settings = loadSettings(getConfig());
            commandMap = resolveCommandMap();
            registerCommand();
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Failed to enable SimpleKillCommand.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("Registered /" + settings.commandName() + " command.");
    }

    @Override
    public void onDisable() {
        unregisterCommand();
    }

    private SimpleKillCommandSettings loadSettings(FileConfiguration file) {
        return SimpleKillCommandSettings.builder()
                .commandName(file.getString("command.name", "kill"))
                .aliases(file.getStringList("command.aliases"))
                .overrideExisting(file.getBoolean("command.override-existing", true))
                .permission(file.getString("command.permission", ""))
                .permissionLevel(file.getInt("command.permission-level", 0))
                .playerOnlyMessage(file.getString("messages.player-only", "&cThis command can only be used by players."))
                .alreadyDeadMessage(file.getString("messages.already-dead", "&4You are already dead!"))
                .noPermissionMessage(file.getString("messages.no-permission", "&cYou do not have permission to use this command."))
                .internalErrorMessage(file.getString(
                        "messages.internal-error",
                        "&cThe command could not be completed. Please contact an administrator."
                ))
                .build();
    }

    private void registerCommand() {
        unregisterCommand();

        Map<String, Command> knownCommands = getKnownCommands(commandMap);
        Set<String> labels = settings.labels();

        for (String label : labels) {
            Command existing = knownCommands.get(label);
            if (existing != null && !settings.overrideExisting()) {
                throw new IllegalStateException("Command label '/" + label + "' is already registered.");
            }
        }

        if (settings.overrideExisting()) {
            for (String label : labels) {
                Command removed = knownCommands.remove(label);
                if (removed != null && removed != command) {
                    replacedCommands.put(label, removed);
                }
            }
        }

        command = new KillCommand(this, settings);
        boolean registered = commandMap.register(getName().toLowerCase(Locale.ROOT), command);
        if (!registered) {
            unregisterCommand();
            throw new IllegalStateException("Could not register '/" + settings.commandName() + "'.");
        }
    }

    private void unregisterCommand() {
        if (command == null || commandMap == null) {
            return;
        }

        try {
            Map<String, Command> knownCommands = getKnownCommands(commandMap);
            for (String label : new LinkedHashSet<>(knownCommands.keySet())) {
                if (knownCommands.get(label) == command) {
                    knownCommands.remove(label);
                }
            }
            command.unregister(commandMap);
            restoreReplacedCommands(knownCommands);
        } catch (RuntimeException exception) {
            getLogger().log(Level.WARNING, "Failed to unregister command cleanly.", exception);
        } finally {
            command = null;
        }
    }

    private void restoreReplacedCommands(Map<String, Command> knownCommands) {
        for (Map.Entry<String, Command> entry : replacedCommands.entrySet()) {
            Command current = knownCommands.get(entry.getKey());
            if (current == null || current == command) {
                knownCommands.put(entry.getKey(), entry.getValue());
            }
        }
        replacedCommands.clear();
    }

    private void kill(Player player) {
        Runnable action = () -> {
            try {
                if (player.isDead()) {
                    player.sendMessage(color(settings.alreadyDeadMessage()));
                    return;
                }
                player.setHealth(0.0D);
            } catch (RuntimeException exception) {
                getLogger().log(Level.SEVERE, "Failed to execute self-kill command.", exception);
                player.sendMessage(color(settings.internalErrorMessage()));
            }
        };

        try {
            if (scheduleOnEntityThread(player, action)) {
                return;
            }
            action.run();
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Failed to execute kill command for " + player.getName() + ".", exception);
            player.sendMessage(color(settings.internalErrorMessage()));
        }
    }

    private boolean scheduleOnEntityThread(Player player, Runnable action) {
        Method getSchedulerMethod;
        try {
            getSchedulerMethod = player.getClass().getMethod("getScheduler");
        } catch (NoSuchMethodException ignored) {
            return false;
        }

        try {
            Object scheduler = getSchedulerMethod.invoke(player);
            Method runMethod = scheduler.getClass().getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            Consumer<Object> task = ignored -> action.run();
            runMethod.invoke(scheduler, this, task, null);
            return true;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Entity scheduler is available but could not be used.", exception);
        }
    }

    private CommandMap resolveCommandMap() {
        try {
            Method method = Bukkit.getServer().getClass().getMethod("getCommandMap");
            Object value = method.invoke(Bukkit.getServer());
            if (!(value instanceof CommandMap)) {
                throw new IllegalStateException("Server command map has an unexpected type.");
            }
            return (CommandMap) value;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not access the server command map.", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Command> getKnownCommands(CommandMap map) {
        try {
            Field field = findField(map.getClass(), "knownCommands");
            field.setAccessible(true);
            return (Map<String, Command>) field.get(map);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not access registered commands.", exception);
        }
    }

    private Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private static final class KillCommand extends Command {
        private final SimpleKillCommandBukkitPlugin plugin;
        private final SimpleKillCommandSettings settings;

        private KillCommand(SimpleKillCommandBukkitPlugin plugin, SimpleKillCommandSettings settings) {
            super(
                    settings.commandName(),
                    "Kills the command sender.",
                    "/" + settings.commandName(),
                    settings.aliases()
            );
            this.plugin = plugin;
            this.settings = settings;

            if (!settings.permission().isEmpty()) {
                setPermission(settings.permission());
                setPermissionMessage(color(settings.noPermissionMessage()));
            }
        }

        @Override
        public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            if (!testPermission(sender)) {
                return true;
            }

            if (!(sender instanceof Player)) {
                sender.sendMessage(color(settings.playerOnlyMessage()));
                return true;
            }

            if (args.length != 0) {
                sender.sendMessage(getUsage());
                return true;
            }

            plugin.kill((Player) sender);
            return true;
        }

        @Override
        public java.util.List<String> tabComplete(CommandSender sender, String alias, String[] args) {
            return Collections.emptyList();
        }
    }
}
