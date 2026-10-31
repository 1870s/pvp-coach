package com.pvpcoach.dev;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;

/** Version specific calls used only by the self test (26.2+ variant). */
final class DevCompat {
    private DevCompat() {}

    static EntityType<?> husk() {
        return EntityTypes.HUSK;
    }

    static RenderTarget mainRenderTarget(Minecraft mc) {
        return mc.gameRenderer.mainRenderTarget();
    }
}
