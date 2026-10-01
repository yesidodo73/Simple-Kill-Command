package com.simplekillcommand.test.neoforge;

import com.simplekillcommand.test.ServerSmokeTest;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@Mod(value = "simplekillcommand_test", dist = Dist.DEDICATED_SERVER)
public final class ServerTestMod {
    public ServerTestMod() {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> ServerSmokeTest.run(event.getServer()));
    }
}
