package com.simplekillcommand.minecraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.simplekillcommand.common.SimpleKillCommandSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class KillCommands {
    private static final Logger LOGGER = Logger.getLogger(SimpleKillCommandSettings.MOD_ID);

    private KillCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, SimpleKillCommandSettings settings) {
        for (String label : settings.labels()) {
            CommandNode<CommandSourceStack> existing = dispatcher.getRoot().getChild(label);
            if (existing != null) {
                if (!settings.overrideExisting()) {
                    LOGGER.warning("Command label '/" + label + "' is already registered.");
                    continue;
                }
                // Brigadier merges duplicate nodes, retaining their original permission predicate and arguments.
                dispatcher.getRoot().getChildren().remove(existing);
            }
            dispatcher.register(Commands.literal(label)
                    .requires(source -> PermissionChecks.hasPermission(source, settings.permissionLevel()))
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
            PlayerKill.kill(player, source);
            return 1;
        } catch (RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Failed to execute self-kill command.", exception);
            source.sendFailure(message(settings.internalErrorMessage()));
            return 0;
        }
    }

    public static Component message(String value) {
        MutableComponent result = Component.empty();
        Style style = Style.EMPTY;
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            ChatFormatting format = index + 1 < value.length() && (character == '&' || character == '\u00a7')
                    ? ChatFormatting.getByCode(value.charAt(index + 1)) : null;
            if (format == null) {
                text.append(character);
                continue;
            }
            if (!text.isEmpty()) {
                result.append(Component.literal(text.toString()).setStyle(style));
                text.setLength(0);
            }
            boolean color = "0123456789abcdef".indexOf(Character.toLowerCase(value.charAt(index + 1))) >= 0;
            style = format == ChatFormatting.RESET ? Style.EMPTY : (color ? Style.EMPTY : style).applyFormat(format);
            index++;
        }
        if (!text.isEmpty()) { result.append(Component.literal(text.toString()).setStyle(style)); }
        return result;
    }
}
