package br.com.tavares.bedfight.arena;

import br.com.tavares.bedfight.config.YamlConfigLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class MapCaptureService {
	private MapCaptureService() {
	}

	public static Path mapDir(String mapId) {
		return YamlConfigLoader.configDir().resolve("maps").resolve(mapId);
	}

	public static void capture(String mapId, ServerLevel level, MapSelection selection) throws IOException {
		BlockPos min = selection.min();
		BlockPos max = selection.max();
		Vec3i size = new Vec3i(max.getX() - min.getX() + 1, max.getY() - min.getY() + 1, max.getZ() - min.getZ() + 1);

		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, min, size, false, List.of());
		CompoundTag tag = template.save(new CompoundTag());

		Path dir = mapDir(mapId);
		Files.createDirectories(dir);
		NbtIo.writeCompressed(tag, dir.resolve("structure.nbt"));

		MapData data = loadOrCreate(mapId);
		data.sizeX = size.getX();
		data.sizeY = size.getY();
		data.sizeZ = size.getZ();
		YamlConfigLoader.save(dir.resolve("map.yml"), data);
	}

	public static void setSpawn(String mapId, String team, MapSelection selection, BlockPos playerPos, float yaw) {
		BlockPos min = selection.min();
		MapData data = loadOrCreate(mapId);
		data.spawns.put(team, new MapSpawnPoint(
			playerPos.getX() - min.getX() + 0.5,
			playerPos.getY() - min.getY(),
			playerPos.getZ() - min.getZ() + 0.5,
			yaw));
		YamlConfigLoader.save(mapDir(mapId).resolve("map.yml"), data);
	}

	private static MapData loadOrCreate(String mapId) {
		Path file = mapDir(mapId).resolve("map.yml");
		return YamlConfigLoader.readIfExists(file, MapData.class, new MapData());
	}
}
