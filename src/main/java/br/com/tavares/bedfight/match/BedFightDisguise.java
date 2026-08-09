package br.com.tavares.bedfight.match;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;

/** Hides real nametags while queued in a match's waiting room, before the fight starts, so opponents can't be scouted ahead of time. Real names are restored once the match goes ACTIVE. */
final class BedFightDisguise {
	private static final String TEAM_NAME = "bedfight_hidden";

	private BedFightDisguise() {
	}

	static void hideName(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		PlayerTeam team = hiddenTeam(server);
		server.getScoreboard().addPlayerToTeam(player.getScoreboardName(), team);
	}

	static void revealName(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		ServerScoreboard scoreboard = server.getScoreboard();
		PlayerTeam team = scoreboard.getPlayerTeam(TEAM_NAME);
		if (team != null) {
			scoreboard.removePlayerFromTeam(player.getScoreboardName(), team);
		}
	}

	private static PlayerTeam hiddenTeam(MinecraftServer server) {
		ServerScoreboard scoreboard = server.getScoreboard();
		PlayerTeam team = scoreboard.getPlayerTeam(TEAM_NAME);
		if (team == null) {
			team = scoreboard.addPlayerTeam(TEAM_NAME);
			team.setNameTagVisibility(Team.Visibility.NEVER);
		}
		return team;
	}
}
