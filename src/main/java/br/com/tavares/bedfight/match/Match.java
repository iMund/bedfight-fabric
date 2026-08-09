package br.com.tavares.bedfight.match;

import br.com.tavares.bedfight.arena.ArenaInstance;
import br.com.tavares.bedfight.arena.Team;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

/** A single arena instance's match, from the moment the first player is teleported in until it ends. */
final class Match {
	enum State {
		WAITING_FOR_PLAYERS,
		COUNTDOWN,
		ACTIVE,
		ENDED
	}

	final GameMode mode;
	final ArenaInstance instance;
	final String mapId;
	final ServerLevel arenaLevel;
	final Map<Team, List<UUID>> rosters = new EnumMap<>(Team.class);
	final Map<Team, Boolean> bedAlive = new EnumMap<>(Team.class);
	/** Players who died after their team's bed was already destroyed - out for the rest of this match. */
	final Set<UUID> eliminated = new HashSet<>();
	final Map<UUID, Integer> kills = new HashMap<>();
	State state = State.WAITING_FOR_PLAYERS;
	int ticksInState;
	int countdownTicks;

	Match(GameMode mode, ArenaInstance instance, String mapId, ServerLevel arenaLevel) {
		this.mode = mode;
		this.instance = instance;
		this.mapId = mapId;
		this.arenaLevel = arenaLevel;
		rosters.put(Team.AZUL, new ArrayList<>());
		rosters.put(Team.VERMELHO, new ArrayList<>());
		bedAlive.put(Team.AZUL, true);
		bedAlive.put(Team.VERMELHO, true);
	}

	int playerCount() {
		return rosters.get(Team.AZUL).size() + rosters.get(Team.VERMELHO).size();
	}

	boolean isFull() {
		return playerCount() >= mode.totalPlayers();
	}

	/** Whichever team has fewer players so far - keeps rosters balanced as players trickle in. */
	Team teamWithSpace() {
		return rosters.get(Team.AZUL).size() <= rosters.get(Team.VERMELHO).size() ? Team.AZUL : Team.VERMELHO;
	}

	void addPlayer(Team team, UUID playerId) {
		rosters.get(team).add(playerId);
	}

	boolean removePlayer(UUID playerId) {
		boolean removed = false;
		for (List<UUID> roster : rosters.values()) {
			removed |= roster.remove(playerId);
		}
		eliminated.remove(playerId);
		return removed;
	}

	boolean isEmpty() {
		return playerCount() == 0;
	}

	List<UUID> allPlayers() {
		List<UUID> all = new ArrayList<>(rosters.get(Team.AZUL));
		all.addAll(rosters.get(Team.VERMELHO));
		return all;
	}

	Team teamOf(UUID playerId) {
		for (Map.Entry<Team, List<UUID>> entry : rosters.entrySet()) {
			if (entry.getValue().contains(playerId)) {
				return entry.getKey();
			}
		}
		return null;
	}

	/** True once every player on this team has died after their bed was already gone - the other team wins. */
	boolean isTeamEliminated(Team team) {
		List<UUID> roster = rosters.get(team);
		return !roster.isEmpty() && eliminated.containsAll(roster);
	}

	Team otherTeam(Team team) {
		return team == Team.AZUL ? Team.VERMELHO : Team.AZUL;
	}
}
