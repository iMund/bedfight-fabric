package br.com.tavares.bedfight.arena;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class ArenaDimension {
	public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("bedfight", "arena"));

	private ArenaDimension() {
	}

	public static ServerLevel get(MinecraftServer server) {
		return server.getLevel(KEY);
	}
}
