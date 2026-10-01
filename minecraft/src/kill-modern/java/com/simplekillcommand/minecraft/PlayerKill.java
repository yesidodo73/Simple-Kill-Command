package com.simplekillcommand.minecraft;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

final class PlayerKill {
    private PlayerKill() {
    }
    static void kill(ServerPlayer player, CommandSourceStack source) {
        player.kill(source.getLevel());
    }
}
