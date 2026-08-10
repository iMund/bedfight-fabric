package br.com.tavares.bedfight.mixin;

import br.com.tavares.bedfight.match.BedFightConnectionHosts;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.server.network.ServerHandshakePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records the literal server address the client typed into its server list (not the raw socket address, which could be a proxy/LAN hop) - the only source for this is the handshake packet itself, vanilla exposes nothing later for it. */
@Mixin(ServerHandshakePacketListenerImpl.class)
public abstract class ServerHandshakeHostCaptureMixin {
	@Shadow
	@Final
	private Connection connection;

	@Inject(method = "beginLogin(Lnet/minecraft/network/protocol/handshake/ClientIntentionPacket;Z)V", at = @At("HEAD"))
	private void bedfight$captureHost(ClientIntentionPacket packet, boolean transfer, CallbackInfo ci) {
		BedFightConnectionHosts.record(this.connection, packet.hostName(), packet.port());
	}
}
