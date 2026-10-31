package com.pvpcoach.mixin;

import com.pvpcoach.fight.FightTracker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sees the local player's attacks and item uses before they are sent to the server. */
@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeMixin {
    @Inject(method = "attack", at = @At("HEAD"))
    private void pvpcoach$onAttack(Player player, Entity target, CallbackInfo ci) {
        FightTracker.onAttack(player, target);
    }

    // No target arguments: piercingAttack gained a SwingAnimation parameter in 26.3, and this handler fits both.
    @Inject(method = "piercingAttack", at = @At("HEAD"))
    private void pvpcoach$onPiercingAttack(CallbackInfo ci) {
        FightTracker.onPiercingAttack();
    }

    @Inject(method = "useItem", at = @At("HEAD"))
    private void pvpcoach$onUseItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        FightTracker.onUseItem(player, hand);
    }
}
