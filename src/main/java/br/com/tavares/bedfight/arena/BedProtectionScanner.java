package br.com.tavares.bedfight.arena;

import br.com.tavares.bedfight.BedFight;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Finds every block that's allowed to be broken in a freshly-pasted arena: each bed, the
 * wood shell touching it, and the end-stone shell touching that wood. The fill is bounded to a
 * small radius from the bed - without a bound, a shell block that happens to touch the island's
 * own floor/walkway (also wood or end stone) would flood-fill the entire island.
 */
public final class BedProtectionScanner {
	private static final int MAX_SHELL_DEPTH = 4;

	public record Result(Set<BlockPos> breakableBlocks, int bedCount, int woodCount, int endStoneCount) {
	}

	private BedProtectionScanner() {
	}

	public static Result scan(ServerLevel level, BlockPos origin, Vec3i size) {
		BlockPos max = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);

		Set<BlockPos> beds = new HashSet<>();
		BlockPos.betweenClosed(origin, max).forEach(pos -> {
			if (level.getBlockState(pos).is(BlockTags.BEDS)) {
				beds.add(pos.immutable());
			}
		});

		Set<BlockPos> breakableBlocks = new HashSet<>(beds);
		Set<BlockPos> woodShell = floodFill(level, beds, origin, max, breakableBlocks, state -> state.is(BlockTags.PLANKS) || state.is(BlockTags.LOGS));
		Set<BlockPos> endStoneShell = floodFill(level, woodShell, origin, max, breakableBlocks, state -> state.is(Blocks.END_STONE));

		if (beds.isEmpty() || woodShell.isEmpty() || endStoneShell.isEmpty()) {
			BedFight.LOGGER.warn("Protecao de cama incompleta ao colar em {}: {} cama(s), {} bloco(s) de madeira, {} end stone. "
				+ "A cama pode ficar indestrutivel se algum desses for zero.", origin, beds.size(), woodShell.size(), endStoneShell.size());
		}

		return new Result(breakableBlocks, beds.size(), woodShell.size(), endStoneShell.size());
	}

	/** Breadth-first, but never more than MAX_SHELL_DEPTH steps away from a seed - keeps a leak local instead of swallowing the island. */
	private static Set<BlockPos> floodFill(ServerLevel level, Set<BlockPos> seeds, BlockPos min, BlockPos max, Set<BlockPos> breakableBlocks, Predicate<BlockState> matches) {
		Set<BlockPos> shell = new HashSet<>();
		Map<BlockPos, Integer> depth = new HashMap<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		for (BlockPos seed : seeds) {
			for (Direction direction : Direction.values()) {
				BlockPos next = seed.relative(direction).immutable();
				if (depth.putIfAbsent(next, 1) == null) {
					queue.add(next);
				}
			}
		}
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			if (shell.contains(pos) || breakableBlocks.contains(pos) || !isWithin(pos, min, max)) {
				continue;
			}
			int currentDepth = depth.getOrDefault(pos, MAX_SHELL_DEPTH + 1);
			if (currentDepth > MAX_SHELL_DEPTH || !matches.test(level.getBlockState(pos))) {
				continue;
			}
			shell.add(pos);
			breakableBlocks.add(pos);
			for (Direction direction : Direction.values()) {
				BlockPos next = pos.relative(direction).immutable();
				if (!shell.contains(next) && depth.getOrDefault(next, Integer.MAX_VALUE) > currentDepth + 1) {
					depth.put(next, currentDepth + 1);
					queue.add(next);
				}
			}
		}
		return shell;
	}

	private static boolean isWithin(BlockPos pos, BlockPos min, BlockPos max) {
		return pos.getX() >= min.getX() && pos.getX() <= max.getX()
			&& pos.getY() >= min.getY() && pos.getY() <= max.getY()
			&& pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
	}
}
