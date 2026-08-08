package br.com.tavares.bedfight.arena;

import br.com.tavares.bedfight.config.YamlConfigLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class MapRegistry {
	private MapRegistry() {
	}

	/** Ids of every map that has a captured structure.nbt on disk, sorted alphabetically. */
	public static List<String> listMapIds() {
		Path mapsDir = YamlConfigLoader.configDir().resolve("maps");
		if (!Files.isDirectory(mapsDir)) {
			return List.of();
		}
		List<String> ids = new ArrayList<>();
		try (Stream<Path> entries = Files.list(mapsDir)) {
			entries.filter(Files::isDirectory)
				.filter(dir -> Files.exists(dir.resolve("structure.nbt")))
				.forEach(dir -> ids.add(dir.getFileName().toString()));
		} catch (IOException exception) {
			return List.of();
		}
		ids.sort(String::compareTo);
		return ids;
	}
}
