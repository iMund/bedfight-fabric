package br.com.tavares.bedfight.config;

public final class ArenaConfig {
	private static ArenaConfig instance = new ArenaConfig();

	public int instancePoolSize = 4;
	public int gridSpacingBlocks = 1000;

	public static ArenaConfig get() {
		return instance;
	}

	public static void load() {
		instance = YamlConfigLoader.load("arena.yml", ArenaConfig.class, new ArenaConfig());
	}
}
