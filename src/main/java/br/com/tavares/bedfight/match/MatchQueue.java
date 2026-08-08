package br.com.tavares.bedfight.match;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** One waiting line per game mode - 1v1 and 2v2 never share a queue. */
final class MatchQueue {
	private final Set<UUID> waiting = new LinkedHashSet<>();

	boolean join(UUID playerId) {
		return waiting.add(playerId);
	}

	boolean leave(UUID playerId) {
		return waiting.remove(playerId);
	}

	boolean contains(UUID playerId) {
		return waiting.contains(playerId);
	}

	int size() {
		return waiting.size();
	}

	Set<UUID> waiting() {
		return Collections.unmodifiableSet(waiting);
	}
}
