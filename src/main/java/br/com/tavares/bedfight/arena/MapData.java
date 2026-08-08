package br.com.tavares.bedfight.arena;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MapData {
	public int sizeX;
	public int sizeY;
	public int sizeZ;

	/** Spawn offsets relative to the structure's origin (the selection's min corner), keyed by team ("azul"/"vermelho"). */
	public Map<String, MapSpawnPoint> spawns = new LinkedHashMap<>();
}
