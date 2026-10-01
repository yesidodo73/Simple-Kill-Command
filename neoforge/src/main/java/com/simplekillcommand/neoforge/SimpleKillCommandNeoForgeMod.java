package com.simplekillcommand.neoforge;

import com.simplekillcommand.common.SimpleKillCommandSettings;
import com.simplekillcommand.minecraft.KillCommands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.nio.file.Path;

@Mod(value = SimpleKillCommandSettings.MOD_ID, dist = Dist.DEDICATED_SERVER)
public final class SimpleKillCommandNeoForgeMod {
    public SimpleKillCommandNeoForgeMod() {
        SimpleKillCommandSettings settings;
        try {
            settings = SimpleKillCommandSettings.loadProperties(Path.of("config", "simplekillcommand.properties"));
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Could not load Simple Kill Command configuration.", exception);
        }
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> KillCommands.register(event.getDispatcher(), settings));
    }
}
