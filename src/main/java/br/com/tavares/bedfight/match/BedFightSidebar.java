package br.com.tavares.bedfight.match;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * A per-player sidebar sent as raw scoreboard packets, never touching the shared server
 * Scoreboard - other systems (e.g. a separate survival sidebar mod) keep working for everyone
 * who isn't in a match, and this only ever talks to the connections of players who are.
 */
final class BedFightSidebar {
	private static final String OBJECTIVE_NAME = "bedfight_sidebar";
	private static final int MAX_LINES = 12;

	private static final Map<UUID, Integer> LAST_LINE_COUNT = new HashMap<>();

	private BedFightSidebar() {
	}

	/** Shows the sidebar (sending the objective/display packets once) if it isn't already up for this player, then (re)writes its lines - safe to call on every refresh regardless of whether this is the first time. */
	static void display(ServerPlayer player, List<Component> lines) {
		if (!LAST_LINE_COUNT.containsKey(player.getUUID())) {
			Objective objective = objective(player);
			player.connection.send(new ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_ADD));
			player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective));
			LAST_LINE_COUNT.put(player.getUUID(), 0);
		}
		int count = Math.min(lines.size(), MAX_LINES);
		int score = count;
		for (int i = 0; i < count; i++) {
			player.connection.send(new ClientboundSetScorePacket(lineKey(i), OBJECTIVE_NAME, score, Optional.of(lines.get(i)), Optional.of(BlankFormat.INSTANCE)));
			score--;
		}
		int previousCount = LAST_LINE_COUNT.getOrDefault(player.getUUID(), 0);
		for (int i = count; i < previousCount; i++) {
			player.connection.send(new ClientboundResetScorePacket(lineKey(i), OBJECTIVE_NAME));
		}
		LAST_LINE_COUNT.put(player.getUUID(), count);
	}

	static void hide(ServerPlayer player) {
		if (LAST_LINE_COUNT.remove(player.getUUID()) == null) {
			return;
		}
		player.connection.send(new ClientboundSetObjectivePacket(objective(player), ClientboundSetObjectivePacket.METHOD_REMOVE));
	}

	static void forget(UUID playerId) {
		LAST_LINE_COUNT.remove(playerId);
	}

	/** In-memory tracking only survives one server run - a clean slate on shutdown avoids a stale entry skipping the show-packets on the next boot. */
	static void resetAll() {
		LAST_LINE_COUNT.clear();
	}

	private static Objective objective(ServerPlayer player) {
		Component title = Component.literal("BED FIGHT").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
		return new Objective(player.level().getServer().getScoreboard(), OBJECTIVE_NAME, ObjectiveCriteria.DUMMY,
			title, ObjectiveCriteria.RenderType.INTEGER, false, BlankFormat.INSTANCE);
	}

	private static String lineKey(int index) {
		return "bedfight_line_" + index;
	}
}
