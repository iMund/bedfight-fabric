package br.com.tavares.bedfight.match;

import br.com.tavares.bedfight.BedFight;
import br.com.tavares.bedfight.config.YamlConfigLoader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

/** Total wins and current win streak per player - just enough to show on the sidebar, not a full stats/leaderboard system. */
public final class PlayerStatsService {
	private static final Path FILE = YamlConfigLoader.configDir().resolve("stats.yml");
	private static PlayerStatsData data = new PlayerStatsData();
	/** Set when an existing stats.yml fails to parse - save() refuses to run so a corrupt file never gets clobbered with blank data by the next match's save. */
	private static boolean loadFailed;

	private PlayerStatsService() {
	}

	public static void load() {
		try {
			PlayerStatsData loaded = YamlConfigLoader.readExistingOrThrow(FILE, PlayerStatsData.class);
			data = loaded != null ? loaded : new PlayerStatsData();
			loadFailed = false;
		} catch (IOException exception) {
			BedFight.LOGGER.error("stats.yml existe mas nao pode ser lido - estatisticas nao serao salvas ate o arquivo ser corrigido ou removido.", exception);
			data = new PlayerStatsData();
			loadFailed = true;
		}
	}

	/** Read-only, doesn't create an entry - the sidebar shows this for every player it's rendered for, including ones who never finish a match. */
	static int winstreakOf(UUID playerId) {
		PlayerStat stat = data.players.get(playerId.toString());
		return stat != null ? stat.winstreak : 0;
	}

	private static PlayerStat get(UUID playerId) {
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
		if (loadFailed) {
			return;
		}
		YamlConfigLoader.save(FILE, data);
	}
}
