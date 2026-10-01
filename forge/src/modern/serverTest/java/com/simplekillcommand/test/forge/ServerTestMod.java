package com.simplekillcommand.test.forge;

import com.simplekillcommand.test.ServerSmokeTest;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod("simplekillcommand_test")
public final class ServerTestMod {
    public ServerTestMod() {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER) { return; }
        ServerStartedEvent.BUS.addListener(event -> ServerSmokeTest.run(event.getServer()));
    }
}
