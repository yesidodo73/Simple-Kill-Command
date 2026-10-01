package com.simplekillcommand.forge;

import com.simplekillcommand.common.SimpleKillCommandSettings;
import com.simplekillcommand.minecraft.KillCommands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.io.IOException;
import java.nio.file.Path;

@Mod(SimpleKillCommandSettings.MOD_ID)
public final class SimpleKillCommandForgeMod {
    public SimpleKillCommandForgeMod() {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER) { return; }
        SimpleKillCommandSettings settings;
        try {
            settings = SimpleKillCommandSettings.loadProperties(Path.of("config", "simplekillcommand.properties"));
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Could not load Simple Kill Command configuration.", exception);
        }
        CommandEvents.register(event -> KillCommands.register(event.getDispatcher(), settings));
    }
}
