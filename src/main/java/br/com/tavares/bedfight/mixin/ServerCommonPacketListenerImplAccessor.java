package br.com.tavares.bedfight.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the private Connection field so the sidebar can look up the hostname the player's client used to connect (captured at handshake, see ServerHandshakeHostCaptureMixin). */
@Mixin(ServerCommonPacketListenerImpl.class)
public interface ServerCommonPacketListenerImplAccessor {
	@Accessor("connection")
	Connection bedfight$getConnection();
}
