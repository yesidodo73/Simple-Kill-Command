package com.simplekillcommand.test;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.simplekillcommand.common.SimpleKillCommandSettings;
import com.simplekillcommand.minecraft.KillCommands;
import com.simplekillcommand.minecraft.PermissionChecks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.GameType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ServerSmokeTest {
    private static final List<String> CHECKS = new ArrayList<>();

    private ServerSmokeTest() {
    }

    public static void run(MinecraftServer server) {
        try {
            TestPlayer player = new TestPlayer(server, "SelfKillTest");
            TestPlayer other = new TestPlayer(server, "OtherPlayer");
            CommandSourceStack source = player.createCommandSourceStack();
            CommandDispatcher<CommandSourceStack> live = server.getCommands().getDispatcher();
            check(!PermissionChecks.hasPermission(source, 2), "non-op-player");
            check(live.getRoot().getChild("kill").canUse(source), "default-permission");
            check(live.getRoot().getChild("kill").getChildren().isEmpty(), "no-target-arguments");
            check(live.execute("kill", server.createCommandSourceStack()) == 0, "console-rejected");
            for (String label : List.of("kill", "suicide", "selfkill")) {
                player = new TestPlayer(server, "Test_" + label);
                source = player.createCommandSourceStack();
                int deaths = player.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS));
                check(live.execute(label, source) == 1 && player.getHealth() == 0.0F, "self-kill-" + label);
                check(player.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS)) == deaths + 1, "death-stat-" + label);
                check(other.getHealth() == 20.0F, "other-player-unaffected-" + label);
            }
            boolean rejected = false;
            try { live.execute("kill OtherPlayer", source); } catch (CommandSyntaxException expected) { rejected = true; }
            check(rejected, "target-rejected");
            player.messages.clear();
            check(live.execute("kill", source) == 1 && player.messages.contains("\uC774\uBBF8 \uC0AC\uB9DD\uD588\uC2B5\uB2C8\uB2E4."), "utf8-already-dead-message");
            TestPlayer creative = new TestPlayer(server, "CreativeTest");
            creative.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            check(live.execute("kill", creative.createCommandSourceStack()) == 1 && creative.getHealth() == 0.0F, "creative-player-killed");
            check(KillCommands.message("&cRed &lBold&r Plain").getString().equals("Red Bold Plain"), "message-formatting");

            CommandDispatcher<CommandSourceStack> isolated = new CommandDispatcher<>();
            isolated.register(Commands.literal("kill").requires(s -> false).then(Commands.literal("targets")));
            KillCommands.register(isolated, SimpleKillCommandSettings.defaults());
            check(isolated.getRoot().getChild("kill").canUse(source), "replaced-permission-predicate");
            check(isolated.getRoot().getChild("kill").getChildren().isEmpty(), "replaced-old-arguments");
            KillCommands.register(isolated, SimpleKillCommandSettings.builder().commandName("leave")
                    .aliases(List.of("goodbye")).permissionLevel(2).build());
            check(!isolated.getRoot().getChild("leave").canUse(source), "permission-level-denied");
            check(isolated.getRoot().getChild("leave").canUse(server.createCommandSourceStack()), "permission-level-allowed");
            check(isolated.getRoot().getChild("goodbye") != null, "custom-name-and-alias");
            var previous = isolated.getRoot().getChild("leave");
            KillCommands.register(isolated, SimpleKillCommandSettings.builder().commandName("leave")
                    .overrideExisting(false).build());
            check(isolated.getRoot().getChild("leave") == previous, "existing-command-preserved");
            check(live.getRoot().getChild("kill").getChildren().isEmpty(), "live-tree-unchanged");

            Path report = Path.of(System.getProperty("simplekillcommand.testReport", "smoke-result.json"));
            Files.writeString(report, "{\"passed\":true,\"checks\":[\"" + String.join("\",\"", CHECKS) + "\"]}");
            System.out.println("SKC_SMOKE_PASS checks=" + CHECKS.size());
        } catch (Throwable failure) {
            System.err.println("SKC_SMOKE_FAIL " + failure);
            failure.printStackTrace();
        } finally {
            if (!Boolean.getBoolean("simplekillcommand.testKeepRunning")) { server.halt(false); }
        }
    }

    private static void check(boolean success, String name) {
        if (!success) { throw new AssertionError(name); }
        CHECKS.add(name);
    }

    private static final class TestPlayer extends ServerPlayer {
        private final List<String> messages = new ArrayList<>();

        private TestPlayer(MinecraftServer server, String name) {
            super(server, server.overworld(), new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), name),
                    ClientInformation.createDefault());
            connection = new ServerGamePacketListenerImpl(server, new net.minecraft.network.Connection(PacketFlow.SERVERBOUND), this,
                    CommonListenerCookie.createInitial(getGameProfile(), false)) {
                public boolean hasClientLoaded() { return true; }
            };
            setHealth(20.0F);
        }

        @Override
        public void sendSystemMessage(Component message) {
            messages.add(message.getString());
        }

        public boolean hasClientLoaded() { return true; }
    }
}
