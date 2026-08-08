package br.com.tavares.bedfight.arena;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MapData {
	/** World/dimension and min-corner of the selection this map's spawns and structure were captured from - null until the first setspawn/capturar. */
	public String originDimension;
	public Integer originX;
	public Integer originY;
	public Integer originZ;

	public int sizeX;
	public int sizeY;
	public int sizeZ;

	/** Spawn offsets relative to originX/Y/Z, keyed by team id ("azul"/"vermelho"). */
	public Map<String, MapSpawnPoint> spawns = new LinkedHashMap<>();

	public boolean hasOrigin() {
		return originDimension != null;
	}
}
