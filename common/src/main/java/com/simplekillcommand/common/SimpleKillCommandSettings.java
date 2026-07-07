package com.simplekillcommand.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

public final class SimpleKillCommandSettings {
    public static final String MOD_ID = "simplekillcommand";
    public static final String DISPLAY_NAME = "Simple Kill Command";

    private static final String DEFAULT_COMMAND_NAME = "kill";
    private static final String DEFAULT_PLAYER_ONLY_MESSAGE = "&cThis command can only be used by players.";
    private static final String DEFAULT_ALREADY_DEAD_MESSAGE = "&4You are already dead!";
    private static final String DEFAULT_NO_PERMISSION_MESSAGE = "&cYou do not have permission to use this command.";
    private static final String DEFAULT_INTERNAL_ERROR_MESSAGE =
            "&cThe command could not be completed. Please contact an administrator.";

    private final String commandName;
    private final List<String> aliases;
    private final boolean overrideExisting;
    private final String permission;
    private final int permissionLevel;
    private final String playerOnlyMessage;
    private final String alreadyDeadMessage;
    private final String noPermissionMessage;
    private final String internalErrorMessage;

    private SimpleKillCommandSettings(Builder builder) {
        commandName = normalizeLabel(builder.commandName, "command.name");

        List<String> normalizedAliases = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        seen.add(commandName);
        for (String alias : builder.aliases) {
            String normalized = normalizeLabel(alias, "command.aliases");
            if (seen.add(normalized)) {
                normalizedAliases.add(normalized);
            }
        }

        aliases = Collections.unmodifiableList(normalizedAliases);
        overrideExisting = builder.overrideExisting;
        permission = trim(builder.permission);
        permissionLevel = clamp(builder.permissionLevel, 0, 4);
        playerOnlyMessage = fallback(builder.playerOnlyMessage, DEFAULT_PLAYER_ONLY_MESSAGE);
        alreadyDeadMessage = fallback(builder.alreadyDeadMessage, DEFAULT_ALREADY_DEAD_MESSAGE);
        noPermissionMessage = fallback(builder.noPermissionMessage, DEFAULT_NO_PERMISSION_MESSAGE);
        internalErrorMessage = fallback(builder.internalErrorMessage, DEFAULT_INTERNAL_ERROR_MESSAGE);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static SimpleKillCommandSettings defaults() {
        return builder().build();
    }

    public static SimpleKillCommandSettings loadProperties(Path path) throws IOException {
        Properties properties = new Properties();

        if (Files.exists(path)) {
            try (InputStream input = Files.newInputStream(path)) {
                properties.load(input);
            }
        } else {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            writeDefaultProperties(path);
        }

        return builder()
                .commandName(properties.getProperty("command.name", DEFAULT_COMMAND_NAME))
                .aliases(parseAliases(properties.getProperty("command.aliases", "")))
                .overrideExisting(Boolean.parseBoolean(properties.getProperty("command.override-existing", "true")))
                .permission(properties.getProperty("command.permission", ""))
                .permissionLevel(parseInt(properties.getProperty("command.permission-level", "0"), 0))
                .playerOnlyMessage(properties.getProperty("messages.player-only", DEFAULT_PLAYER_ONLY_MESSAGE))
                .alreadyDeadMessage(properties.getProperty("messages.already-dead", DEFAULT_ALREADY_DEAD_MESSAGE))
                .noPermissionMessage(properties.getProperty("messages.no-permission", DEFAULT_NO_PERMISSION_MESSAGE))
                .internalErrorMessage(properties.getProperty("messages.internal-error", DEFAULT_INTERNAL_ERROR_MESSAGE))
                .build();
    }

    public static void writeDefaultProperties(Path path) throws IOException {
        String content = String.join(System.lineSeparator(),
                "# Simple Kill Command configuration",
                "command.name=" + DEFAULT_COMMAND_NAME,
                "# Separate aliases with commas. Example: command.aliases=suicide,selfkill",
                "command.aliases=",
                "command.override-existing=true",
                "command.permission=",
                "command.permission-level=0",
                "messages.player-only=" + DEFAULT_PLAYER_ONLY_MESSAGE,
                "messages.already-dead=" + DEFAULT_ALREADY_DEAD_MESSAGE,
                "messages.no-permission=" + DEFAULT_NO_PERMISSION_MESSAGE,
                "messages.internal-error=" + DEFAULT_INTERNAL_ERROR_MESSAGE,
                ""
        );

        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    public String commandName() {
        return commandName;
    }

    public List<String> aliases() {
        return aliases;
    }

    public boolean overrideExisting() {
        return overrideExisting;
    }

    public String permission() {
        return permission;
    }

    public int permissionLevel() {
        return permissionLevel;
    }

    public String playerOnlyMessage() {
        return playerOnlyMessage;
    }

    public String alreadyDeadMessage() {
        return alreadyDeadMessage;
    }

    public String noPermissionMessage() {
        return noPermissionMessage;
    }

    public String internalErrorMessage() {
        return internalErrorMessage;
    }

    public Set<String> labels() {
        Set<String> labels = new LinkedHashSet<>();
        labels.add(commandName);
        labels.addAll(aliases);
        return labels;
    }

    public static String colorize(String message) {
        char[] characters = fallback(message, "").toCharArray();
        for (int index = 0; index < characters.length - 1; index++) {
            if (characters[index] == '&' && isColorCode(characters[index + 1])) {
                characters[index] = '\u00A7';
                characters[index + 1] = Character.toLowerCase(characters[index + 1]);
            }
        }
        return new String(characters);
    }

    public static String normalizeLabel(String value, String path) {
        String label = trim(value);
        if (label.startsWith("/")) {
            label = label.substring(1);
        }
        label = label.toLowerCase(Locale.ROOT);

        if (!label.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException(path + " must contain only letters, numbers, underscores, or hyphens.");
        }
        return label;
    }

    private static List<String> parseAliases(String value) {
        List<String> aliases = new ArrayList<>();
        for (String alias : fallback(value, "").split(",")) {
            String trimmed = trim(alias);
            if (!trimmed.isEmpty()) {
                aliases.add(trimmed);
            }
        }
        return aliases;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(trim(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean isColorCode(char value) {
        char code = Character.toLowerCase(value);
        return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') || (code >= 'k' && code <= 'o')
                || code == 'r';
    }

    private static String fallback(String value, String fallback) {
        String trimmed = trim(value);
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Builder {
        private String commandName = DEFAULT_COMMAND_NAME;
        private List<String> aliases = Collections.emptyList();
        private boolean overrideExisting = true;
        private String permission = "";
        private int permissionLevel = 0;
        private String playerOnlyMessage = DEFAULT_PLAYER_ONLY_MESSAGE;
        private String alreadyDeadMessage = DEFAULT_ALREADY_DEAD_MESSAGE;
        private String noPermissionMessage = DEFAULT_NO_PERMISSION_MESSAGE;
        private String internalErrorMessage = DEFAULT_INTERNAL_ERROR_MESSAGE;

        public Builder commandName(String commandName) {
            this.commandName = commandName;
            return this;
        }

        public Builder aliases(List<String> aliases) {
            this.aliases = aliases == null ? Collections.emptyList() : aliases;
            return this;
        }

        public Builder overrideExisting(boolean overrideExisting) {
            this.overrideExisting = overrideExisting;
            return this;
        }

        public Builder permission(String permission) {
            this.permission = permission;
            return this;
        }

        public Builder permissionLevel(int permissionLevel) {
            this.permissionLevel = permissionLevel;
            return this;
        }

        public Builder playerOnlyMessage(String playerOnlyMessage) {
            this.playerOnlyMessage = playerOnlyMessage;
            return this;
        }

        public Builder alreadyDeadMessage(String alreadyDeadMessage) {
            this.alreadyDeadMessage = alreadyDeadMessage;
            return this;
        }

        public Builder noPermissionMessage(String noPermissionMessage) {
            this.noPermissionMessage = noPermissionMessage;
            return this;
        }

        public Builder internalErrorMessage(String internalErrorMessage) {
            this.internalErrorMessage = internalErrorMessage;
            return this;
        }

        public SimpleKillCommandSettings build() {
            return new SimpleKillCommandSettings(this);
        }
    }
}
