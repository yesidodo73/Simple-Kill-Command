package com.simplekillcommand.minecraft;

import net.minecraft.commands.CommandSourceStack;

public final class PermissionChecks {
    private PermissionChecks() {
    }

    public static boolean hasPermission(CommandSourceStack source, int level) {
        return source.hasPermission(level);
    }
}
