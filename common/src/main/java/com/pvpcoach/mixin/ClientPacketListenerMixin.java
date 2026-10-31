package com.pvpcoach.mixin;

import com.pvpcoach.fight.FightTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reads the server's damage and entity events. Both handlers first hop from the network thread to the main
 * thread by throwing, so RETURN is only reached on the main thread.
 */
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {
    @Inject(method = "handleDamageEvent", at = @At("RETURN"))
    private void pvpcoach$onDamageEvent(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        FightTracker.onDamageEvent(packet.entityId(), packet.sourceCauseId(), packet.sourceDirectId());
    }

    @Inject(method = "handleEntityEvent", at = @At("RETURN"))
    private void pvpcoach$onEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) FightTracker.onEntityEvent(packet.getEntity(mc.level), packet.getEventId());
    }
}
