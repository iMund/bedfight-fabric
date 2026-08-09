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

	/** Only removes the player if they're actually still on bedfight_hidden - the 2-arg removePlayerFromTeam throws IllegalStateException otherwise (e.g. activateMatch already revealed them, or this player was never in a match at all). */
	static void revealName(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		ServerScoreboard scoreboard = server.getScoreboard();
		PlayerTeam team = scoreboard.getPlayerTeam(TEAM_NAME);
		if (team != null && scoreboard.getPlayersTeam(player.getScoreboardName()) == team) {
			scoreboard.removePlayerFromTeam(player.getScoreboardName(), team);
		}
	}

	/** The team itself lives in the world's saved scoreboard data, not just memory - if the server stops mid-COUNTDOWN, a player could stay stuck hidden forever otherwise, since nothing would ever call revealName for them again. Wiping the team on shutdown guarantees a clean slate next boot. */
	static void clearAll(MinecraftServer server) {
		ServerScoreboard scoreboard = server.getScoreboard();
		PlayerTeam team = scoreboard.getPlayerTeam(TEAM_NAME);
		if (team != null) {
			scoreboard.removePlayerTeam(team);
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
