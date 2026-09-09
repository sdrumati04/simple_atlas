package rubbertoe.simple_atlas.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rubbertoe.simple_atlas.item.ModItems;

@Mixin(targets = "net.minecraft.world.inventory.CartographyTableMenu$4")
public abstract class CartographyTableAdditionalSlotMixin {

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void simple_atlas$allowAtlas(ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
        if (itemStack.is(ModItems.ATLAS) || itemStack.is(Items.BOOK) || itemStack.is(Items.FILLED_MAP)) {
            cir.setReturnValue(true);
        }
    }
}