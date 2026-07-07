package com.simplekillcommand.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.simplekillcommand.common.SimpleKillCommandSettings;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class SimpleKillCommandFabricCommands {
    private static final Logger LOGGER = Logger.getLogger(SimpleKillCommandSettings.MOD_ID);

    private SimpleKillCommandFabricCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        SimpleKillCommandSettings settings = SimpleKillCommandFabricMod.settings();
        for (String label : settings.labels()) {
            if (dispatcher.getRoot().getChild(label) != null && !settings.overrideExisting()) {
                LOGGER.warning("Command label '/" + label + "' is already registered.");
                continue;
            }

            dispatcher.register(Commands.literal(label)
                    .requires(source -> source.permissions().hasPermission(
                            new Permission.HasCommandLevel(PermissionLevel.byId(settings.permissionLevel()))
                    ))
                    .executes(context -> execute(context.getSource(), settings)));
        }
    }

    private static int execute(CommandSourceStack source, SimpleKillCommandSettings settings) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException exception) {
            source.sendFailure(message(settings.playerOnlyMessage()));
            return 0;
        }

        try {
            if (!player.isAlive()) {
                player.sendSystemMessage(message(settings.alreadyDeadMessage()));
                return 1;
            }

            player.setHealth(0.0F);
            return 1;
        } catch (RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Failed to execute kill command for " + player.getName().getString() + ".", exception);
            player.sendSystemMessage(message(settings.internalErrorMessage()));
            return 0;
        }
    }

    private static Component message(String value) {
        return Component.literal(SimpleKillCommandSettings.colorize(value));
    }
}
