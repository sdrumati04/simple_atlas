package rubbertoe.simple_atlas.cartography;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import rubbertoe.simple_atlas.advancement.AtlasCartographyActionTrigger;
import rubbertoe.simple_atlas.advancement.ModCriteria;
import rubbertoe.simple_atlas.compat.MapModCompat;
import rubbertoe.simple_atlas.component.AtlasContents;
import rubbertoe.simple_atlas.component.ModComponents;
import rubbertoe.simple_atlas.item.ModItems;

public final class AtlasCartographyTakeHandler {
    private AtlasCartographyTakeHandler() {}

    public record TakeContext(
            boolean isBookDuplication,
            ItemStack duplicationAtlasTemplate,
            boolean isAtlasScale,
            ItemStack scaleAtlasTemplate,
            boolean isAtlasDownscale,
            ItemStack downscaleAtlasTemplate,
            ItemStack shearsTemplate,
            int shearsSlotIndex,
            boolean isEmptyMapAdd,
            int emptyMapSlotIndex,
            int emptyMapCountToConsume,
            AtlasCartographyActionTrigger.Action cartographyAction
    ) {}

    public static TakeContext captureContext(Container container) {
        ItemStack slot0 = container.getItem(0);
        ItemStack slot1 = container.getItem(1);

        boolean bookDuplication = (slot0.is(Items.BOOK) && slot1.is(ModItems.ATLAS))
                || (slot0.is(ModItems.ATLAS) && slot1.is(Items.BOOK));
        boolean atlasScale = (slot0.is(ModItems.ATLAS) && slot1.is(Items.PAPER))
                || (slot0.is(Items.PAPER) && slot1.is(ModItems.ATLAS));
        boolean atlasDownscale = (slot0.is(ModItems.ATLAS) && slot1.is(Items.SHEARS))
                || (slot0.is(Items.SHEARS) && slot1.is(ModItems.ATLAS));
        boolean emptyMapAdd = (MapModCompat.isEmptyMap(slot0) && slot1.is(ModItems.ATLAS))
                || (slot0.is(ModItems.ATLAS) && MapModCompat.isEmptyMap(slot1));

        int emptyMapSlotIndex = -1;
        int emptyMapCountToConsume = 0;
        if (emptyMapAdd) {
            emptyMapSlotIndex = MapModCompat.isEmptyMap(slot0) ? 0 : 1;
            emptyMapCountToConsume = container.getItem(emptyMapSlotIndex).getCount();
        }

        ItemStack duplicationAtlasTemplate = bookDuplication
                ? (slot0.is(ModItems.ATLAS) ? slot0.copyWithCount(1) : slot1.copyWithCount(1))
                : ItemStack.EMPTY;
        ItemStack scaleAtlasTemplate = atlasScale
                ? (slot0.is(ModItems.ATLAS) ? slot0.copyWithCount(1) : slot1.copyWithCount(1))
                : ItemStack.EMPTY;
        ItemStack downscaleAtlasTemplate = atlasDownscale
                ? (slot0.is(ModItems.ATLAS) ? slot0.copyWithCount(1) : slot1.copyWithCount(1))
                : ItemStack.EMPTY;

        ItemStack shearsTemplate = ItemStack.EMPTY;
        int shearsSlotIndex = -1;
        if (atlasDownscale) {
            shearsSlotIndex = slot0.is(Items.SHEARS) ? 0 : 1;
            shearsTemplate = container.getItem(shearsSlotIndex).copy();
        }

        AtlasCartographyActionTrigger.Action action = bookDuplication
                ? AtlasCartographyActionTrigger.Action.DUPLICATE
                : (atlasScale || atlasDownscale)
                ? AtlasCartographyActionTrigger.Action.SCALE
                : (slot0.is(ModItems.ATLAS) && slot1.is(ModItems.ATLAS))
                ? AtlasCartographyActionTrigger.Action.MERGE
                : null;

        return new TakeContext(
                bookDuplication,
                duplicationAtlasTemplate,
                atlasScale,
                scaleAtlasTemplate,
                atlasDownscale,
                downscaleAtlasTemplate,
                shearsTemplate,
                shearsSlotIndex,
                emptyMapAdd,
                emptyMapSlotIndex,
                emptyMapCountToConsume,
                action
        );
    }

    public static void scaleOrDownscaleAtlas(ServerLevel level, ItemStack slot0, ItemStack slot1, ItemStack atlasResult) {
        if (!atlasResult.is(ModItems.ATLAS)) {
            return;
        }

        boolean isScale = (slot0.is(ModItems.ATLAS) && slot1.is(Items.PAPER))
                || (slot0.is(Items.PAPER) && slot1.is(ModItems.ATLAS));
        if (isScale) {
            ItemStack atlasInput = slot0.is(ModItems.ATLAS) ? slot0 : slot1;
            AtlasContents original = atlasInput.getOrDefault(ModComponents.ATLAS_CONTENTS, AtlasContents.EMPTY);
            AtlasContents scaled = AtlasCartographyScaler.scaleAtlas(level, original);
            if (scaled != null) {
                atlasResult.set(ModComponents.ATLAS_CONTENTS, scaled);
            }
            return;
        }

        boolean isDownscale = (slot0.is(ModItems.ATLAS) && slot1.is(Items.SHEARS))
                || (slot0.is(Items.SHEARS) && slot1.is(ModItems.ATLAS));
        if (isDownscale) {
            ItemStack atlasInput = slot0.is(ModItems.ATLAS) ? slot0 : slot1;
            AtlasContents original = atlasInput.getOrDefault(ModComponents.ATLAS_CONTENTS, AtlasContents.EMPTY);
            AtlasContents downscaled = AtlasCartographyScaler.downscaleAtlas(level, original);
            if (downscaled != null) {
                atlasResult.set(ModComponents.ATLAS_CONTENTS, downscaled);
            }
        }
    }

    public static void applyPostTakeEffects(TakeContext context, Container container, Player player, ItemStack carried) {
        if (context == null) {
            return;
        }

        // 1. If taking via cursor (carried is non-empty atlas), execute scaling if needed
        if (carried.is(ModItems.ATLAS) && player instanceof ServerPlayer serverPlayer) {
            if (context.isAtlasScale() && !context.scaleAtlasTemplate().isEmpty()) {
                AtlasContents original = context.scaleAtlasTemplate().getOrDefault(ModComponents.ATLAS_CONTENTS, AtlasContents.EMPTY);
                AtlasContents scaled = AtlasCartographyScaler.scaleAtlas(serverPlayer.level(), original);
                if (scaled != null) {
                    carried.set(ModComponents.ATLAS_CONTENTS, scaled);
                }
            } else if (context.isAtlasDownscale() && !context.downscaleAtlasTemplate().isEmpty()) {
                AtlasContents original = context.downscaleAtlasTemplate().getOrDefault(ModComponents.ATLAS_CONTENTS, AtlasContents.EMPTY);
                AtlasContents downscaled = AtlasCartographyScaler.downscaleAtlas(serverPlayer.level(), original);
                if (downscaled != null) {
                    carried.set(ModComponents.ATLAS_CONTENTS, downscaled);
                }
            }
        }

        // 2. Book duplication: give second atlas copy
        if (context.isBookDuplication() && !context.duplicationAtlasTemplate().isEmpty()) {
            ItemStack extraAtlas = context.duplicationAtlasTemplate().copy();
            if (!player.getInventory().add(extraAtlas)) {
                player.drop(extraAtlas, false);
            }
        }

        // 3. Empty map consumption: consume remaining stack of empty maps
        if (context.isEmptyMapAdd() && context.emptyMapSlotIndex() >= 0 && context.emptyMapCountToConsume() > 1) {
            container.removeItem(context.emptyMapSlotIndex(), context.emptyMapCountToConsume() - 1);
        }

        // 4. Shears durability damage and preservation:
        if (context.isAtlasDownscale() && !context.shearsTemplate().isEmpty()) {
            ItemStack shears = context.shearsTemplate();
            int newDamage = shears.getDamageValue() + 1;
            if (newDamage < shears.getMaxDamage()) {
                shears.setDamageValue(newDamage);
                container.setItem(context.shearsSlotIndex(), shears);
            } else {
                player.level().playSound(
                        null,
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        SoundEvents.ITEM_BREAK,
                        SoundSource.PLAYERS,
                        0.8F,
                        0.8F + player.level().getRandom().nextFloat() * 0.4F
                );
            }
        }

        // 5. Trigger cartography criteria
        if (player instanceof ServerPlayer serverPlayer && context.cartographyAction() != null) {
            ModCriteria.ATLAS_CARTOGRAPHY_ACTION.trigger(serverPlayer, context.cartographyAction());
        }
    }
}
