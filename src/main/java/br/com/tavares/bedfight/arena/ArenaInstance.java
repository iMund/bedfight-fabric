package br.com.tavares.bedfight.arena;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;

public final class ArenaInstance {
	private final int index;
	private final BlockPos origin;
	private boolean inUse;
	private String mapId;
	private final Set<BlockPos> breakableBlocks = new HashSet<>();

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

	/** True for the bed/wood/end-stone shell from the last paste, plus anything a player has placed since. */
	public boolean isBreakable(BlockPos pos) {
		return breakableBlocks.contains(pos);
	}

	/** Called when a player places a block inside this instance - their own bridge stays breakable by them. */
	public void markPlaced(BlockPos pos) {
		breakableBlocks.add(pos.immutable());
	}

	void occupy(String mapId, Set<BlockPos> initialBreakable) {
		this.mapId = mapId;
		this.inUse = true;
		this.breakableBlocks.clear();
		this.breakableBlocks.addAll(initialBreakable);
	}

	void free() {
		this.mapId = null;
		this.inUse = false;
		this.breakableBlocks.clear();
	}
}
