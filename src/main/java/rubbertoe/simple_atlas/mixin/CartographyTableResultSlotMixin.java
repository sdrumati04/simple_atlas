package rubbertoe.simple_atlas.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rubbertoe.simple_atlas.advancement.AtlasCartographyActionTrigger;
import rubbertoe.simple_atlas.advancement.ModCriteria;
import rubbertoe.simple_atlas.cartography.AtlasCartographyScaler;
import rubbertoe.simple_atlas.component.AtlasContents;
import rubbertoe.simple_atlas.component.ModComponents;
import rubbertoe.simple_atlas.item.ModItems;

/**
 * Extends the result-slot take behavior for atlas + book duplication so the
 * player receives the second atlas copy after taking the crafted result.
 */
@Mixin(targets = "net.minecraft.world.inventory.CartographyTableMenu$5")
public abstract class CartographyTableResultSlotMixin {

    /** Outer {@link CartographyTableMenu} instance (synthetic {@code this$0}). */
    @Shadow(aliases = "this$0")
    private CartographyTableMenu simple_atlas$outerMenu;

    @Unique
    private boolean simple_atlas$bookDuplicationTake;

    @Unique
    private ItemStack simple_atlas$duplicationAtlasTemplate = ItemStack.EMPTY;

    @Unique
    private boolean simple_atlas$atlasScaleTake;

    @Unique
    private ItemStack simple_atlas$scaleAtlasTemplate = ItemStack.EMPTY;

    @Unique
    private AtlasCartographyActionTrigger.Action simple_atlas$cartographyAction;

    @Inject(method = "onTake", at = @At("HEAD"))
    private void simple_atlas$captureRecipeType(Player player, ItemStack carried, CallbackInfo ci) {
        ItemStack slot0 = simple_atlas$outerMenu.container.getItem(0);
        ItemStack slot1 = simple_atlas$outerMenu.container.getItem(1);

        simple_atlas$bookDuplicationTake = (slot0.is(Items.BOOK) && slot1.is(ModItems.ATLAS))
                || (slot0.is(ModItems.ATLAS) && slot1.is(Items.BOOK));
        simple_atlas$atlasScaleTake = (slot0.is(ModItems.ATLAS) && slot1.is(Items.PAPER))
                || (slot0.is(Items.PAPER) && slot1.is(ModItems.ATLAS));

        simple_atlas$duplicationAtlasTemplate = simple_atlas$bookDuplicationTake
                ? (slot0.is(ModItems.ATLAS) ? slot0.copyWithCount(1) : slot1.copyWithCount(1))
                : ItemStack.EMPTY;
        simple_atlas$scaleAtlasTemplate = simple_atlas$atlasScaleTake
                ? (slot0.is(ModItems.ATLAS) ? slot0.copyWithCount(1) : slot1.copyWithCount(1))
                : ItemStack.EMPTY;
        simple_atlas$cartographyAction = simple_atlas$bookDuplicationTake
                ? AtlasCartographyActionTrigger.Action.DUPLICATE
                : simple_atlas$atlasScaleTake
                ? AtlasCartographyActionTrigger.Action.SCALE
                : (slot0.is(ModItems.ATLAS) && slot1.is(ModItems.ATLAS))
                ? AtlasCartographyActionTrigger.Action.MERGE
                : null;
    }

    @Inject(method = "onTake", at = @At("TAIL"))
    private void simple_atlas$handleAtlasRecipes(Player player, ItemStack carried, CallbackInfo ci) {
        if (simple_atlas$bookDuplicationTake && !simple_atlas$duplicationAtlasTemplate.isEmpty()) {
            ItemStack extraAtlas = simple_atlas$duplicationAtlasTemplate.copy();
            if (!player.getInventory().add(extraAtlas)) {
                player.drop(extraAtlas, false);
            }
        }

        // When manually dragging the atlas from result slot (cursor pickup)
        if (carried.is(ModItems.ATLAS) && player instanceof ServerPlayer serverPlayer) {
            if (simple_atlas$atlasScaleTake && !simple_atlas$scaleAtlasTemplate.isEmpty()) {
                AtlasContents original = simple_atlas$scaleAtlasTemplate.getOrDefault(ModComponents.ATLAS_CONTENTS, AtlasContents.EMPTY);
                AtlasContents scaled = AtlasCartographyScaler.scaleAtlas(serverPlayer.level(), original);
                if (scaled != null) {
                    carried.set(ModComponents.ATLAS_CONTENTS, scaled);
                }
            }
        }

        if (player instanceof ServerPlayer serverPlayer && simple_atlas$cartographyAction != null) {
            ModCriteria.ATLAS_CARTOGRAPHY_ACTION.trigger(serverPlayer, simple_atlas$cartographyAction);
        }

        simple_atlas$bookDuplicationTake = false;
        simple_atlas$duplicationAtlasTemplate = ItemStack.EMPTY;
        simple_atlas$atlasScaleTake = false;
        simple_atlas$scaleAtlasTemplate = ItemStack.EMPTY;
        simple_atlas$cartographyAction = null;
    }
}
