package com.pvpcoach.dev;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;

/** Version specific calls used only by the self test (26.1.x variant). */
final class DevCompat {
    private DevCompat() {}

    static EntityType<?> husk() {
        return EntityType.HUSK;
    }

    static RenderTarget mainRenderTarget(Minecraft mc) {
        return mc.getMainRenderTarget();
    }
}
