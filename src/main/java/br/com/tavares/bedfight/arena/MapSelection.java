package br.com.tavares.bedfight.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class MapSelection {
	public ResourceKey<Level> dimension;
	public BlockPos pos1;
	public BlockPos pos2;

	public boolean isComplete() {
		return pos1 != null && pos2 != null;
	}

	public BlockPos min() {
		return BlockPos.min(pos1, pos2);
	}

	public BlockPos max() {
		return BlockPos.max(pos1, pos2);
	}
}
