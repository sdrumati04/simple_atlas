package rubbertoe.simple_atlas.server;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import rubbertoe.simple_atlas.cartography.AtlasCartographyScaler;
import rubbertoe.simple_atlas.compat.MapModCompat;
import rubbertoe.simple_atlas.component.AtlasContents;
import rubbertoe.simple_atlas.component.ModComponents;
import rubbertoe.simple_atlas.item.ModItems;
import rubbertoe.simple_atlas.map.AtlasMapSelector;

public final class AtlasViewTicker {
    private static final int SYNC_INTERVAL_TICKS = 10;

    private AtlasViewTicker() {}

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(AtlasViewTicker::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, _) ->
                AtlasViewManager.stopViewing(handler.player)
        );
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % SYNC_INTERVAL_TICKS != 0) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ItemStack mainHand = player.getMainHandItem();
            ItemStack offHand = player.getOffhandItem();
            boolean holdingAtlasInMainHand = mainHand.is(ModItems.ATLAS);
            boolean holdingAtlas = holdingAtlasInMainHand || offHand.is(ModItems.ATLAS);

            // If the atlas left the main hand, close any open view.
            if (!holdingAtlasInMainHand) {
                if (AtlasViewManager.isViewing(player)) {
                    AtlasViewManager.stopViewing(player);
                }
            }

            if (!holdingAtlas) {
                continue;
            }

            // Regular held-map packets are already handled every tick in ServerPlayer.
            // Keep ticker map packets only for the full atlas screen session.
            if (!AtlasViewManager.isViewing(player)) {
                continue;
            }

            sendCurrentHeldAtlasMapUpdate(player, mainHand);
            sendCurrentHeldAtlasMapUpdate(player, offHand);
        }
    }

    private static void sendCurrentHeldAtlasMapUpdate(ServerPlayer player, ItemStack stack) {
        if (!stack.is(ModItems.ATLAS)) {
            return;
        }

        AtlasContents contents = stack.getOrDefault(ModComponents.ATLAS_CONTENTS, AtlasContents.EMPTY);
        if (contents.mapIds().isEmpty()) {
            return;
        }

        MapId existingId = stack.get(DataComponents.MAP_ID);
        Integer preferredRawId = existingId != null ? existingId.id() : null;
        Integer currentMapRawId = AtlasMapSelector.findCurrentMapRawId(
                player.level(),
                player.getX(),
                player.getZ(),
                contents.mapIds(),
                preferredRawId
        );
        if (currentMapRawId == null) {
            return;
        }

        MapId mapId = new MapId(currentMapRawId);
        MapItemSavedData mapData = player.level().getMapData(mapId);
        if (mapData == null) {
            return;
        }

        if (!mapData.locked) {
            ((MapItem) Items.FILLED_MAP).update(player.level(), player, mapData);
        }

        mapData.getHoldingPlayer(player);
        Packet<?> packet = mapData.getUpdatePacket(mapId, player);
        Packet<?> augmentedPacket = AtlasWaypointDecorations.withAtlasWaypointDecorations(packet, mapData, contents, false);
        if (augmentedPacket != null) {
            player.connection.send(augmentedPacket);
        }
        MapModCompat.sendRemappedPackets(player, mapId, mapData);

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
                    if (!subMapData.locked) {
                        ((MapItem) Items.FILLED_MAP).update(player.level(), player, subMapData);
                    }
                    AtlasCartographyScaler.syncParentAndSubMaps(player.level(), contents);
                    subMapData.getHoldingPlayer(player);
                    Packet<?> subPacket = subMapData.getUpdatePacket(subMapId, player);
                    if (subPacket != null) {
                        player.connection.send(subPacket);
                    }
                    MapModCompat.sendRemappedPackets(player, subMapId, subMapData);
                }
            }
        }
    }
}
