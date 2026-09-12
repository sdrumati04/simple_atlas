package rubbertoe.simple_atlas.recipe;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.jspecify.annotations.NonNull;
import rubbertoe.simple_atlas.compat.MapModCompat;
import rubbertoe.simple_atlas.component.AtlasContents;
import rubbertoe.simple_atlas.component.ModComponents;
import rubbertoe.simple_atlas.item.ModItems;

import java.util.ArrayList;
import java.util.List;

public class AtlasAddRecipe extends CustomRecipe {
    public static final MapCodec<AtlasAddRecipe> CODEC = MapCodec.unit(new AtlasAddRecipe());
    public static final StreamCodec<RegistryFriendlyByteBuf, AtlasAddRecipe> STREAM_CODEC = StreamCodec.unit(new AtlasAddRecipe());
    public static final RecipeSerializer<AtlasAddRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);

    public AtlasAddRecipe() {
        super();
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack atlasStack = ItemStack.EMPTY;
        int emptyMapSlots = 0;
        List<MapId> filledMapIds = new ArrayList<>();

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }

            if (stack.is(ModItems.ATLAS)) {
                if (!atlasStack.isEmpty()) {
                    return false; // Only 1 atlas allowed
                }
                atlasStack = stack;
            } else if (MapModCompat.isEmptyMap(stack)) {
                emptyMapSlots++;
            } else if (stack.is(Items.FILLED_MAP)) {
                MapId mapId = stack.get(DataComponents.MAP_ID);
                if (mapId == null) {
                    return false;
                }
                filledMapIds.add(mapId);
            } else {
                return false; // Unknown item in grid
            }
        }

        if (atlasStack.isEmpty()) {
            return false;
        }

        if (emptyMapSlots == 0 && filledMapIds.isEmpty()) {
            return false; // Need at least 1 map to add
        }

        AtlasContents contents = atlasStack.getOrDefault(ModComponents.ATLAS_CONTENTS, AtlasContents.EMPTY);

        if (!filledMapIds.isEmpty()) {
            if (!contents.canAddMapCount(filledMapIds.size())) {
                return false;
            }
            for (MapId id : filledMapIds) {
                if (contents.contains(id.id())) {
                    return false; // Deduplicate
                }
            }
        }

        return true;
    }

    @Override
    public @NonNull ItemStack assemble(CraftingInput input) {
        ItemStack atlasStack = ItemStack.EMPTY;
        int emptyMapsCount = 0;
        List<MapId> filledMapIds = new ArrayList<>();

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }

            if (stack.is(ModItems.ATLAS)) {
                atlasStack = stack;
            } else if (MapModCompat.isEmptyMap(stack)) {
                emptyMapsCount += stack.getCount();
            } else if (stack.is(Items.FILLED_MAP)) {
                MapId mapId = stack.get(DataComponents.MAP_ID);
                if (mapId != null) {
                    filledMapIds.add(mapId);
                }
            }
        }

        if (atlasStack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack result = atlasStack.copyWithCount(1);
        AtlasContents contents = result.getOrDefault(ModComponents.ATLAS_CONTENTS, AtlasContents.EMPTY);

        AtlasContents updated = contents.withAddedBlankMaps(emptyMapsCount);
        for (MapId id : filledMapIds) {
            updated = updated.withAdded(id.id());
        }

        result.set(ModComponents.ATLAS_CONTENTS, updated);
        return result;
    }

    @Override
    public @NonNull RecipeSerializer<AtlasAddRecipe> getSerializer() {
        return ModRecipes.ATLAS_ADD;
    }
}
