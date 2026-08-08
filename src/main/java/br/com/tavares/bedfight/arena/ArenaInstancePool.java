package br.com.tavares.bedfight.arena;

import br.com.tavares.bedfight.config.ArenaConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;

public final class ArenaInstancePool {
	private static final int INSTANCE_Y = 64;
	private static List<ArenaInstance> instances = List.of();

	private ArenaInstancePool() {
	}

	public static void init() {
		ArenaConfig config = ArenaConfig.get();
		List<ArenaInstance> built = new ArrayList<>(config.instancePoolSize);
		for (int i = 0; i < config.instancePoolSize; i++) {
			built.add(new ArenaInstance(i, new BlockPos(i * config.gridSpacingBlocks, INSTANCE_Y, 0)));
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
}
