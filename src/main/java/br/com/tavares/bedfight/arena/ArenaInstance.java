package br.com.tavares.bedfight.arena;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class ArenaInstance {
	/** Every instance pastes at the same local coordinate inside its own dimension now - there's no shared space left to collide in, so there's no reason for it to vary per instance. */
	private static final BlockPos ORIGIN = new BlockPos(0, 64, 0);

	private final int index;
	private final ResourceKey<Level> dimensionKey;
	private boolean inUse;
	private String mapId;
	private final Set<BlockPos> breakableBlocks = new HashSet<>();
	private Map<Team, Set<BlockPos>> teamBeds = Map.of();

	ArenaInstance(int index, ResourceKey<Level> dimensionKey) {
		this.index = index;
		this.dimensionKey = dimensionKey;
	}

	public int index() {
		return index;
	}

	public ResourceKey<Level> dimensionKey() {
		return dimensionKey;
	}

	public ServerLevel level(MinecraftServer server) {
		return server.getLevel(dimensionKey);
	}

	public BlockPos origin() {
		return ORIGIN;
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

	/** Which team's bed (if any) a given block belongs to, for destruction detection - empty if it's not part of a bed. */
	public Optional<Team> teamOfBedBlock(BlockPos pos) {
		for (Map.Entry<Team, Set<BlockPos>> entry : teamBeds.entrySet()) {
			if (entry.getValue().contains(pos)) {
				return Optional.of(entry.getKey());
			}
		}
		return Optional.empty();
	}

	void occupy(String mapId, Set<BlockPos> initialBreakable) {
		this.mapId = mapId;
		this.inUse = true;
		this.breakableBlocks.clear();
		this.breakableBlocks.addAll(initialBreakable);
	}

	void setTeamBeds(Map<Team, Set<BlockPos>> teamBeds) {
		this.teamBeds = teamBeds;
	}

	void free() {
		this.mapId = null;
		this.inUse = false;
		this.breakableBlocks.clear();
		this.teamBeds = Map.of();
	}
}
