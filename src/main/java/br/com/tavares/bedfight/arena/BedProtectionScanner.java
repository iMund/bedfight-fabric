package br.com.tavares.bedfight.arena;

import br.com.tavares.bedfight.BedFight;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

	public record Result(Set<BlockPos> breakableBlocks, Set<BlockPos> bedPositions, int bedCount, int woodCount, int endStoneCount) {
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

		return new Result(breakableBlocks, Set.copyOf(beds), beds.size(), woodShell.size(), endStoneShell.size());
	}

	/** North/south/east/west only, not up/down - a vanilla bed's head and foot are always on the same Y, and this avoids merging two maps' beds that happen to be stacked on different floors into one group. */
	private static final Direction[] HORIZONTAL_DIRECTIONS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

	/** Groups individual bed blocks into physical beds (a vanilla bed is exactly 2 horizontally-adjacent blocks) via connected components. */
	public static List<Set<BlockPos>> groupBeds(Set<BlockPos> bedPositions) {
		List<Set<BlockPos>> groups = new ArrayList<>();
		Set<BlockPos> visited = new HashSet<>();
		for (BlockPos start : bedPositions) {
			if (!visited.add(start)) {
				continue;
			}
			Set<BlockPos> group = new HashSet<>();
			Deque<BlockPos> queue = new ArrayDeque<>();
			queue.add(start);
			group.add(start);
			while (!queue.isEmpty()) {
				BlockPos pos = queue.poll();
				for (Direction direction : HORIZONTAL_DIRECTIONS) {
					BlockPos next = pos.relative(direction).immutable();
					if (bedPositions.contains(next) && visited.add(next)) {
						group.add(next);
						queue.add(next);
					}
				}
			}
			groups.add(group);
		}
		return groups;
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
