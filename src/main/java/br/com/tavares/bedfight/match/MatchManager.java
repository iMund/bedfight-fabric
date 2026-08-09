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
import br.com.tavares.bedfight.arena.ArenaInstancePool;
import br.com.tavares.bedfight.config.MatchConfig;
import br.com.tavares.bedfight.kit.KitService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Queue -&gt; instance/map allocation -&gt; team assignment -&gt; teleport -&gt; countdown/freeze/kit -&gt; active
 * -&gt; death/respawn (if the team's bed is alive) or elimination (if it isn't) -&gt; end of match once a
 * whole team is eliminated. Full end-of-match UX (return-to-lobby items/timer) is not built yet -
 * ending a match just frees the arena and clears PLAYER_MATCH so the accounts can requeue.
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
	/** Ticks left until a dead player (whose bed is still alive) respawns - absence means not waiting to respawn. */
	private static final Map<UUID, Integer> RESPAWN_TICKS = new HashMap<>();
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
		PlayerBlockBreakEvents.BEFORE.register(MatchManager::onBeforeBedBreak);
		PlayerBlockBreakEvents.AFTER.register(MatchManager::onBedBreakAfter);
		ServerLivingEntityEvents.ALLOW_DEATH.register(MatchManager::onAllowDeath);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MATCHES.clear();
			PLAYER_MATCH.clear();
			LAST_JOIN_ATTEMPT.clear();
			RESPAWN_TICKS.clear();
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
		resetPlayerState(player);

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
			KitService.Result kitResult = KitService.giveKit(player, match.teamOf(playerId));
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

	/** Other server systems (lobby protection, speed boosts) leave their state on the player when they're pulled into the arena mid-dimension-change - force a clean slate rather than relying on those systems noticing they've left. */
	private static void resetPlayerState(ServerPlayer player) {
		player.setGameMode(GameType.SURVIVAL);
		player.removeAllEffects();
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
		tickRespawns(server);
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

	/**
	 * Any death inside a tracked match is intercepted, not just ACTIVE ones - a player who falls into
	 * the void while still waiting/frozen in the arena must not fall through to vanilla's respawn
	 * (that's the exact bug this whole feature exists to fix), it just doesn't have "eliminated" stakes
	 * before the match is actually running.
	 */
	private static boolean onAllowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player)) {
			return true;
		}
		Match match = PLAYER_MATCH.get(player.getUUID());
		if (match == null) {
			return true;
		}
		boolean handled = switch (match.state) {
			case ACTIVE -> handlePlayerDeath(match, player);
			case WAITING_FOR_PLAYERS, COUNTDOWN -> handlePreMatchDeath(match, player);
			default -> false;
		};
		return !handled;
	}

	/** Not in a match yet or the match already ended by the time this fires - nothing sane to do, let vanilla handle it. */
	private static boolean handlePreMatchDeath(Match match, ServerPlayer player) {
		Team team = match.teamOf(player.getUUID());
		if (team == null) {
			return false;
		}
		player.setHealth(player.getMaxHealth());
		player.clearFire();
		Optional<ArenaSpawn> spawn = ArenaInstanceService.teamSpawn(match.instance, match.mapId, team);
		spawn.ifPresent(s -> player.teleportTo(match.arenaLevel, s.x(), s.y(), s.z(), Set.of(), s.yaw(), s.pitch(), false));
		return true;
	}

	/**
	 * Denying vanilla death (returning false from ALLOW_DEATH) leaves health at/below zero unless we
	 * fix it ourselves - heal and move away from danger first, before anything else, or a void-fall
	 * death would keep re-triggering this every tick. Returns false (letting vanilla handle it after
	 * all) if this player somehow isn't actually on the match's roster, so the caller never denies
	 * death without also having healed.
	 */
	private static boolean handlePlayerDeath(Match match, ServerPlayer player) {
		UUID playerId = player.getUUID();
		Team team = match.teamOf(playerId);
		if (team == null) {
			return false;
		}
		player.setHealth(player.getMaxHealth());
		player.clearFire();
		player.getInventory().clearContent();
		player.inventoryMenu.getCraftSlots().clearContent();
		player.containerMenu.setCarried(ItemStack.EMPTY);
		player.setGameMode(GameType.SPECTATOR);

		Optional<ArenaSpawn> spawn = ArenaInstanceService.teamSpawn(match.instance, match.mapId, team);
		spawn.ifPresent(s -> player.teleportTo(match.arenaLevel, s.x(), s.y(), s.z(), Set.of(), s.yaw(), s.pitch(), false));

		if (Boolean.TRUE.equals(match.bedAlive.get(team))) {
			int seconds = Math.clamp(MatchConfig.get().respawnDelaySeconds, 1, 60);
			RESPAWN_TICKS.put(playerId, seconds * TICKS_PER_SECOND);
			player.sendSystemMessage(Component.literal("Voce morreu! Renascendo em " + seconds + "s...").withStyle(ChatFormatting.RED));
		} else {
			match.eliminated.add(playerId);
			player.sendSystemMessage(Component.literal("Sua cama foi destruida, voce foi eliminado da partida.").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
			checkTeamElimination(match, team);
		}
		return true;
	}

	private static void tickRespawns(MinecraftServer server) {
		if (RESPAWN_TICKS.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Integer>> iterator = RESPAWN_TICKS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Integer> entry = iterator.next();
			int remaining = entry.getValue() - 1;
			if (remaining > 0) {
				entry.setValue(remaining);
				continue;
			}
			iterator.remove();
			respawnPlayer(entry.getKey(), server);
		}
	}

	private static void respawnPlayer(UUID playerId, MinecraftServer server) {
		Match match = PLAYER_MATCH.get(playerId);
		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		if (match == null || player == null || match.state != Match.State.ACTIVE) {
			return;
		}
		Team team = match.teamOf(playerId);
		if (team == null) {
			return;
		}
		Optional<ArenaSpawn> spawn = ArenaInstanceService.teamSpawn(match.instance, match.mapId, team);
		spawn.ifPresent(s -> player.teleportTo(match.arenaLevel, s.x(), s.y(), s.z(), Set.of(), s.yaw(), s.pitch(), false));
		player.setGameMode(GameType.SURVIVAL);
		player.setHealth(player.getMaxHealth());
		KitService.Result kitResult = KitService.giveKit(player, team);
		if (!kitResult.isComplete()) {
			BedFight.LOGGER.warn("Kit incompleto ao renascer {}: {}", playerId, kitResult.failures());
		}
		player.sendSystemMessage(Component.literal("Voce renasceu!").withStyle(ChatFormatting.GREEN));
	}

	/** ArenaBlockProtection marks every bed block breakable for anyone (it doesn't know about teams) - this denies breaking your own team's bed specifically, so the only way to lose a bed is the enemy destroying it. */
	private static boolean onBeforeBedBreak(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
		if (!(level instanceof ServerLevel serverLevel) || serverLevel.dimension() != ArenaDimension.KEY || !(player instanceof ServerPlayer serverPlayer)) {
			return true;
		}
		Match match = PLAYER_MATCH.get(serverPlayer.getUUID());
		if (match == null) {
			return true;
		}
		Team breakerTeam = match.teamOf(serverPlayer.getUUID());
		return ArenaInstancePool.findByPosition(pos)
			.flatMap(instance -> instance.teamOfBedBlock(pos))
			.map(bedTeam -> bedTeam != breakerTeam)
			.orElse(true);
	}

	private static void onBedBreakAfter(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
		if (!(level instanceof ServerLevel serverLevel) || serverLevel.dimension() != ArenaDimension.KEY) {
			return;
		}
		ArenaInstancePool.findByPosition(pos).ifPresent(instance -> onBedBlockBroken(instance, pos));
	}

	private static void onBedBlockBroken(ArenaInstance instance, BlockPos pos) {
		if (!instance.isInUse()) {
			return;
		}
		instance.teamOfBedBlock(pos).ifPresent(team -> {
			Match match = findMatchByInstance(instance);
			if (match == null || match.state != Match.State.ACTIVE || !Boolean.TRUE.equals(match.bedAlive.get(team))) {
				return;
			}
			match.bedAlive.put(team, false);
			announceBedDestroyed(match, team);
		});
	}

	private static void announceBedDestroyed(Match match, Team team) {
		Component message = Component.literal("A cama do time " + team.id() + " foi destruida!").withStyle(team.color(), ChatFormatting.BOLD);
		for (UUID playerId : match.allPlayers()) {
			ServerPlayer player = match.arenaLevel.getServer().getPlayerList().getPlayer(playerId);
			if (player != null) {
				player.sendSystemMessage(message);
			}
		}
	}

	private static void checkTeamElimination(Match match, Team deadTeam) {
		if (!match.isTeamEliminated(deadTeam)) {
			return;
		}
		endMatch(match, match.otherTeam(deadTeam));
	}

	/** Minimal end-of-match: announce, free the arena, let the accounts requeue. No return-to-lobby UX yet - that's a separate follow-up (bedfight-fabric doesn't own the lobby). */
	private static void endMatch(Match match, Team winner) {
		match.state = Match.State.ENDED;
		Component message = Component.literal("Time " + winner.id() + " venceu a partida!").withStyle(winner.color(), ChatFormatting.BOLD);
		for (UUID playerId : match.allPlayers()) {
			RESPAWN_TICKS.remove(playerId);
			PLAYER_MATCH.remove(playerId);
			ServerPlayer player = match.arenaLevel.getServer().getPlayerList().getPlayer(playerId);
			if (player != null) {
				player.sendSystemMessage(message);
				player.setGameMode(GameType.SPECTATOR);
			}
		}
		MATCHES.remove(match);
		ArenaInstanceService.free(match.instance);
	}

	private static Match findMatchByInstance(ArenaInstance instance) {
		for (Match match : MATCHES) {
			if (match.instance == instance) {
				return match;
			}
		}
		return null;
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
		RESPAWN_TICKS.remove(playerId);
		Match match = PLAYER_MATCH.remove(playerId);
		if (match == null) {
			return;
		}
		// Full reconnect-aware handling (rejoin an already-running ACTIVE match) isn't built yet -
		// for now any disconnect just drops the player from the roster. A COUNTDOWN match reverts
		// to WAITING_FOR_PLAYERS instead of ticking down short-handed, since nothing could ever
		// refill it otherwise (findFormingMatch only matches WAITING_FOR_PLAYERS). An ACTIVE match
		// whose last player on a team disconnects awards the win to the other team instead of
		// leaving the match stuck ACTIVE forever with a permanently-unwinnable roster.
		boolean wasCountingDown = match.state == Match.State.COUNTDOWN;
		boolean wasActive = match.state == Match.State.ACTIVE;
		Team team = match.teamOf(playerId);
		match.removePlayer(playerId);
		if (match.isEmpty()) {
			MATCHES.remove(match);
			ArenaInstanceService.free(match.instance);
			return;
		}
		if (wasCountingDown) {
			revertToWaiting(match);
			return;
		}
		if (wasActive && team != null && match.rosters.get(team).isEmpty()) {
			endMatch(match, match.otherTeam(team));
		}
	}
}
