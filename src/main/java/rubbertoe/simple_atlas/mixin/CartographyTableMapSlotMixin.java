package rubbertoe.simple_atlas.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rubbertoe.simple_atlas.item.ModItems;

/**
 * Extends the cartography table's top (map) slot to also accept:
 * <ul>
 *   <li>{@link Items#BOOK} – books for the atlas duplication recipe.</li>
 * </ul>
 */
@Mixin(targets = "net.minecraft.world.inventory.CartographyTableMenu$3")
public abstract class CartographyTableMapSlotMixin {

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void simple_atlas$allowAtlasInputs(ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
        if (itemStack.is(Items.BOOK)
                || itemStack.is(ModItems.ATLAS)
                || itemStack.is(Items.SHEARS)
                || itemStack.is(Items.PAPER)
                || rubbertoe.simple_atlas.compat.MapModCompat.isEmptyMap(itemStack)) {
            cir.setReturnValue(true);
        }
    }
}

