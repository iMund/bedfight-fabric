package br.com.tavares.bedfight.config;

import java.util.ArrayList;
import java.util.List;

public final class KitConfig {
	private static KitConfig instance = new KitConfig();

	public List<KitItem> items = new ArrayList<>();

	public static KitConfig get() {
		return instance;
	}

	public static void load() {
		instance = YamlConfigLoader.load("kit.yml", KitConfig.class, new KitConfig());
	}
}
