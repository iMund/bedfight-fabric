package br.com.tavares.bedfight.match;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** No block placement, no PvP, while a player's match is in the pre-start countdown. Movement itself is frozen via an attribute modifier applied/removed in MatchManager. */
public final class MatchFreeze {
	private MatchFreeze() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register(MatchFreeze::onUseBlock);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(MatchFreeze::onAllowDamage);
	}

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hitResult) {
		if (player instanceof ServerPlayer serverPlayer && MatchManager.isFrozen(serverPlayer.getUUID())) {
			return InteractionResult.FAIL;
		}
		return InteractionResult.PASS;
	}

	private static boolean onAllowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (!(source.getEntity() instanceof Player)) {
			return true;
		}
		if (victim instanceof ServerPlayer serverPlayer && MatchManager.isFrozen(serverPlayer.getUUID())) {
			return false;
		}
		return true;
	}
}
