package br.com.tavares.bedfight.arena;

import net.minecraft.ChatFormatting;

public enum Team {
	AZUL("azul", ChatFormatting.BLUE),
	VERMELHO("vermelho", ChatFormatting.RED);

	private final String id;
	private final ChatFormatting color;

	Team(String id, ChatFormatting color) {
		this.id = id;
		this.color = color;
	}

	public String id() {
		return id;
	}

	public ChatFormatting color() {
		return color;
	}

	public static Team byId(String id) {
		for (Team team : values()) {
			if (team.id.equalsIgnoreCase(id)) {
				return team;
			}
		}
		throw new IllegalArgumentException("Time desconhecido: " + id);
	}
}
