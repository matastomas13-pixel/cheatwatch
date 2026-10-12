package cz.cheatwatch.mixin;

import cz.cheatwatch.Detector;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    // Relativní pohyb/rotace entit (hlavní pohybový paket serveru)
    @Inject(method = "onEntity", at = @At("HEAD"))
    private void cheatwatch$onEntity(EntityS2CPacket packet, CallbackInfo ci) {
        Detector.onMovePacket(packet);
    }
}
