package br.com.tavares.bedfight.arena;

public final class MapSpawnPoint {
	public double x;
	public double y;
	public double z;
	public float yaw;
	public float pitch;

	public MapSpawnPoint() {
	}

	public MapSpawnPoint(double x, double y, double z, float yaw, float pitch) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.yaw = yaw;
		this.pitch = pitch;
	}
}
