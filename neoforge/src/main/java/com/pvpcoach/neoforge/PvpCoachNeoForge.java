package com.pvpcoach.neoforge;

import com.pvpcoach.PvpCoach;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** NeoForge entry point: wires NeoForge events into {@link PvpCoach}. */
@Mod(value = PvpCoach.MOD_ID, dist = Dist.CLIENT)
public class PvpCoachNeoForge {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(PvpCoach.KEY_CATEGORY_ID);

    public PvpCoachNeoForge(IEventBus modBus) {
        PvpCoach.init(() -> FMLPaths.CONFIGDIR.get(), CATEGORY);

        modBus.addListener(FMLClientSetupEvent.class, event -> PvpCoach.onClientSetup());
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.registerCategory(CATEGORY);
            event.register(PvpCoach.OPEN_COACH);
        });
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> PvpCoach.onClientTick());
    }
}
