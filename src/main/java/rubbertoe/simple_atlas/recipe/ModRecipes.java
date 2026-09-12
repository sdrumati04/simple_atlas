package rubbertoe.simple_atlas.recipe;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeSerializer;
import rubbertoe.simple_atlas.SimpleAtlas;

public final class ModRecipes {
    public static final RecipeSerializer<AtlasAddRecipe> ATLAS_ADD = Registry.register(
            BuiltInRegistries.RECIPE_SERIALIZER,
            Identifier.fromNamespaceAndPath(SimpleAtlas.MOD_ID, "crafting_special_atlas_add"),
            AtlasAddRecipe.SERIALIZER
    );

    private ModRecipes() {}

    public static void initialize() {
        // Triggers static initialization
    }
}
