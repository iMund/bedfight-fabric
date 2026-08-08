package br.com.tavares.bedfight.config;

import br.com.tavares.bedfight.BedFight;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.introspector.PropertyUtils;
import org.yaml.snakeyaml.representer.Representer;

/**
 * Copies packaged default YAML files (with their hand-written comments) to the config
 * folder the first time they're needed, then reads whatever is on disk from then on -
 * an existing file is never overwritten by the mod.
 */
public final class YamlConfigLoader {
	private static final String DEFAULTS_RESOURCE_PREFIX = "defaults/bedfight/";

	private YamlConfigLoader() {
	}

	public static <T> T load(String fileName, Class<T> type, T fallback) {
		Path file = configDir().resolve(fileName);
		try {
			Files.createDirectories(file.getParent());
			if (!Files.exists(file)) {
				copyDefault(fileName, file);
			}
		} catch (IOException exception) {
			BedFight.LOGGER.error("Falha ao preparar config {}, usando valores padrao.", fileName, exception);
			return fallback;
		}
		return readIfExists(file, type, fallback);
	}

	/** Reads admin-authored data (e.g. a map's spawn points) if the file exists; falls back otherwise. Never copies a default. */
	public static <T> T readIfExists(Path file, Class<T> type, T fallback) {
		if (!Files.exists(file)) {
			return fallback;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			T loaded = newYaml(type).load(reader);
			return loaded != null ? loaded : fallback;
		} catch (IOException | RuntimeException exception) {
			BedFight.LOGGER.error("Falha ao carregar config {}, usando valores padrao.", file, exception);
			return fallback;
		}
	}

	public static Path configDir() {
		return FabricLoader.getInstance().getConfigDir().resolve("bedfight");
	}

	/** Writes admin-authored data (e.g. a captured map's spawn points) as YAML - always overwrites. */
	public static void save(Path file, Object data) {
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				newDumper().dump(data, writer);
			}
		} catch (IOException | RuntimeException exception) {
			BedFight.LOGGER.error("Falha ao salvar config {}.", file, exception);
		}
	}

	private static <T> Yaml newYaml(Class<T> type) {
		Constructor constructor = new Constructor(type, new LoaderOptions());
		PropertyUtils propertyUtils = new PropertyUtils();
		propertyUtils.setBeanAccess(BeanAccess.FIELD);
		propertyUtils.setSkipMissingProperties(true);
		constructor.setPropertyUtils(propertyUtils);
		return new Yaml(constructor);
	}

	private static Yaml newDumper() {
		DumperOptions options = new DumperOptions();
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		options.setPrettyFlow(true);
		Representer representer = new Representer(options);
		PropertyUtils propertyUtils = new PropertyUtils();
		propertyUtils.setBeanAccess(BeanAccess.FIELD);
		representer.setPropertyUtils(propertyUtils);
		return new Yaml(representer, options);
	}

	private static void copyDefault(String fileName, Path target) throws IOException {
		String resourcePath = DEFAULTS_RESOURCE_PREFIX + fileName;
		try (InputStream stream = YamlConfigLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
			if (stream == null) {
				throw new IOException("Default de config nao encontrado no jar: " + resourcePath);
			}
			Files.copy(stream, target);
		}
	}
}
