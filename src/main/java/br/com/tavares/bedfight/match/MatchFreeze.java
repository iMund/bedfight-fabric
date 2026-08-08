package br.com.tavares.bedfight.match;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * While a player's match is in the pre-start countdown: no block placement (UseBlockCallback),
 * no in-air item use like buckets/pearls/potions (UseItemCallback), no block breaking, and no
 * damage of any kind (not just PvP - there's no legitimate damage during a frozen pre-start).
 * Movement itself is frozen in MatchManager (attribute modifiers + per-tick position reassert).
 */
public final class MatchFreeze {
	private MatchFreeze() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register(MatchFreeze::onUseBlock);
		UseItemCallback.EVENT.register(MatchFreeze::onUseItem);
		PlayerBlockBreakEvents.BEFORE.register(MatchFreeze::onBeforeBreak);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(MatchFreeze::onAllowDamage);
	}

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hitResult) {
		return isFrozen(player) ? InteractionResult.FAIL : InteractionResult.PASS;
	}

	private static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
		return isFrozen(player) ? InteractionResult.FAIL : InteractionResult.PASS;
	}

	private static boolean onBeforeBreak(Level level, Player player, net.minecraft.core.BlockPos pos, BlockState state, BlockEntity blockEntity) {
		return !isFrozen(player);
	}

	private static boolean onAllowDamage(LivingEntity victim, DamageSource source, float amount) {
		return !(victim instanceof ServerPlayer serverPlayer && MatchManager.isFrozen(serverPlayer.getUUID()));
	}

	private static boolean isFrozen(Player player) {
		return player instanceof ServerPlayer serverPlayer && MatchManager.isFrozen(serverPlayer.getUUID());
	}
}
