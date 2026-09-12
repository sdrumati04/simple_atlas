package rubbertoe.simple_atlas.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rubbertoe.simple_atlas.component.AtlasContents;
import rubbertoe.simple_atlas.component.ModComponents;
import rubbertoe.simple_atlas.item.ModItems;
import rubbertoe.simple_atlas.compat.MapModCompat;
import rubbertoe.simple_atlas.server.AtlasWaypointDecorations;

@Mixin(value = ServerPlayer.class, priority = 500)
public abstract class ServerPlayerMixin {
    @Inject(method = "synchronizeSpecialItemUpdates", at = @At("HEAD"), cancellable = true)
    private void simple_atlas$augmentAtlasMapUpdatePackets(ItemStack itemStack, CallbackInfo ci) {
        MapId itemMapId = itemStack.get(DataComponents.MAP_ID);
        if (itemMapId == null) {
            return;
        }

        ServerPlayer player = simple_atlas$self();
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        MapId mainAtlasMapId = mainHand.is(ModItems.ATLAS) ? mainHand.get(DataComponents.MAP_ID) : null;
        MapId offAtlasMapId = offHand.is(ModItems.ATLAS) ? offHand.get(DataComponents.MAP_ID) : null;

        // If the player is not actively holding any atlas in hand, allow vanilla behavior
        if (mainAtlasMapId == null && offAtlasMapId == null) {
            return;
        }

        boolean isHeldAtlas = (itemStack == mainHand || itemStack == offHand) && itemStack.is(ModItems.ATLAS);

        // If this item is not the held atlas itself, but matches the map shown by a held atlas,
        // cancel vanilla processing to avoid sending unaugmented packets or clearing dirty decorations.
        if (!isHeldAtlas) {
            if (itemMapId.equals(mainAtlasMapId) || itemMapId.equals(offAtlasMapId)) {
                ci.cancel();
            }
            return;
        }

        // Only the actively held atlas reaches this point
        AtlasContents contents = itemStack.getOrDefault(ModComponents.ATLAS_CONTENTS, AtlasContents.EMPTY);
        MapItemSavedData mapData = MapItem.getSavedData(itemMapId, player.level());
        if (mapData == null) {
            ci.cancel();
            return;
        }

        MapItemSavedData.HoldingPlayer holdingPlayer = mapData.getHoldingPlayer(player);
        boolean wasDirtyData = false;
        HoldingPlayerAccessor accessor = null;
        if (MapModCompat.isRemappedLoaded() && holdingPlayer instanceof HoldingPlayerAccessor acc) {
            accessor = acc;
            wasDirtyData = accessor.simple_atlas$getDirtyData();
        }

        // Call getUpdatePacket first so vanilla processes dirtyDecorations before remapped touches it
        Packet<?> packet = mapData.getUpdatePacket(itemMapId, player);
        Packet<?> augmentedPacket = AtlasWaypointDecorations.withAtlasWaypointDecorations(packet, mapData, contents);
        if (augmentedPacket != null) {
            player.connection.send(augmentedPacket);
        }

        if (accessor != null && wasDirtyData) {
            accessor.simple_atlas$setDirtyData(true);
            MapModCompat.sendRemappedPackets(player, itemMapId, mapData);
            accessor.simple_atlas$setDirtyData(false);
        } else {
            MapModCompat.sendRemappedPackets(player, itemMapId, mapData);
        }

        ci.cancel();
    }

    @Unique
    private ServerPlayer simple_atlas$self() {
        return (ServerPlayer) (Object) this;
    }
}
