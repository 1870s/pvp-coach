package com.pvpcoach.fabric;

import com.pvpcoach.PvpCoach;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;

/** Fabric entry point: wires Fabric API callbacks into {@link PvpCoach}. */
public class PvpCoachFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(PvpCoach.KEY_CATEGORY_ID);
        PvpCoach.init(() -> FabricLoader.getInstance().getConfigDir(), category);
        KeyMappingHelper.registerKeyMapping(PvpCoach.OPEN_COACH);
        PvpCoach.onClientSetup();
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> PvpCoach.onClientTick());
    }
}
