package br.com.tavares.bedfight.match;

public enum GameMode {
	ONE_V_ONE("1v1", 1),
	TWO_V_TWO("2v2", 2);

	private final String id;
	private final int playersPerTeam;

	GameMode(String id, int playersPerTeam) {
		this.id = id;
		this.playersPerTeam = playersPerTeam;
	}

	public String id() {
		return id;
	}

	public int playersPerTeam() {
		return playersPerTeam;
	}

	public int totalPlayers() {
		return playersPerTeam * 2;
	}

	public static GameMode byId(String id) {
		for (GameMode mode : values()) {
			if (mode.id.equalsIgnoreCase(id)) {
				return mode;
			}
		}
		throw new IllegalArgumentException("Modo desconhecido: " + id);
	}
}
