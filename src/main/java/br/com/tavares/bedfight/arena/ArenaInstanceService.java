package br.com.tavares.bedfight.arena;

import br.com.tavares.bedfight.config.YamlConfigLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
	private ArenaInstanceService() {
	}

	/** Pastes a map's captured structure at the instance's origin, overwriting whatever was there before - this doubles as the reset between matches. */
	public static void paste(ServerLevel arenaLevel, ArenaInstance instance, String mapId) throws IOException {
		Path structureFile = MapCaptureService.mapDir(mapId).resolve("structure.nbt");
		if (!Files.exists(structureFile)) {
			throw new IOException("Mapa " + mapId + " nao tem structure.nbt capturado.");
		}
		CompoundTag tag = NbtIo.readCompressed(structureFile, NbtAccounter.unlimitedHeap());
		StructureTemplate template = new StructureTemplate();
		template.load(arenaLevel.registryAccess().lookupOrThrow(Registries.BLOCK), tag);

		StructurePlaceSettings settings = new StructurePlaceSettings();
		template.placeInWorld(arenaLevel, instance.origin(), instance.origin(), settings, arenaLevel.getRandom(), Block.UPDATE_ALL);

		Vec3i size = template.getSize();
		Set<BlockPos> protectedBlocks = BedProtectionScanner.scan(arenaLevel, instance.origin(), size);
		instance.occupy(mapId, protectedBlocks);
	}

	public static void free(ArenaInstance instance) {
		instance.free();
	}

	/** World-space coordinates for a team's spawn in this instance, or the instance origin if the map has no spawn recorded for that team. */
	public static ArenaSpawn teamSpawn(ArenaInstance instance, String mapId, Team team) {
		Path file = MapCaptureService.mapDir(mapId).resolve("map.yml");
		MapData data = YamlConfigLoader.readIfExists(file, MapData.class, new MapData());
		MapSpawnPoint spawn = data.spawns.get(team.id());
		BlockPos origin = instance.origin();
		if (spawn == null) {
			return new ArenaSpawn(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0f, 0f);
		}
		return new ArenaSpawn(origin.getX() + spawn.x, origin.getY() + spawn.y, origin.getZ() + spawn.z, spawn.yaw, spawn.pitch);
	}
}
