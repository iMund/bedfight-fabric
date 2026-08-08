package br.com.tavares.bedfight;

import br.com.tavares.bedfight.arena.ArenaBlockProtection;
import br.com.tavares.bedfight.arena.ArenaInstancePool;
import br.com.tavares.bedfight.arena.MapSelectionManager;
import br.com.tavares.bedfight.config.ArenaConfig;
import br.com.tavares.bedfight.config.KitConfig;
import br.com.tavares.bedfight.config.MatchConfig;
import br.com.tavares.bedfight.match.MatchManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BedFight implements ModInitializer {
	public static final String MOD_ID = "bedfight";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ArenaConfig.load();
		KitConfig.load();
		MatchConfig.load();
		ArenaInstancePool.init();

		MapSelectionManager.register();
		ArenaBlockProtection.register();
		MatchManager.register();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			BedFightCommands.register(dispatcher));

		LOGGER.info("Bed Fight loaded.");
	}
}
