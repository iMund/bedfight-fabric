package br.com.tavares.bedfight;

import br.com.tavares.bedfight.config.ArenaConfig;
import br.com.tavares.bedfight.config.KitConfig;
import br.com.tavares.bedfight.config.MatchConfig;
import net.fabricmc.api.ModInitializer;
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

		LOGGER.info("Bed Fight loaded.");
	}
}
