package com.simplekillcommand.test.fabric;

import com.simplekillcommand.test.ServerSmokeTest;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public abstract class ServerTestMixin {
    private boolean simplekillcommand$tested;

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void simplekillcommand$test(BooleanSupplier hasTimeLeft, CallbackInfo info) {
        if (!simplekillcommand$tested) {
            simplekillcommand$tested = true;
            ServerSmokeTest.run((MinecraftServer) (Object) this);
        }
    }
}
