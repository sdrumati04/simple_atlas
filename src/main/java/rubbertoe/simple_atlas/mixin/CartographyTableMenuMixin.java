package rubbertoe.simple_atlas.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rubbertoe.simple_atlas.component.AtlasContents;
import rubbertoe.simple_atlas.cartography.AtlasCartographyScaler;
import rubbertoe.simple_atlas.component.ModComponents;
import rubbertoe.simple_atlas.config.SimpleAtlasConfigManager;
import rubbertoe.simple_atlas.item.ModItems;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Mixin(CartographyTableMenu.class)
public abstract class CartographyTableMenuMixin {

    @Shadow @Final private ResultContainer resultContainer;
    @Shadow @Final private ContainerLevelAccess access;

    @Inject(method = "setupResultSlot", at = @At("HEAD"), cancellable = true)
    private void simple_atlas$setupAtlasResult(
            ItemStack mapStack,
            ItemStack additionalStack,
            ItemStack resultStack,
            CallbackInfo ci
    ) {
        // ── Book + atlas → duplicate the atlas (costs 1 book) ────────────────
        boolean isBookAndAtlas = (mapStack.is(Items.BOOK) && additionalStack.is(ModItems.ATLAS))
                || (mapStack.is(ModItems.ATLAS) && additionalStack.is(Items.BOOK));
        if (isBookAndAtlas) {
            ItemStack atlasInput = mapStack.is(ModItems.ATLAS) ? mapStack : additionalStack;
            ItemStack result = atlasInput.copyWithCount(1);

            if (!ItemStack.matches(result, resultStack)) {
                this.resultContainer.setItem(2, result);
                ((CartographyTableMenu) (Object) this).broadcastChanges();
            }
            ci.cancel();
            return;
        }

        // ── Filled map + atlas → add the map to the atlas ────────────────────
        boolean isFilledMapAndAtlas = (mapStack.is(Items.FILLED_MAP) && additionalStack.is(ModItems.ATLAS))
                || (mapStack.is(ModItems.ATLAS) && additionalStack.is(Items.FILLED_MAP));
        if (isFilledMapAndAtlas) {
            ItemStack mapInput = mapStack.is(Items.FILLED_MAP) ? mapStack : additionalStack;
            ItemStack atlasInput = mapStack.is(ModItems.ATLAS) ? mapStack : additionalStack;

            MapId mapId = mapInput.get(DataComponents.MAP_ID);
            if (mapId == null) {
                simple_atlas$rejectAtlasResult();
                ci.cancel();
                return;
            }

            AtlasContents contents = atlasInput.getOrDefault(ModComponents.ATLAS_CONTENTS, AtlasContents.EMPTY);

            this.access.execute((level, _) -> {
                MapItemSavedData newMapData = level.getMapData(mapId);
                if (newMapData == null) {
                    simple_atlas$rejectAtlasResult();
                    return;
                }

                if (!contents.canAddMapId() || contents.contains(mapId.id())) {
                    simple_atlas$rejectAtlasResult();
                    return;
                }

                ItemStack result = atlasInput.copyWithCount(1);
                AtlasContents updatedContents = contents.withAdded(mapId.id());
                if (contents.mapIds().isEmpty() || contents.selectedScale() < 0) {
                    updatedContents = updatedContents.withSelectedScale(newMapData.scale);
                }
                result.set(ModComponents.ATLAS_CONTENTS, updatedContents);

                if (!ItemStack.matches(result, resultStack)) {
                    this.resultContainer.setItem(2, result);
                    ((CartographyTableMenu) (Object) this).broadcastChanges();
                }
            });

            ci.cancel();
            return;
        }

        // ── Atlas + paper → scale atlas maps by +1 (replaces lower-scale maps) ─
        boolean isAtlasAndPaper = (mapStack.is(ModItems.ATLAS) && additionalStack.is(Items.PAPER))
                || (mapStack.is(Items.PAPER) && additionalStack.is(ModItems.ATLAS));
        if (isAtlasAndPaper) {
            ItemStack atlasInput = mapStack.is(ModItems.ATLAS) ? mapStack : additionalStack;
            AtlasContents contents = atlasInput.getOrDefault(ModComponents.ATLAS_CONTENTS, AtlasContents.EMPTY);

            this.access.execute((level, _) -> {
                int targetScale = AtlasCartographyScaler.getTargetUpscale(level, contents);
                if (targetScale < 0) {
                    simple_atlas$rejectAtlasResult();
                    return;
                }

                ItemStack result = atlasInput.copyWithCount(1);
                result.set(ModComponents.ATLAS_CONTENTS, contents.withSelectedScale(targetScale + 1));
                if (!ItemStack.matches(result, resultStack)) {
                    this.resultContainer.setItem(2, result);
                    ((CartographyTableMenu) (Object) this).broadcastChanges();
                }
            });

            ci.cancel();
            return;
        }

        // ── Atlas + shears → downscale atlas maps by -1 ─────────────────────
        boolean isAtlasAndShears = (mapStack.is(ModItems.ATLAS) && additionalStack.is(Items.SHEARS))
                || (mapStack.is(Items.SHEARS) && additionalStack.is(ModItems.ATLAS));
        if (isAtlasAndShears) {
            ItemStack atlasInput = mapStack.is(ModItems.ATLAS) ? mapStack : additionalStack;
            AtlasContents contents = atlasInput.getOrDefault(ModComponents.ATLAS_CONTENTS, AtlasContents.EMPTY);

            this.access.execute((level, _) -> {
                int targetScale = AtlasCartographyScaler.getTargetDownscale(level, contents);
                if (targetScale <= 0 || !AtlasCartographyScaler.canDownscaleAtlas(level, contents)) {
                    simple_atlas$rejectAtlasResult();
                    return;
                }

                ItemStack result = atlasInput.copyWithCount(1);
                result.set(ModComponents.ATLAS_CONTENTS, contents.withSelectedScale(targetScale - 1));
                if (!ItemStack.matches(result, resultStack)) {
                    this.resultContainer.setItem(2, result);
                    ((CartographyTableMenu) (Object) this).broadcastChanges();
                }
            });

            ci.cancel();
            return;
        }

        // ── Atlas + atlas → merge contents ──────────────────────────────────
        if (mapStack.is(ModItems.ATLAS) && additionalStack.is(ModItems.ATLAS)) {
            AtlasContents topContents = mapStack.getOrDefault(ModComponents.ATLAS_CONTENTS, AtlasContents.EMPTY);
            AtlasContents bottomContents = additionalStack.getOrDefault(ModComponents.ATLAS_CONTENTS, AtlasContents.EMPTY);

            this.access.execute((level, _) -> {
                LinkedHashSet<Integer> mergedMapIds = new LinkedHashSet<>(bottomContents.mapIds());
                mergedMapIds.addAll(topContents.mapIds());
                int mergedMapCount = mergedMapIds.size();
                if (mergedMapCount > SimpleAtlasConfigManager.getMaxAtlasMapCount()) {
                    simple_atlas$rejectAtlasResult();
                    return;
                }

                AtlasContents merged = simple_atlas$mergeAtlasContents(bottomContents, topContents);
                ItemStack result = additionalStack.copyWithCount(1);
                result.set(ModComponents.ATLAS_CONTENTS, merged);

                if (!ItemStack.matches(result, resultStack)) {
                    this.resultContainer.setItem(2, result);
                    ((CartographyTableMenu) (Object) this).broadcastChanges();
                }
            });

            ci.cancel();
            return;
        }
    }

    @Unique
    private static AtlasContents simple_atlas$mergeAtlasContents(AtlasContents base, AtlasContents incoming) {
        LinkedHashSet<Integer> mergedMapIds = new LinkedHashSet<>(base.mapIds());
        mergedMapIds.addAll(incoming.mapIds());

        LinkedHashSet<AtlasContents.WaypointData> mergedWaypoints = new LinkedHashSet<>(base.waypoints());
        mergedWaypoints.addAll(incoming.waypoints());

        int selectedIcon = base.selectedWaypointIconIndex();
        int nextWaypointNumber = Math.max(base.nextWaypointNumber(), incoming.nextWaypointNumber());
        int selectedScale = base.selectedScale() >= 0 ? base.selectedScale() : incoming.selectedScale();

        return new AtlasContents(
                List.copyOf(mergedMapIds),
                new ArrayList<>(mergedWaypoints),
                selectedIcon,
                nextWaypointNumber,
                0,
                selectedScale
        );
    }

    @Unique
    private void simple_atlas$rejectAtlasResult() {
        this.resultContainer.removeItemNoUpdate(2);
        ((CartographyTableMenu) (Object) this).broadcastChanges();
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void simple_atlas$quickMoveAtlas(
            Player player,
            int slotIndex,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        Slot slot = ((CartographyTableMenu) (Object) this).slots.get(slotIndex);

        if (slot == null || !slot.hasItem()) {
            return;
        }

        ItemStack stack = slot.getItem();
        ItemStack clicked = stack.copy();

        // ── Result slot (slot 2) quick-move (shift-click) ─────────────────────
        if (slotIndex == 2 && stack.is(ModItems.ATLAS)) {
            CartographyTableMenu menu = (CartographyTableMenu) (Object) this;
            ItemStack slot0 = menu.container.getItem(0);
            ItemStack slot1 = menu.container.getItem(1);

            // Handle scale take:
            boolean isScale = (slot0.is(ModItems.ATLAS) && slot1.is(Items.PAPER))
                    || (slot0.is(Items.PAPER) && slot1.is(ModItems.ATLAS));
            if (isScale && player instanceof ServerPlayer serverPlayer) {
                ItemStack atlas = slot0.is(ModItems.ATLAS) ? slot0 : slot1;
                AtlasContents original = atlas.getOrDefault(ModComponents.ATLAS_CONTENTS, AtlasContents.EMPTY);
                AtlasContents scaled = AtlasCartographyScaler.scaleAtlas(serverPlayer.level(), original);
                if (scaled != null) {
                    stack.set(ModComponents.ATLAS_CONTENTS, scaled);
                }
            }

            // Handle scale down (Shears):
            boolean isDownscale = (slot0.is(ModItems.ATLAS) && slot1.is(Items.SHEARS))
                    || (slot0.is(Items.SHEARS) && slot1.is(ModItems.ATLAS));
            if (isDownscale && player instanceof ServerPlayer serverPlayer) {
                ItemStack atlas = slot0.is(ModItems.ATLAS) ? slot0 : slot1;
                AtlasContents original = atlas.getOrDefault(ModComponents.ATLAS_CONTENTS, AtlasContents.EMPTY);
                AtlasContents downscaled = AtlasCartographyScaler.downscaleAtlas(serverPlayer.level(), original);
                if (downscaled != null) {
                    stack.set(ModComponents.ATLAS_CONTENTS, downscaled);
                }
            }

            // Move to player inventory
            if (!((AbstractContainerMenuInvoker) this).simple_atlas$invokeMoveItemStackTo(stack, 3, 39, true)) {
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }

            slot.onQuickCraft(stack, clicked);

            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            }

            slot.setChanged();

            if (stack.getCount() == clicked.getCount()) {
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }

            slot.onTake(player, stack);
            ((AbstractContainerMenuInvoker) this).simple_atlas$invokeBroadcastChanges();
            cir.setReturnValue(clicked);
            return;
        }

        if (slotIndex >= 3 && slotIndex < 39) {
            if (stack.is(ModItems.ATLAS)) {
                if (simple_atlas$moveStackToSlots(slot, stack, clicked, player, cir, 0, 1, 1, 2)) {
                    return;
                }
            }

            if (stack.is(Items.SHEARS)) {
                if (simple_atlas$moveStackToSlots(slot, stack, clicked, player, cir, 1, 2, 0, 1)) {
                    return;
                }
            }

            if (stack.is(Items.PAPER)) {
                if (simple_atlas$moveStackToSlots(slot, stack, clicked, player, cir, 1, 2, 0, 1)) {
                    return;
                }
            }

            if (stack.is(Items.BOOK)) {
                if (simple_atlas$moveStackToSlots(slot, stack, clicked, player, cir, 0, 1, 1, 2)) {
                    return;
                }
            }

            if (stack.is(Items.FILLED_MAP)) {
                if (simple_atlas$moveStackToSlots(slot, stack, clicked, player, cir, 0, 1, 1, 2)) {
                    return;
                }
            }
        }
    }

    @Unique
    private boolean simple_atlas$moveStackToSlots(
            Slot slot,
            ItemStack stack,
            ItemStack clicked,
            Player player,
            CallbackInfoReturnable<ItemStack> cir,
            int firstSlotPrimary,
            int lastSlotPrimary,
            int firstSlotSecondary,
            int lastSlotSecondary
    ) {
        boolean moved = ((AbstractContainerMenuInvoker) this).simple_atlas$invokeMoveItemStackTo(stack, firstSlotPrimary, lastSlotPrimary, false);
        if (!moved && firstSlotSecondary >= 0) {
            moved = ((AbstractContainerMenuInvoker) this).simple_atlas$invokeMoveItemStackTo(stack, firstSlotSecondary, lastSlotSecondary, false);
        }

        if (!moved) {
            cir.setReturnValue(ItemStack.EMPTY);
            return true;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        }

        slot.setChanged();

        if (stack.getCount() == clicked.getCount()) {
            cir.setReturnValue(ItemStack.EMPTY);
            return true;
        }

        slot.onTake(player, stack);
        ((AbstractContainerMenuInvoker) this).simple_atlas$invokeBroadcastChanges();
        cir.setReturnValue(clicked);
        return true;
    }
}
