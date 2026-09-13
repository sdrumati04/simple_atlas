package rubbertoe.simple_atlas.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

public class SimpleAtlasDataGenerator implements DataGeneratorEntrypoint {
	@Override
	public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
		FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();

		pack.addProvider(SimpleAtlasModelProvider::new);
		pack.addProvider(SimpleAtlasLangProvider::new);
		pack.addProvider(SimpleAtlasItalianLangProvider::new);
		pack.addProvider(SimpleAtlasRecipeProvider::new);
		pack.addProvider(SimpleAtlasAdvancementProvider::new);
	}
}
