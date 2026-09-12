package rubbertoe.simple_atlas.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rubbertoe.simple_atlas.SimpleAtlas;
import rubbertoe.simple_atlas.compat.MapModCompat;
import rubbertoe.simple_atlas.item.ModItems;

import java.util.ArrayList;
import java.util.List;

@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {
    @Shadow
    @Final
    private CraftingContainer craftSlots;

    @Shadow
    @Final
    private Player player;

    @Unique
    private final List<Integer> simple_atlas$emptyMapSlotsToClear = new ArrayList<>();

    @Inject(method = "onTake", at = @At("HEAD"))
    private void simple_atlas$beforeTake(Player player, ItemStack stack, CallbackInfo ci) {
        simple_atlas$emptyMapSlotsToClear.clear();

        boolean hasAtlasInput = false;

        for (int i = 0; i < this.craftSlots.getContainerSize(); i++) {
            ItemStack item = this.craftSlots.getItem(i);
            if (item.is(ModItems.ATLAS)) {
                hasAtlasInput = true;
            } else if (MapModCompat.isEmptyMap(item)) {
                simple_atlas$emptyMapSlotsToClear.add(i);
            }
        }

        if (!hasAtlasInput) {
            simple_atlas$emptyMapSlotsToClear.clear();
        }
    }

    @Inject(method = "onTake", at = @At("TAIL"))
    private void simple_atlas$afterTake(Player player, ItemStack stack, CallbackInfo ci) {
        if (!simple_atlas$emptyMapSlotsToClear.isEmpty()) {
            int clearedCount = simple_atlas$emptyMapSlotsToClear.size();
            for (int slotIndex : simple_atlas$emptyMapSlotsToClear) {
                this.craftSlots.setItem(slotIndex, ItemStack.EMPTY);
            }
            simple_atlas$emptyMapSlotsToClear.clear();
            SimpleAtlas.LOGGER.info("Consumed full empty map stack(s) across {} slot(s) for AtlasAddRecipe", clearedCount);
        }
    }
}