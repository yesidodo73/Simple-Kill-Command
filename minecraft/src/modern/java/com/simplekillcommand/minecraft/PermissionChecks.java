package com.simplekillcommand.minecraft;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

public final class PermissionChecks {
    private PermissionChecks() {
    }

    public static boolean hasPermission(CommandSourceStack source, int level) {
        return source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(level)));
    }
}
