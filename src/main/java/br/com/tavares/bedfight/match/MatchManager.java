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
import br.com.tavares.bedfight.config.MatchConfig;
import br.com.tavares.bedfight.kit.KitService;
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
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Queue -&gt; instance/map allocation -&gt; team assignment -&gt; teleport -&gt; countdown/freeze/kit -&gt; active.
 * Death/respawn/elimination, in-match disconnect handling and end-of-match flow are not built yet
 * (see README/memory - this is the next slice on top of this one).
 *
 * All mutable state here is only ever touched from the server thread: ServerPlayConnectionEvents.DISCONNECT
 * does NOT guarantee that on its own (it can fire from Netty's event loop on a client-initiated
 * disconnect), so onDisconnect is always dispatched via server.execute().
 */
public final class MatchManager {
	private static final long JOIN_COOLDOWN_MILLIS = 3000;
	private static final int QUEUE_DRAIN_INTERVAL_TICKS = 100;
	private static final int TICKS_PER_SECOND = 20;
	private static final int MIN_COUNTDOWN_SECONDS = 1;
	private static final int MAX_COUNTDOWN_SECONDS = 60;
	/** Attribute modifiers can't stop a modified client from sending movement packets - this is only the visible/UX half of the freeze, the per-tick position reassert below is the real enforcement. */
	private static final Identifier FREEZE_MODIFIER_ID = Identifier.fromNamespaceAndPath(BedFight.MOD_ID, "countdown_freeze");
	private static final double FREEZE_SPEED_PENALTY = -1024.0;
	/** How far a frozen player can drift from their spawn (teammates overlapping and pushing each other) before getting snapped back. */
	private static final double FREEZE_DRIFT_TOLERANCE = 1.5;

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
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MATCHES.clear();
			PLAYER_MATCH.clear();
			LAST_JOIN_ATTEMPT.clear();
			QUEUES.values().forEach(queue -> new ArrayList<>(queue.waiting()).forEach(queue::leave));
			tickCounter = 0;
		});
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

	/** True while the player's match is in the pre-start countdown - frozen, no PvP, no block placement. */
	public static boolean isFrozen(UUID playerId) {
		Match match = PLAYER_MATCH.get(playerId);
		return match != null && match.state == Match.State.COUNTDOWN;
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
			match = new Match(mode, instance.get(), mapId, arenaLevel);
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
			startCountdown(match);
		}
		return new JoinResult(true, false, "Entrou no time " + team.id() + " (" + match.playerCount() + "/" + mode.totalPlayers() + ").");
	}

	private static void startCountdown(Match match) {
		match.state = Match.State.COUNTDOWN;
		match.ticksInState = 0;
		int seconds = Math.clamp(MatchConfig.get().countdownSeconds, MIN_COUNTDOWN_SECONDS, MAX_COUNTDOWN_SECONDS);
		match.countdownTicks = seconds * TICKS_PER_SECOND;
		for (UUID playerId : match.allPlayers()) {
			ServerPlayer player = match.arenaLevel.getServer().getPlayerList().getPlayer(playerId);
			if (player == null) {
				BedFight.LOGGER.warn("Jogador {} sumiu da lista antes do kit/congelamento da partida.", playerId);
				continue;
			}
			KitService.Result kitResult = KitService.giveKit(player);
			if (!kitResult.isComplete()) {
				BedFight.LOGGER.warn("Kit incompleto pra {} no inicio da partida: {}", playerId, kitResult.failures());
			}
			freeze(player);
			player.sendSystemMessage(Component.literal("Partida comecando em " + seconds + "s...").withStyle(ChatFormatting.GOLD));
		}
	}

	private static void activateMatch(Match match) {
		match.state = Match.State.ACTIVE;
		match.ticksInState = 0;
		for (UUID playerId : match.allPlayers()) {
			ServerPlayer player = match.arenaLevel.getServer().getPlayerList().getPlayer(playerId);
			if (player == null) {
				continue;
			}
			unfreeze(player);
			player.sendSystemMessage(Component.literal("Vai!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
		}
	}

	/** A disconnect mid-countdown leaves the match short-handed with no way to ever refill it (findFormingMatch only matches WAITING_FOR_PLAYERS) - revert instead of leaving it stuck forever. */
	private static void revertToWaiting(Match match) {
		match.state = Match.State.WAITING_FOR_PLAYERS;
		match.ticksInState = 0;
		for (UUID playerId : match.allPlayers()) {
			ServerPlayer player = match.arenaLevel.getServer().getPlayerList().getPlayer(playerId);
			if (player != null) {
				unfreeze(player);
				player.sendSystemMessage(Component.literal("Um jogador saiu, esperando completar de novo...").withStyle(ChatFormatting.YELLOW));
			}
		}
	}

	private static void freeze(ServerPlayer player) {
		addModifier(player, Attributes.MOVEMENT_SPEED);
		addModifier(player, Attributes.JUMP_STRENGTH);
	}

	private static void unfreeze(ServerPlayer player) {
		removeModifier(player, Attributes.MOVEMENT_SPEED);
		removeModifier(player, Attributes.JUMP_STRENGTH);
	}

	private static void addModifier(ServerPlayer player, Holder<Attribute> attribute) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null && !instance.hasModifier(FREEZE_MODIFIER_ID)) {
			instance.addTransientModifier(new AttributeModifier(FREEZE_MODIFIER_ID, FREEZE_SPEED_PENALTY, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	private static void removeModifier(ServerPlayer player, Holder<Attribute> attribute) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) {
			instance.removeModifier(FREEZE_MODIFIER_ID);
		}
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

	private static void onServerTick(MinecraftServer server) {
		tickCounter++;
		if (tickCounter % QUEUE_DRAIN_INTERVAL_TICKS == 0) {
			for (GameMode mode : GameMode.values()) {
				drainQueue(mode, server);
			}
		}
		tickCountdowns(server);
	}

	private static void tickCountdowns(MinecraftServer server) {
		// Indexed loop, not for-each: activateMatch doesn't mutate MATCHES today, but the next
		// slice (elimination/end-of-match) will want to remove a match from within a tick pass,
		// and a for-each here would throw ConcurrentModificationException the day that lands.
		for (int i = 0; i < MATCHES.size(); i++) {
			Match match = MATCHES.get(i);
			if (match.state != Match.State.COUNTDOWN) {
				continue;
			}
			reassertFrozenPositions(match, server);
			match.ticksInState++;
			if (match.ticksInState >= match.countdownTicks) {
				activateMatch(match);
			}
		}
	}

	/** Attribute modifiers only stop a well-behaved client from initiating movement - this is the actual server-authoritative "no mover" enforcement. */
	private static void reassertFrozenPositions(Match match, MinecraftServer server) {
		for (UUID playerId : match.allPlayers()) {
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			Team team = match.teamOf(playerId);
			if (player == null || team == null) {
				continue;
			}
			Optional<ArenaSpawn> spawn = ArenaInstanceService.teamSpawn(match.instance, match.mapId, team);
			if (spawn.isEmpty()) {
				continue;
			}
			ArenaSpawn s = spawn.get();
			double dx = player.getX() - s.x();
			double dy = player.getY() - s.y();
			double dz = player.getZ() - s.z();
			if (dx * dx + dy * dy + dz * dz > FREEZE_DRIFT_TOLERANCE * FREEZE_DRIFT_TOLERANCE) {
				player.teleportTo(match.arenaLevel, s.x(), s.y(), s.z(), Set.of(), player.getYRot(), player.getXRot(), false);
			}
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
		// Full reconnect-aware handling (rejoin an already-running ACTIVE match) isn't built yet -
		// for now any disconnect just drops the player from the roster. A COUNTDOWN match reverts
		// to WAITING_FOR_PLAYERS instead of ticking down short-handed, since nothing could ever
		// refill it otherwise (findFormingMatch only matches WAITING_FOR_PLAYERS).
		boolean wasCountingDown = match.state == Match.State.COUNTDOWN;
		match.removePlayer(playerId);
		if (match.isEmpty()) {
			MATCHES.remove(match);
			ArenaInstanceService.free(match.instance);
			return;
		}
		if (wasCountingDown) {
			revertToWaiting(match);
		}
	}
}
