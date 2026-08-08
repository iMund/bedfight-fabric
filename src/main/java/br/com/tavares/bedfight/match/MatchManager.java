package br.com.tavares.bedfight.match;

import br.com.tavares.bedfight.BedFight;
import br.com.tavares.bedfight.arena.ArenaDimension;
import br.com.tavares.bedfight.arena.ArenaInstance;
import br.com.tavares.bedfight.arena.ArenaInstancePool;
import br.com.tavares.bedfight.arena.ArenaInstanceService;
import br.com.tavares.bedfight.arena.ArenaSpawn;
import br.com.tavares.bedfight.arena.MapCaptureException;
import br.com.tavares.bedfight.arena.MapRegistry;
import br.com.tavares.bedfight.arena.Team;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Queue -&gt; instance/map allocation -&gt; team assignment -&gt; teleport. Stops at "player is standing in
 * their team's spot with no kit, free to move" - the countdown/freeze/kit-delivery-on-fill,
 * death/respawn/elimination, in-match disconnect handling and end-of-match flow are not built yet
 * (see README/memory - this is the next slice on top of this one).
 *
 * All mutable state here is only ever touched from the server thread: ServerPlayConnectionEvents.DISCONNECT
 * does NOT guarantee that on its own (it can fire from Netty's event loop on a client-initiated
 * disconnect), so onDisconnect is always dispatched via server.execute().
 */
public final class MatchManager {
	private static final long JOIN_COOLDOWN_MILLIS = 3000;
	private static final int QUEUE_DRAIN_INTERVAL_TICKS = 100;

	private static final Map<GameMode, MatchQueue> QUEUES = new EnumMap<>(GameMode.class);
	private static final List<Match> MATCHES = new ArrayList<>();
	private static final Map<UUID, Match> PLAYER_MATCH = new HashMap<>();
	private static final Map<UUID, Long> LAST_JOIN_ATTEMPT = new HashMap<>();
	private static final Random RANDOM = new Random();
	private static int tickCounter;

	static {
		for (GameMode mode : GameMode.values()) {
			QUEUES.put(mode, new MatchQueue());
		}
	}

	public record JoinResult(boolean joined, boolean retryable, String message) {
	}

	private MatchManager() {
	}

	public static void register() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID playerId = handler.getPlayer().getUUID();
			server.execute(() -> onDisconnect(playerId));
		});
		ServerTickEvents.END_SERVER_TICK.register(MatchManager::onServerTick);
	}

	public static JoinResult join(ServerPlayer player, GameMode mode, MinecraftServer server) {
		UUID playerId = player.getUUID();
		if (PLAYER_MATCH.containsKey(playerId) || isQueued(playerId)) {
			return new JoinResult(false, false, "Voce ja esta na fila ou numa partida.");
		}
		long now = System.currentTimeMillis();
		Long lastAttempt = LAST_JOIN_ATTEMPT.get(playerId);
		if (lastAttempt != null && now - lastAttempt < JOIN_COOLDOWN_MILLIS) {
			return new JoinResult(false, false, "Aguarde um pouco antes de tentar de novo.");
		}
		LAST_JOIN_ATTEMPT.put(playerId, now);

		JoinResult result = attemptPlacement(player, mode, server);
		if (result.retryable()) {
			QUEUES.get(mode).join(playerId);
		}
		return result;
	}

	/** How many matches are forming or running - reload guards on this to avoid corrupting a live match. */
	public static int activeMatchCount() {
		return MATCHES.size();
	}

	private static JoinResult attemptPlacement(ServerPlayer player, GameMode mode, MinecraftServer server) {
		ServerLevel arenaLevel = ArenaDimension.get(server);
		if (arenaLevel == null) {
			return new JoinResult(false, false, "Dimensao bedfight:arena nao carregou.");
		}

		Match match = findFormingMatch(mode);
		if (match == null) {
			Optional<ArenaInstance> instance = ArenaInstancePool.findFree();
			if (instance.isEmpty()) {
				return new JoinResult(false, true, "Todas as arenas estao ocupadas, voce esta na fila de " + mode.id() + ".");
			}
			List<String> maps = MapRegistry.listPlayableMapIds();
			if (maps.isEmpty()) {
				return new JoinResult(false, false, "Nenhum mapa pronto (com spawn dos dois times definido) pra jogar ainda.");
			}
			String mapId = maps.get(RANDOM.nextInt(maps.size()));
			try {
				ArenaInstanceService.paste(arenaLevel, instance.get(), mapId);
			} catch (MapCaptureException exception) {
				return new JoinResult(false, false, exception.getMessage());
			} catch (IOException exception) {
				BedFight.LOGGER.error("Falha ao colar o mapa {} pra uma nova partida.", mapId, exception);
				return new JoinResult(false, false, "Falha ao preparar a arena, veja o console.");
			}
			match = new Match(mode, instance.get(), mapId);
			MATCHES.add(match);
		}

		Team team = match.teamWithSpace();
		Optional<ArenaSpawn> spawn = ArenaInstanceService.teamSpawn(match.instance, match.mapId, team);
		if (spawn.isEmpty()) {
			if (match.isEmpty()) {
				MATCHES.remove(match);
				ArenaInstanceService.free(match.instance);
			}
			return new JoinResult(false, false, "Mapa " + match.mapId + " nao tem spawn do time " + team.id() + " definido.");
		}

		UUID playerId = player.getUUID();
		match.addPlayer(team, playerId);
		PLAYER_MATCH.put(playerId, match);
		QUEUES.get(mode).leave(playerId);

		ArenaSpawn s = spawn.get();
		player.teleportTo(arenaLevel, s.x(), s.y(), s.z(), Set.of(), s.yaw(), s.pitch(), false);

		if (match.isFull()) {
			match.state = Match.State.COUNTDOWN;
		}
		return new JoinResult(true, false, "Entrou no time " + team.id() + " (" + match.playerCount() + "/" + mode.totalPlayers() + ").");
	}

	private static boolean isQueued(UUID playerId) {
		return QUEUES.values().stream().anyMatch(queue -> queue.contains(playerId));
	}

	private static Match findFormingMatch(GameMode mode) {
		for (Match match : MATCHES) {
			if (match.mode == mode && match.state == Match.State.WAITING_FOR_PLAYERS && !match.isFull()) {
				return match;
			}
		}
		return null;
	}

	/** Retries queued players periodically so a freed-up instance doesn't leave them stuck forever. */
	private static void onServerTick(MinecraftServer server) {
		tickCounter++;
		if (tickCounter % QUEUE_DRAIN_INTERVAL_TICKS != 0) {
			return;
		}
		for (GameMode mode : GameMode.values()) {
			drainQueue(mode, server);
		}
	}

	private static void drainQueue(GameMode mode, MinecraftServer server) {
		MatchQueue queue = QUEUES.get(mode);
		for (UUID playerId : List.copyOf(queue.waiting())) {
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (player == null) {
				queue.leave(playerId);
				continue;
			}
			JoinResult result = attemptPlacement(player, mode, server);
			if (result.retryable()) {
				return;
			}
			queue.leave(playerId);
			if (result.joined()) {
				player.sendSystemMessage(Component.literal(result.message()).withStyle(ChatFormatting.GREEN));
			} else {
				player.sendSystemMessage(Component.literal(result.message()).withStyle(ChatFormatting.RED));
			}
		}
	}

	private static void onDisconnect(UUID playerId) {
		for (MatchQueue queue : QUEUES.values()) {
			queue.leave(playerId);
		}
		LAST_JOIN_ATTEMPT.remove(playerId);
		Match match = PLAYER_MATCH.remove(playerId);
		if (match == null) {
			return;
		}
		// Full reconnect-aware handling (rejoin an already-running match) isn't built yet - for now
		// any disconnect just drops the player from the roster, whatever state the match is in.
		// Leaving the old, incomplete "only clean up WAITING_FOR_PLAYERS" behavior permanently
		// locked that account out of /bedfight join, which is worse than losing the roster entry.
		match.removePlayer(playerId);
		if (match.isEmpty()) {
			MATCHES.remove(match);
			ArenaInstanceService.free(match.instance);
		}
	}
}
