package br.com.tavares.bedfight.arena;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Finds every block that's allowed to be broken in a freshly-pasted arena: each bed, the
 * contiguous wood shell touching it, and the contiguous end-stone shell touching that wood.
 * Everything else in the pasted structure stays unbreakable for the whole match.
 */
public final class BedProtectionScanner {
	private BedProtectionScanner() {
	}

	public static Set<BlockPos> scan(ServerLevel level, BlockPos origin, Vec3i size) {
		BlockPos max = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);

		Set<BlockPos> beds = new HashSet<>();
		BlockPos.betweenClosed(origin, max).forEach(pos -> {
			if (level.getBlockState(pos).is(BlockTags.BEDS)) {
				beds.add(pos.immutable());
			}
		});

		Set<BlockPos> protectedBlocks = new HashSet<>(beds);
		Set<BlockPos> woodShell = floodFill(level, beds, origin, max, protectedBlocks, state -> state.is(BlockTags.PLANKS) || state.is(BlockTags.LOGS));
		floodFill(level, woodShell, origin, max, protectedBlocks, state -> state.is(Blocks.END_STONE));

		return protectedBlocks;
	}

	private static Set<BlockPos> floodFill(ServerLevel level, Set<BlockPos> seeds, BlockPos min, BlockPos max, Set<BlockPos> protectedBlocks, java.util.function.Predicate<BlockState> matches) {
		Set<BlockPos> shell = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		for (BlockPos seed : seeds) {
			for (Direction direction : Direction.values()) {
				queue.add(seed.relative(direction));
			}
		}
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			if (!isWithin(pos, min, max) || shell.contains(pos) || protectedBlocks.contains(pos)) {
				continue;
			}
			if (!matches.test(level.getBlockState(pos))) {
				continue;
			}
			BlockPos immutable = pos.immutable();
			shell.add(immutable);
			protectedBlocks.add(immutable);
			for (Direction direction : Direction.values()) {
				queue.add(pos.relative(direction));
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
