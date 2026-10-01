package com.simplekillcommand.fabric.mixin;

import com.simplekillcommand.fabric.SimpleKillCommandFabricMod;
import com.simplekillcommand.minecraft.KillCommands;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Commands.class)
public abstract class CommandsMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void simplekillcommand$register(Commands.CommandSelection commandSelection, CommandBuildContext context, CallbackInfo info) {
        KillCommands.register(((Commands) (Object) this).getDispatcher(), SimpleKillCommandFabricMod.settings());
    }
}
