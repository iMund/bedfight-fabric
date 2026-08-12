package br.com.tavares.bedfight.arena;

import br.com.tavares.bedfight.BedFight;
import br.com.tavares.bedfight.config.ArenaConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class ArenaInstancePool {
	/** How many bedfight:arena_N dimensions actually exist as datapack JSON (see data/bedfight/dimension) - the hard ceiling on how many instances can ever be pooled, since Fabric/vanilla dimensions are fixed at server start, not created on demand. */
	private static final int MAX_POOL_SIZE = 16;
	private static List<ArenaInstance> instances = List.of();

	private ArenaInstancePool() {
	}

	public static void init() {
		ArenaConfig config = ArenaConfig.get();
		int poolSize = Math.clamp(config.instancePoolSize, 1, MAX_POOL_SIZE);
		if (poolSize != config.instancePoolSize) {
			BedFight.LOGGER.warn("arena.yml tinha instancePoolSize={} fora do intervalo aceito (1-{}), usando {}.",
				config.instancePoolSize, MAX_POOL_SIZE, poolSize);
		}

		List<ArenaInstance> built = new ArrayList<>(poolSize);
		for (int i = 0; i < poolSize; i++) {
			built.add(new ArenaInstance(i, ArenaDimension.arenaKey(i)));
		}
		instances = built;
	}

	public static List<ArenaInstance> all() {
		return instances;
	}

	public static Optional<ArenaInstance> findFree() {
		for (ArenaInstance instance : instances) {
			if (!instance.isInUse()) {
				return Optional.of(instance);
			}
		}
		return Optional.empty();
	}

	public static Optional<ArenaInstance> byIndex(int index) {
		for (ArenaInstance instance : instances) {
			if (instance.index() == index) {
				return Optional.of(instance);
			}
		}
		return Optional.empty();
	}

	/**
	 * Which instance owns this dimension, if any - now that each instance is its own dimension, this
	 * is the same question as "which instance is this block/player in". Called on every block
	 * break/place server-wide (not just inside an arena), so this is a plain loop rather than a
	 * Stream pipeline - the instance count is small (at most MAX_POOL_SIZE) but this runs constantly.
	 */
	public static Optional<ArenaInstance> findByDimension(ResourceKey<Level> dimensionKey) {
		for (ArenaInstance instance : instances) {
			if (instance.dimensionKey() == dimensionKey) {
				return Optional.of(instance);
			}
		}
		return Optional.empty();
	}

	/** Cheap membership check for hot paths (mixins) that only need to know "are we in any arena instance", not which one. */
	public static boolean isAnyArenaInstance(ResourceKey<Level> dimensionKey) {
		for (ArenaInstance instance : instances) {
			if (instance.dimensionKey() == dimensionKey) {
				return true;
			}
		}
		return false;
	}
}
