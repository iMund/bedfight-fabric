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
import net.minecraft.world.Container;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class MapCaptureService {
	/** Guards against an accidental huge selection stalling/OOM-ing the server thread during fillFromWorld. */
	private static final long MAX_VOLUME = 2_000_000L;

	private MapCaptureService() {
	}

	public static Path mapDir(String mapId) {
		return YamlConfigLoader.configDir().resolve("maps").resolve(mapId);
	}

	public static void capture(String mapId, ServerLevel level, MapSelection selection) throws IOException, MapCaptureException {
		BlockPos min = selection.min();
		BlockPos max = selection.max();
		Vec3i size = new Vec3i(max.getX() - min.getX() + 1, max.getY() - min.getY() + 1, max.getZ() - min.getZ() + 1);
		long volume = (long) size.getX() * size.getY() * size.getZ();
		if (volume > MAX_VOLUME) {
			throw new MapCaptureException("Selecao grande demais (" + volume + " blocos, limite " + MAX_VOLUME + "). Confira os cantos com a varinha antes de capturar.");
		}
		BlockPos container = findContainer(level, min, max);
		if (container != null) {
			throw new MapCaptureException("Tem um container (bau/barril/shulker/etc) em " + container.toShortString()
				+ " dentro da selecao - remove antes de capturar, senao o conteudo fica acessivel na arena.");
		}

		MapData data = loadOrCreate(mapId);
		bindOrigin(data, selection);

		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, min, size, false, List.of());
		CompoundTag tag = template.save(new CompoundTag());

		Path dir = mapDir(mapId);
		Files.createDirectories(dir);
		NbtIo.writeCompressed(tag, dir.resolve("structure.nbt"));

		data.sizeX = size.getX();
		data.sizeY = size.getY();
		data.sizeZ = size.getZ();
		YamlConfigLoader.save(dir.resolve("map.yml"), data);
	}

	public static void setSpawn(String mapId, Team team, MapSelection selection, BlockPos playerPos, float yaw, float pitch) throws MapCaptureException {
		MapData data = loadOrCreate(mapId);
		bindOrigin(data, selection);

		BlockPos min = selection.min();
		data.spawns.put(team.id(), new MapSpawnPoint(
			playerPos.getX() - min.getX() + 0.5,
			playerPos.getY() - min.getY(),
			playerPos.getZ() - min.getZ() + 0.5,
			yaw,
			pitch));
		YamlConfigLoader.save(mapDir(mapId).resolve("map.yml"), data);
	}

	/**
	 * The map's structure and every team's spawn offset must all be relative to the same origin
	 * (dimension + min corner). Binds it on the first call for a fresh map id, otherwise rejects a
	 * selection that doesn't match what's already recorded - silently allowing a drifted selection
	 * would misplace spawns relative to the pasted structure with no visible symptom.
	 */
	private static void bindOrigin(MapData data, MapSelection selection) throws MapCaptureException {
		BlockPos min = selection.min();
		String dimension = selection.dimension.identifier().toString();
		if (!data.hasOrigin()) {
			data.originDimension = dimension;
			data.originX = min.getX();
			data.originY = min.getY();
			data.originZ = min.getZ();
			return;
		}
		if (!data.originDimension.equals(dimension) || data.originX != min.getX() || data.originY != min.getY() || data.originZ != min.getZ()) {
			throw new MapCaptureException("Essa selecao nao bate com a origem ja gravada pra esse mapa (dimensao ou canto minimo diferente). "
				+ "Refaca a selecao com a varinha do zero, do mesmo jeito de antes, ou use um mapId novo.");
		}
	}

	private static BlockPos findContainer(ServerLevel level, BlockPos min, BlockPos max) {
		for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
			if (level.getBlockEntity(pos) instanceof Container) {
				return pos.immutable();
			}
		}
		return null;
	}

	private static MapData loadOrCreate(String mapId) {
		Path file = mapDir(mapId).resolve("map.yml");
		return YamlConfigLoader.readIfExists(file, MapData.class, new MapData());
	}
}
