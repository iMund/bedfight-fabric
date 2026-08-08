package br.com.tavares.bedfight.arena;

import net.minecraft.core.BlockPos;

public final class ArenaInstance {
	private final int index;
	private final BlockPos origin;
	private boolean inUse;
	private String mapId;

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

	void occupy(String mapId) {
		this.mapId = mapId;
		this.inUse = true;
	}

	void free() {
		this.mapId = null;
		this.inUse = false;
	}
}
