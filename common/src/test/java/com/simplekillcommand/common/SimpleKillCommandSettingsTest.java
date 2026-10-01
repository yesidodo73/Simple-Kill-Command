package com.simplekillcommand.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SimpleKillCommandSettingsTest {

    @TempDir
    private Path tempDir;

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

    @Test
    void loadsCommaSeparatedAliasesFromProperties() throws Exception {
        Path config = tempDir.resolve("simplekillcommand.properties");
        Files.writeString(config, String.join(System.lineSeparator(),
                "command.name=kill",
                "command.aliases=suicide,selfkill",
                ""
        ));

        SimpleKillCommandSettings settings = SimpleKillCommandSettings.loadProperties(config);

        assertEquals(Arrays.asList("suicide", "selfkill"), settings.aliases());
    }

    @Test
    void rejectsMalformedPermissionLevelInsteadOfAllowingEveryone() throws Exception {
        Path config = tempDir.resolve("simplekillcommand.properties");
        Files.writeString(config, "command.permission-level=two");
        assertThrows(IllegalArgumentException.class, () -> SimpleKillCommandSettings.loadProperties(config));
    }

    @Test
    void readsUtf8MessagesWithoutCorruption() throws Exception {
        Path config = tempDir.resolve("simplekillcommand.properties");
        String message = "\uD50C\uB808\uC774\uC5B4\uB9CC \uC0AC\uC6A9\uD560 \uC218 \uC788\uC2B5\uB2C8\uB2E4.";
        Files.writeString(config, "messages.player-only=" + message);
        assertEquals(message, SimpleKillCommandSettings.loadProperties(config).playerOnlyMessage());
    }

    @Test
    void writesAliasSeparatorHintToDefaultProperties() throws Exception {
        Path config = tempDir.resolve("simplekillcommand.properties");

        SimpleKillCommandSettings.loadProperties(config);

        String content = Files.readString(config);
        assertTrue(content.contains("Separate aliases with commas"));
        assertTrue(content.contains("command.aliases="));
    }
}
