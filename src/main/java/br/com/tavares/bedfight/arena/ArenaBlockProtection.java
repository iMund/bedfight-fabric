package br.com.tavares.bedfight.arena;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Inside the arena dimension, only a match's pasted bed/wood/end-stone shell is breakable - everything else is locked. */
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
		for (ArenaInstance instance : ArenaInstancePool.all()) {
			if (instance.isInUse() && instance.protectedBlocks().contains(pos)) {
				return true;
			}
		}
		return false;
	}
}
