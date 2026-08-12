package br.com.tavares.bedfight.arena;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Dimension keys for the mod's own datapack-registered void dimensions - one per arena instance (see ArenaInstancePool) plus a dedicated one for admin map building. */
public final class ArenaDimension {
	/** For admin map authoring (wand selection, setspawn, capturar, buildzone) - never used by a live match. */
	public static final ResourceKey<Level> BUILD = key("build");

	private ArenaDimension() {
	}

	public static ResourceKey<Level> arenaKey(int index) {
		return key("arena_" + index);
	}

	public static ServerLevel get(MinecraftServer server, ResourceKey<Level> key) {
		return server.getLevel(key);
	}

	private static ResourceKey<Level> key(String path) {
		return ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("bedfight", path));
	}
}
