package br.com.tavares.bedfight.match;

import br.com.tavares.bedfight.BedFight;
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
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Queue -&gt; instance/map allocation -&gt; team assignment -&gt; teleport. Stops at "player is standing in
 * their team's spot with no kit, free to move" - the countdown/freeze/kit-delivery-on-fill,
 * death/respawn/elimination, in-match disconnect handling and end-of-match flow are not built yet
 * (see README/memory - this is the next slice on top of this one).
 */
public final class MatchManager {
	private static final Map<GameMode, MatchQueue> QUEUES = new EnumMap<>(GameMode.class);
	private static final List<Match> MATCHES = new ArrayList<>();
	private static final Map<UUID, Match> PLAYER_MATCH = new HashMap<>();
	private static final Random RANDOM = new Random();

	static {
		for (GameMode mode : GameMode.values()) {
			QUEUES.put(mode, new MatchQueue());
		}
	}

	public record JoinResult(boolean joined, String message) {
	}

	private MatchManager() {
	}

	public static void register() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onDisconnect(handler.getPlayer().getUUID()));
	}

	public static JoinResult join(ServerPlayer player, GameMode mode, ServerLevel arenaLevel) {
		UUID playerId = player.getUUID();
		if (PLAYER_MATCH.containsKey(playerId) || isQueued(playerId)) {
			return new JoinResult(false, "Voce ja esta na fila ou numa partida.");
		}

		Match match = findFormingMatch(mode);
		if (match == null) {
			Optional<ArenaInstance> instance = ArenaInstancePool.findFree();
			if (instance.isEmpty()) {
				QUEUES.get(mode).join(playerId);
				return new JoinResult(false, "Todas as arenas estao ocupadas, voce foi colocado na fila de " + mode.id() + ".");
			}
			List<String> maps = MapRegistry.listMapIds();
			if (maps.isEmpty()) {
				return new JoinResult(false, "Nenhum mapa capturado ainda, nao da pra iniciar partida.");
			}
			String mapId = maps.get(RANDOM.nextInt(maps.size()));
			try {
				ArenaInstanceService.paste(arenaLevel, instance.get(), mapId);
			} catch (MapCaptureException exception) {
				return new JoinResult(false, exception.getMessage());
			} catch (IOException exception) {
				BedFight.LOGGER.error("Falha ao colar o mapa {} pra uma nova partida.", mapId, exception);
				return new JoinResult(false, "Falha ao preparar a arena, veja o console.");
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
			return new JoinResult(false, "Mapa " + match.mapId + " nao tem spawn do time " + team.id() + " definido.");
		}

		match.addPlayer(team, playerId);
		PLAYER_MATCH.put(playerId, match);
		QUEUES.get(mode).leave(playerId);

		ArenaSpawn s = spawn.get();
		player.teleportTo(arenaLevel, s.x(), s.y(), s.z(), Set.of(), s.yaw(), s.pitch(), false);

		if (match.isFull()) {
			match.state = Match.State.COUNTDOWN;
		}
		return new JoinResult(true, "Entrou no time " + team.id() + " (" + match.playerCount() + "/" + mode.totalPlayers() + ").");
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

	private static void onDisconnect(UUID playerId) {
		for (MatchQueue queue : QUEUES.values()) {
			queue.leave(playerId);
		}
		Match match = PLAYER_MATCH.get(playerId);
		if (match == null || match.state != Match.State.WAITING_FOR_PLAYERS) {
			// A player disconnecting mid-countdown/active match needs the full reconnect-aware
			// handling from the confirmed design - not built yet, so leave PLAYER_MATCH alone here.
			return;
		}
		PLAYER_MATCH.remove(playerId);
		match.removePlayer(playerId);
		if (match.isEmpty()) {
			MATCHES.remove(match);
			ArenaInstanceService.free(match.instance);
		}
	}
}
