package br.com.tavares.bedfight.config;

public final class MatchConfig {
	private static MatchConfig instance = new MatchConfig();

	public int countdownSeconds = 5;
	public int respawnDelaySeconds = 3;
	public int endMatchChoiceSeconds = 3;

	public static MatchConfig get() {
		return instance;
	}

	public static void load() {
		instance = YamlConfigLoader.load("match.yml", MatchConfig.class, new MatchConfig());
	}
}
