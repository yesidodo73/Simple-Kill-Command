package com.simplekillcommand.fabric;

import com.simplekillcommand.common.SimpleKillCommandSettings;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SimpleKillCommandFabricMod implements ModInitializer {
    private static final Logger LOGGER = Logger.getLogger(SimpleKillCommandSettings.MOD_ID);

    private static volatile SimpleKillCommandSettings settings = SimpleKillCommandSettings.defaults();

    @Override
    public void onInitialize() {
        settings = loadSettings();
    }

    public static SimpleKillCommandSettings settings() {
        return settings;
    }

    private SimpleKillCommandSettings loadSettings() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("simplekillcommand.properties");
        try {
            return SimpleKillCommandSettings.loadProperties(configPath);
        } catch (IOException | RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Failed to load Simple Kill Command config.", exception);
            return SimpleKillCommandSettings.defaults();
        }
    }
}
