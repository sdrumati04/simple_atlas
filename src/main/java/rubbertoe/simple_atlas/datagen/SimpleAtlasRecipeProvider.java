package rubbertoe.simple_atlas.datagen;

import java.util.concurrent.CompletableFuture;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.SpecialRecipeBuilder;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NonNull;
import rubbertoe.simple_atlas.item.ModItems;

public class SimpleAtlasRecipeProvider extends FabricRecipeProvider {
    public SimpleAtlasRecipeProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected @NonNull RecipeProvider createRecipeProvider(HolderLookup.@NonNull Provider registryLookup, @NonNull RecipeOutput exporter) {
        return new RecipeProvider(registryLookup, exporter) {
            @Override
            public void buildRecipes() {
                shapeless(RecipeCategory.TOOLS, ModItems.ATLAS)
                        .requires(Items.BOOK)
                        .requires(Items.FEATHER)
                        .requires(Items.COMPASS)
                        .requires(Items.MAP)
                        .unlockedBy(getHasName(Items.MAP), has(Items.MAP))
                        .save(output);

                SpecialRecipeBuilder.special(rubbertoe.simple_atlas.recipe.AtlasAddRecipe::new).save(output, "atlas_add");
            }
        };
    }

    @Override
    public @NonNull String getName() {
        return "Simple Atlas Recipes";
    }
}