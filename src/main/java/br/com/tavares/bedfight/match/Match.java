package br.com.tavares.bedfight.match;

import br.com.tavares.bedfight.arena.ArenaInstance;
import br.com.tavares.bedfight.arena.Team;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
	final Map<Team, List<UUID>> rosters = new EnumMap<>(Team.class);
	State state = State.WAITING_FOR_PLAYERS;

	Match(GameMode mode, ArenaInstance instance, String mapId) {
		this.mode = mode;
		this.instance = instance;
		this.mapId = mapId;
		rosters.put(Team.AZUL, new ArrayList<>());
		rosters.put(Team.VERMELHO, new ArrayList<>());
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
		return removed;
	}

	boolean isEmpty() {
		return playerCount() == 0;
	}
}
