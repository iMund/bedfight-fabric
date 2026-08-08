package br.com.tavares.bedfight.arena;

public final class MapSpawnPoint {
	public double x;
	public double y;
	public double z;
	public float yaw;

	public MapSpawnPoint() {
	}

	public MapSpawnPoint(double x, double y, double z, float yaw) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.yaw = yaw;
	}
}
