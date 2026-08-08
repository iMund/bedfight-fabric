package br.com.tavares.bedfight.arena;

import br.com.tavares.bedfight.BedFightPermissions;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Inside the arena dimension, only a match's bed/wood/end-stone shell and player-placed blocks are breakable. */
public final class ArenaBlockProtection {
	private ArenaBlockProtection() {
	}

	public static void register() {
		PlayerBlockBreakEvents.BEFORE.register(ArenaBlockProtection::onBeforeBreak);
	}

	private static boolean onBeforeBreak(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
		if (!(level instanceof ServerLevel serverLevel) || serverLevel.dimension() != ArenaDimension.KEY) {
			return true;
		}
		if (player.isCreative() || (player instanceof ServerPlayer serverPlayer && BedFightPermissions.hasAdminPermission(serverPlayer.permissions()))) {
			return true;
		}
		return ArenaInstancePool.findByPosition(pos)
			.map(instance -> instance.isInUse() && instance.isBreakable(pos))
			.orElse(false);
	}
}
