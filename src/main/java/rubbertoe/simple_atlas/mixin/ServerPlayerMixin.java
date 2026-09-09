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
import rubbertoe.simple_atlas.map.AtlasMapSelector;
import rubbertoe.simple_atlas.server.AtlasWaypointDecorations;
import rubbertoe.simple_atlas.server.AtlasViewManager;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "synchronizeSpecialItemUpdates", at = @At("HEAD"), cancellable = true)
    private void simple_atlas$augmentAtlasMapUpdatePackets(ItemStack itemStack, CallbackInfo ci) {
        if (!itemStack.is(ModItems.ATLAS)) {
            return;
        }

        ServerPlayer player = simple_atlas$self();
        MapId mapId = itemStack.get(DataComponents.MAP_ID);
        if (mapId == null) {
            ci.cancel();
            return;
        }

        MapItemSavedData mapData = MapItem.getSavedData(mapId, player.level());
        if (mapData == null) {
            ci.cancel();
            return;
        }

        AtlasContents contents = itemStack.getOrDefault(ModComponents.ATLAS_CONTENTS, AtlasContents.EMPTY);
        mapData.getHoldingPlayer(player);
        MapModCompat.sendRemappedPackets(player, mapId, mapData);
        Packet<?> packet = mapData.getUpdatePacket(mapId, player);
        boolean includeAtlasWaypoints = !AtlasViewManager.isViewing(player);
        Packet<?> augmentedPacket = AtlasWaypointDecorations.withAtlasWaypointDecorations(packet, mapData, contents, includeAtlasWaypoints);
        if (augmentedPacket != null) {
            player.connection.send(augmentedPacket);
        }

        if (!contents.subMapIds().isEmpty()) {
            Integer currentSubMapRawId = AtlasMapSelector.findCurrentMapRawId(
                    player.level(),
                    player.getX(),
                    player.getZ(),
                    contents.subMapIds(),
                    null
            );
            if (currentSubMapRawId != null) {
                MapId subMapId = new MapId(currentSubMapRawId);
                MapItemSavedData subMapData = player.level().getMapData(subMapId);
                if (subMapData != null) {
                    subMapData.getHoldingPlayer(player);
                    MapModCompat.sendRemappedPackets(player, subMapId, subMapData);
                    Packet<?> subPacket = subMapData.getUpdatePacket(subMapId, player);
                    if (subPacket != null) {
                        player.connection.send(subPacket);
                    }
                }
            }
        }

        ci.cancel();
    }

    @Unique
    private ServerPlayer simple_atlas$self() {
        return (ServerPlayer) (Object) this;
    }
}
