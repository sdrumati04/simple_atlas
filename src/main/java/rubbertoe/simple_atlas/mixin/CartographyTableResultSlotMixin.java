package rubbertoe.simple_atlas.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rubbertoe.simple_atlas.cartography.AtlasCartographyTakeHandler;

/**
 * Extends the result-slot take behavior for atlas recipes in the cartography table.
 */
@Mixin(targets = "net.minecraft.world.inventory.CartographyTableMenu$5")
public abstract class CartographyTableResultSlotMixin {

    /** Outer {@link CartographyTableMenu} instance (synthetic {@code this$0}). */
    @Shadow(aliases = "this$0")
    private CartographyTableMenu simple_atlas$outerMenu;

    @Unique
    private AtlasCartographyTakeHandler.TakeContext simple_atlas$takeContext;

    @Inject(method = "onTake", at = @At("HEAD"))
    private void simple_atlas$captureRecipeType(Player player, ItemStack carried, CallbackInfo ci) {
        simple_atlas$takeContext = AtlasCartographyTakeHandler.captureContext(simple_atlas$outerMenu.container);
    }

    @Inject(method = "onTake", at = @At("TAIL"))
    private void simple_atlas$handleAtlasRecipes(Player player, ItemStack carried, CallbackInfo ci) {
        if (simple_atlas$takeContext != null) {
            AtlasCartographyTakeHandler.applyPostTakeEffects(
                    simple_atlas$takeContext,
                    simple_atlas$outerMenu.container,
                    player,
                    carried
            );
            simple_atlas$takeContext = null;
        }
    }
}
