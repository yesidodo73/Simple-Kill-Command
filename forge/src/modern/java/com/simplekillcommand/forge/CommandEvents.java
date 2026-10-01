package com.simplekillcommand.forge;

import net.minecraftforge.event.RegisterCommandsEvent;
import java.util.function.Consumer;

final class CommandEvents {
    private CommandEvents() {
    }
    static void register(Consumer<RegisterCommandsEvent> listener) {
        RegisterCommandsEvent.BUS.addListener(listener);
    }
}
