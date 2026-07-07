package com.simplekillcommand.forge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.simplekillcommand.common.SimpleKillCommandSettings;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

@Mod(SimpleKillCommandSettings.MOD_ID)
public final class SimpleKillCommandForgeMod {
    private static final Logger LOGGER = Logger.getLogger(SimpleKillCommandSettings.MOD_ID);

    private final SimpleKillCommandSettings settings;

    public SimpleKillCommandForgeMod() {
        settings = loadSettings();
        registerCommandListener();
    }

    private SimpleKillCommandSettings loadSettings() {
        Path configPath = Paths.get("config", "simplekillcommand.properties");
        try {
            return SimpleKillCommandSettings.loadProperties(configPath);
        } catch (IOException | RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Failed to load Simple Kill Command config.", exception);
            return SimpleKillCommandSettings.defaults();
        }
    }

    private void registerCommandListener() {
        Consumer<RegisterCommandsEvent> listener = this::registerCommands;
        RegisterCommandsEvent.BUS.addListener(listener);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerCommands(Object event) {
        try {
            Object dispatcherObject = event.getClass().getMethod("getDispatcher").invoke(event);
            CommandDispatcher dispatcher = (CommandDispatcher) dispatcherObject;

            for (String label : settings.labels()) {
                if (dispatcher.getRoot().getChild(label) != null && !settings.overrideExisting()) {
                    LOGGER.warning("Command label '/" + label + "' is already registered.");
                    continue;
                }

                dispatcher.register(LiteralArgumentBuilder.literal(label)
                        .requires(source -> hasPermission(source, settings.permissionLevel()))
                        .executes(context -> execute(context.getSource())));
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not register Forge command.", exception);
        }
    }

    private boolean hasPermission(Object source, int permissionLevel) {
        if (permissionLevel <= 0) {
            return true;
        }

        try {
            Object permissions = source.getClass().getMethod("permissions").invoke(source);
            Class<?> permissionLevelClass = Class.forName("net.minecraft.server.permissions.PermissionLevel");
            Class<?> permissionClass = Class.forName("net.minecraft.server.permissions.Permission");
            Class<?> hasCommandLevelClass = Class.forName("net.minecraft.server.permissions.Permission$HasCommandLevel");
            Object level = permissionLevelClass.getMethod("byId", int.class).invoke(null, permissionLevel);
            Constructor<?> constructor = hasCommandLevelClass.getConstructor(permissionLevelClass);
            Object permission = constructor.newInstance(level);
            return (Boolean) permissions.getClass().getMethod("hasPermission", permissionClass).invoke(permissions, permission);
        } catch (ReflectiveOperationException exception) {
            LOGGER.log(Level.WARNING, "Could not check Forge command permission level.", exception);
            return false;
        }
    }

    private int execute(Object source) {
        Object player;
        try {
            player = source.getClass().getMethod("getPlayerOrException").invoke(source);
        } catch (ReflectiveOperationException exception) {
            sendFailure(source, settings.playerOnlyMessage());
            return 0;
        }

        try {
            boolean alive = (Boolean) player.getClass().getMethod("isAlive").invoke(player);
            if (!alive) {
                sendSystemMessage(player, settings.alreadyDeadMessage());
                return 1;
            }

            player.getClass().getMethod("setHealth", float.class).invoke(player, 0.0F);
            return 1;
        } catch (ReflectiveOperationException exception) {
            LOGGER.log(Level.SEVERE, "Failed to execute kill command.", exception);
            sendSystemMessage(player, settings.internalErrorMessage());
            return 0;
        }
    }

    private void sendFailure(Object source, String message) {
        try {
            source.getClass().getMethod("sendFailure", componentClass()).invoke(source, component(message));
        } catch (ReflectiveOperationException exception) {
            LOGGER.log(Level.WARNING, "Could not send Forge command failure message.", exception);
        }
    }

    private void sendSystemMessage(Object player, String message) {
        try {
            player.getClass().getMethod("sendSystemMessage", componentClass()).invoke(player, component(message));
        } catch (ReflectiveOperationException exception) {
            LOGGER.log(Level.WARNING, "Could not send Forge command message.", exception);
        }
    }

    private Object component(String value) throws ReflectiveOperationException {
        Class<?> componentClass = componentClass();
        return componentClass.getMethod("literal", String.class).invoke(null, SimpleKillCommandSettings.colorize(value));
    }

    private Class<?> componentClass() throws ClassNotFoundException {
        return Class.forName("net.minecraft.network.chat.Component");
    }
}
