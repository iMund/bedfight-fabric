package br.com.tavares.bedfight.arena;

import java.util.Set;
import net.minecraft.core.BlockPos;

public final class ArenaInstance {
	private final int index;
	private final BlockPos origin;
	private boolean inUse;
	private String mapId;
	private Set<BlockPos> protectedBlocks = Set.of();

	ArenaInstance(int index, BlockPos origin) {
		this.index = index;
		this.origin = origin;
	}

	public int index() {
		return index;
	}

	public BlockPos origin() {
		return origin;
	}

	public boolean isInUse() {
		return inUse;
	}

	public String mapId() {
		return mapId;
	}

	/** Positions that may currently be broken (bed + its wood shell + its end-stone shell), from the last paste. */
	public Set<BlockPos> protectedBlocks() {
		return protectedBlocks;
	}

	void occupy(String mapId, Set<BlockPos> protectedBlocks) {
		this.mapId = mapId;
		this.inUse = true;
		this.protectedBlocks = protectedBlocks;
	}

	void free() {
		this.mapId = null;
		this.inUse = false;
		this.protectedBlocks = Set.of();
	}
}
