package br.com.tavares.bedfight.arena;

import br.com.tavares.bedfight.BedFight;
import br.com.tavares.bedfight.config.ArenaConfig;
import br.com.tavares.bedfight.config.YamlConfigLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class ArenaInstanceService {
	/** Skip neighbor updates (avoids a lag spike on a bulk paste) and don't pop attached blocks as item drops. */
	private static final int PASTE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;
	private static final int ARENA_HEIGHT = 256;

	private ArenaInstanceService() {
	}

	/** Pastes a map's captured structure at the instance's origin, overwriting whatever was there before - this doubles as the reset between matches. */
	public static BedProtectionScanner.Result paste(ServerLevel arenaLevel, ArenaInstance instance, String mapId) throws IOException, MapCaptureException {
		Path structureFile = MapCaptureService.mapDir(mapId).resolve("structure.nbt");
		if (!Files.exists(structureFile)) {
			throw new IOException("Mapa " + mapId + " nao tem structure.nbt capturado.");
		}
		CompoundTag tag = NbtIo.readCompressed(structureFile, NbtAccounter.create(2_000_000_000L));
		StructureTemplate template = new StructureTemplate();
		template.load(arenaLevel.registryAccess().lookupOrThrow(Registries.BLOCK), tag);

		Vec3i size = template.getSize();
		int gridSpacing = ArenaConfig.get().gridSpacingBlocks;
		if (size.getX() > gridSpacing || size.getZ() > gridSpacing) {
			throw new MapCaptureException("Mapa " + mapId + " (" + size.getX() + "x" + size.getZ()
				+ ") e maior que o espacamento do grid (" + gridSpacing + "), instancias vizinhas se sobrepoem.");
		}
		if (instance.origin().getY() + size.getY() > ARENA_HEIGHT) {
			throw new MapCaptureException("Mapa " + mapId + " e alto demais (" + size.getY() + " blocos) pra dimensao da arena.");
		}

		StructurePlaceSettings settings = new StructurePlaceSettings().setKnownShape(true);
		template.placeInWorld(arenaLevel, instance.origin(), instance.origin(), settings, arenaLevel.getRandom(), PASTE_FLAGS);

		BedProtectionScanner.Result scan = BedProtectionScanner.scan(arenaLevel, instance.origin(), size);
		instance.occupy(mapId, scan.breakableBlocks());
		Map<Team, Set<BlockPos>> teamBeds = assignBedsToTeams(scan.bedPositions(), instance, mapId);
		instance.setTeamBeds(teamBeds);
		for (Team team : Team.values()) {
			if (teamBeds.get(team).isEmpty()) {
				BedFight.LOGGER.warn("Nenhuma cama foi atribuida ao time {} no mapa {} - esse time fica sem cama pra defender/perder, a partida pode ficar impossivel de vencer contra ele.", team.id(), mapId);
			}
		}
		return scan;
	}

	/** Assigns each physical bed (a connected pair of bed blocks) to whichever team's spawn it's closest to. */
	private static Map<Team, Set<BlockPos>> assignBedsToTeams(Set<BlockPos> bedPositions, ArenaInstance instance, String mapId) {
		Map<Team, Set<BlockPos>> teamBeds = new EnumMap<>(Team.class);
		for (Team team : Team.values()) {
			teamBeds.put(team, new HashSet<>());
		}
		Optional<ArenaSpawn> azulSpawn = teamSpawn(instance, mapId, Team.AZUL);
		Optional<ArenaSpawn> vermelhoSpawn = teamSpawn(instance, mapId, Team.VERMELHO);
		for (Set<BlockPos> bedGroup : BedProtectionScanner.groupBeds(bedPositions)) {
			BlockPos anyBlock = bedGroup.iterator().next();
			Team nearest = nearestTeam(anyBlock, azulSpawn, vermelhoSpawn);
			if (nearest != null) {
				teamBeds.get(nearest).addAll(bedGroup);
			}
		}
		return teamBeds;
	}

	private static Team nearestTeam(BlockPos pos, Optional<ArenaSpawn> azulSpawn, Optional<ArenaSpawn> vermelhoSpawn) {
		if (azulSpawn.isEmpty() && vermelhoSpawn.isEmpty()) {
			return null;
		}
		if (azulSpawn.isEmpty()) {
			return Team.VERMELHO;
		}
		if (vermelhoSpawn.isEmpty()) {
			return Team.AZUL;
		}
		double distSqAzul = pos.distSqr(new BlockPos((int) azulSpawn.get().x(), (int) azulSpawn.get().y(), (int) azulSpawn.get().z()));
		double distSqVermelho = pos.distSqr(new BlockPos((int) vermelhoSpawn.get().x(), (int) vermelhoSpawn.get().y(), (int) vermelhoSpawn.get().z()));
		return distSqAzul <= distSqVermelho ? Team.AZUL : Team.VERMELHO;
	}

	public static void free(ArenaInstance instance) {
		instance.free();
	}

	/** World-space coordinates for a team's spawn in this instance - empty if the map has no spawn recorded for that team. */
	public static Optional<ArenaSpawn> teamSpawn(ArenaInstance instance, String mapId, Team team) {
		Path file = MapCaptureService.mapDir(mapId).resolve("map.yml");
		MapData data = YamlConfigLoader.readIfExists(file, MapData.class, new MapData());
		MapSpawnPoint spawn = data.spawns.get(team.id());
		if (spawn == null) {
			return Optional.empty();
		}
		BlockPos origin = instance.origin();
		return Optional.of(new ArenaSpawn(origin.getX() + spawn.x, origin.getY() + spawn.y, origin.getZ() + spawn.z, spawn.yaw, spawn.pitch));
	}
}
