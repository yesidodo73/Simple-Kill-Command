package com.simplekillcommand.common;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SimpleKillCommandSettingsTest {

    @Test
    void normalizesCommandLabelsAndAliases() {
        SimpleKillCommandSettings settings = SimpleKillCommandSettings.builder()
                .commandName("/Suicide")
                .aliases(Arrays.asList("KILL", "suicide", "self_kill"))
                .build();

        assertEquals("suicide", settings.commandName());
        assertEquals(Arrays.asList("kill", "self_kill"), settings.aliases());
    }

    @Test
    void rejectsInvalidCommandLabels() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SimpleKillCommandSettings.builder().commandName("bad command").build()
        );

        assertTrue(exception.getMessage().contains("command.name"));
    }

    @Test
    void clampsPermissionLevelToMinecraftRange() {
        assertEquals(0, SimpleKillCommandSettings.builder().permissionLevel(-10).build().permissionLevel());
        assertEquals(4, SimpleKillCommandSettings.builder().permissionLevel(99).build().permissionLevel());
    }
}
