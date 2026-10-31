package com.pvpcoach.mixin;

import com.pvpcoach.fight.FightTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Counts left clicks that swing at nothing (missed hits). */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Shadow
    public HitResult hitResult;

    @Shadow
    protected int missTime;

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void pvpcoach$onStartAttack(CallbackInfoReturnable<Boolean> cir) {
        // While missTime runs the click is ignored by vanilla, so it is no swing either.
        if (missTime <= 0 && hitResult != null && hitResult.getType() == HitResult.Type.MISS) FightTracker.onMissSwing();
    }
}
