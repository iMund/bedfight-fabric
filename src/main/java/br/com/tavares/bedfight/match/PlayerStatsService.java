package br.com.tavares.bedfight.match;

import br.com.tavares.bedfight.config.YamlConfigLoader;
import java.nio.file.Path;
import java.util.UUID;

/** Total wins and current win streak per player - just enough to show on the sidebar, not a full stats/leaderboard system. */
public final class PlayerStatsService {
	private static final Path FILE = YamlConfigLoader.configDir().resolve("stats.yml");
	private static PlayerStatsData data = new PlayerStatsData();

	private PlayerStatsService() {
	}

	public static void load() {
		data = YamlConfigLoader.readIfExists(FILE, PlayerStatsData.class, new PlayerStatsData());
	}

	static PlayerStat get(UUID playerId) {
		return data.players.computeIfAbsent(playerId.toString(), id -> new PlayerStat());
	}

	static void recordWin(UUID playerId) {
		PlayerStat stat = get(playerId);
		stat.wins++;
		stat.winstreak++;
	}

	static void recordLoss(UUID playerId) {
		get(playerId).winstreak = 0;
	}

	static void save() {
		YamlConfigLoader.save(FILE, data);
	}
}
