package br.com.tavares.bedfight.arena;

import br.com.tavares.bedfight.BedFight;
import br.com.tavares.bedfight.config.ArenaConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;

public final class ArenaInstancePool {
	private static final int INSTANCE_Y = 64;
	private static final int MIN_GRID_SPACING = 256;
	private static final int MAX_POOL_SIZE = 64;
	private static List<ArenaInstance> instances = List.of();
	private static int gridSpacing = MIN_GRID_SPACING;

	private ArenaInstancePool() {
	}

	public static void init() {
		ArenaConfig config = ArenaConfig.get();
		int poolSize = Math.clamp(config.instancePoolSize, 1, MAX_POOL_SIZE);
		int spacing = Math.max(config.gridSpacingBlocks, MIN_GRID_SPACING);
		if (poolSize != config.instancePoolSize || spacing != config.gridSpacingBlocks) {
			BedFight.LOGGER.warn("arena.yml tinha instancePoolSize={} gridSpacingBlocks={} fora do intervalo aceito, usando {}/{}.",
				config.instancePoolSize, config.gridSpacingBlocks, poolSize, spacing);
		}
		gridSpacing = spacing;

		List<ArenaInstance> built = new ArrayList<>(poolSize);
		for (int i = 0; i < poolSize; i++) {
			built.add(new ArenaInstance(i, new BlockPos(i * spacing, INSTANCE_Y, 0)));
		}
		instances = built;
	}

	public static List<ArenaInstance> all() {
		return instances;
	}

	public static Optional<ArenaInstance> findFree() {
		return instances.stream().filter(instance -> !instance.isInUse()).findFirst();
	}

	public static Optional<ArenaInstance> byIndex(int index) {
		return instances.stream().filter(instance -> instance.index() == index).findFirst();
	}

	/** Which instance's grid cell a position falls in - every instance occupies the same fixed-width X slice, so this is exact as long as maps are validated to fit within gridSpacingBlocks (see ArenaInstanceService.paste). */
	public static Optional<ArenaInstance> findByPosition(BlockPos pos) {
		return byIndex(Math.floorDiv(pos.getX(), gridSpacing));
	}
}
