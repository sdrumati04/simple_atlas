package rubbertoe.simple_atlas;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rubbertoe.simple_atlas.advancement.ModCriteria;
import rubbertoe.simple_atlas.component.ModComponents;
import rubbertoe.simple_atlas.config.SimpleAtlasConfigManager;
import rubbertoe.simple_atlas.item.ModItems;
import rubbertoe.simple_atlas.map.ModMapDecorationTypes;
import rubbertoe.simple_atlas.network.ModNetworking;
import rubbertoe.simple_atlas.recipe.ModRecipes;
import rubbertoe.simple_atlas.server.AtlasViewTicker;

public class SimpleAtlas implements ModInitializer {
	public static final String MOD_ID = "simple-atlas";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		SimpleAtlasConfigManager.load();
		ModItems.initialize();
		ModMapDecorationTypes.initialize();
		ModComponents.initialize();
		ModNetworking.initialize();
		ModCriteria.initialize();
		ModRecipes.initialize();
		AtlasViewTicker.initialize();
		LOGGER.info("Simple Atlas initialized.");
	}
}
