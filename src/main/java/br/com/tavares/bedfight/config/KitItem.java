package br.com.tavares.bedfight.config;

import java.util.LinkedHashMap;
import java.util.Map;

public final class KitItem {
	public String item = "minecraft:air";
	public int count = 1;
	public Map<String, Integer> enchantments = new LinkedHashMap<>();
}
