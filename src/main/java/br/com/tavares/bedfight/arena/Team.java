package br.com.tavares.bedfight.arena;

import net.minecraft.ChatFormatting;
import net.minecraft.world.item.DyeColor;

public enum Team {
	AZUL("azul", ChatFormatting.BLUE, DyeColor.BLUE),
	VERMELHO("vermelho", ChatFormatting.RED, DyeColor.RED);

	private final String id;
	private final ChatFormatting color;
	private final DyeColor dyeColor;

	Team(String id, ChatFormatting color, DyeColor dyeColor) {
		this.id = id;
		this.color = color;
		this.dyeColor = dyeColor;
	}

	public String id() {
		return id;
	}

	public ChatFormatting color() {
		return color;
	}

	/** Single source of truth for team color across wool and dyed leather armor. */
	public DyeColor dyeColor() {
		return dyeColor;
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
