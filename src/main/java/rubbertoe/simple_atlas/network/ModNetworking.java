package rubbertoe.simple_atlas.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.waypoints.Waypoint;
import rubbertoe.simple_atlas.advancement.ModCriteria;
import rubbertoe.simple_atlas.component.AtlasContents;
import rubbertoe.simple_atlas.component.ModComponents;
import rubbertoe.simple_atlas.config.SimpleAtlasConfigManager;
import rubbertoe.simple_atlas.item.ModItems;
import rubbertoe.simple_atlas.navigation.WaypointIconCatalog;
import rubbertoe.simple_atlas.server.AtlasWaypointDecorations;
import rubbertoe.simple_atlas.server.AtlasViewManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ModNetworking {
    public static final int MAX_WAYPOINT_COUNT = 256;
    private static final int PINNED_WAYPOINT_RECONCILE_INTERVAL_TICKS = 20;
    private static final Map<UUID, Set<UUID>> PINNED_NAVIGATION_IDS = new ConcurrentHashMap<>();

    private ModNetworking() {}

    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(
                OpenAtlasScreenPayload.TYPE,
                OpenAtlasScreenPayload.CODEC
        );
        PayloadTypeRegistry.serverboundPlay().register(
                CloseAtlasViewPayload.TYPE,
                CloseAtlasViewPayload.CODEC
        );
        PayloadTypeRegistry.serverboundPlay().register(
                SaveAtlasWaypointsPayload.TYPE,
                SaveAtlasWaypointsPayload.CODEC
        );
        PayloadTypeRegistry.serverboundPlay().register(
                NavigateToWaypointPayload.TYPE,
                NavigateToWaypointPayload.CODEC
        );
        PayloadTypeRegistry.serverboundPlay().register(
                StopNavigatingPayload.TYPE,
                StopNavigatingPayload.CODEC
        );
        PayloadTypeRegistry.serverboundPlay().register(
                UnpinWaypointPayload.TYPE,
                UnpinWaypointPayload.CODEC
        );
        PayloadTypeRegistry.serverboundPlay().register(
                RemoveAtlasMapPayload.TYPE,
                RemoveAtlasMapPayload.CODEC
        );
        ServerPlayNetworking.registerGlobalReceiver(
                CloseAtlasViewPayload.TYPE,
                (_, context) -> context.server().execute(() -> {
                    var player = context.player();
                    AtlasViewManager.stopViewing(player);
                    refreshHeldAtlasWaypoints(player);
                })
        );
        ServerPlayNetworking.registerGlobalReceiver(
                SaveAtlasWaypointsPayload.TYPE,
                (payload, context) -> context.server().execute(() -> {
                    var player = context.player();
                    ItemStack atlasStack = player.getMainHandItem();
                    if (!atlasStack.is(ModItems.ATLAS)) {
                        return;
                    }

                    AtlasContents contents = atlasStack.getOrDefault(ModComponents.ATLAS_CONTENTS, AtlasContents.EMPTY);
                    if (!contents.mapIds().equals(payload.atlasMapIds())) {
                        return;
                    }

                    List<AtlasContents.WaypointData> sanitizedWaypoints = sanitizeWaypoints(payload.waypoints());
                    AtlasContents updated = contents.withWaypointState(
                            sanitizedWaypoints,
                            payload.selectedWaypointIconIndex(),
                            payload.nextWaypointNumber()
                    );
                    atlasStack.set(ModComponents.ATLAS_CONTENTS, updated);
                    reconcilePinnedWaypoints(player, sanitizedWaypoints);
                    sendImmediateWaypointRefresh(player, updated);
                })
        );
        ServerPlayNetworking.registerGlobalReceiver(
                NavigateToWaypointPayload.TYPE,
                (payload, context) -> context.server().execute(() -> {
                    var player = context.player();
                    if (playerLacksAtlasInInventory(player)) {
                        clearPinnedWaypoints(player);
                        return;
                    }

                    UUID playerId = player.getUUID();
                    UUID newNavigationId = WaypointIconCatalog.navigationWaypointId(payload.worldX(), payload.worldZ());

                    if (!addPinnedWaypoint(playerId, newNavigationId)) {
                        return;
                    }

                    Waypoint.Icon icon = new Waypoint.Icon();
                    icon.style = WaypointIconCatalog.styleKeyForIndex(payload.waypointIconIndex());
                    icon.color = Optional.of(0xFFFFFF);

                    BlockPos pos = BlockPos.containing(payload.worldX(), player.getY(), payload.worldZ());
                    sendPinnedWaypoint(player, newNavigationId, icon, pos);
                    ModCriteria.WAYPOINT_PINNED_TO_LOCATOR_BAR.trigger(player);
                })
        );
        ServerPlayNetworking.registerGlobalReceiver(
                StopNavigatingPayload.TYPE,
                (_, context) -> context.server().execute(() -> clearPinnedWaypoints(context.player()))
        );
        ServerPlayNetworking.registerGlobalReceiver(
                UnpinWaypointPayload.TYPE,
                (payload, context) -> context.server().execute(() -> {
                    var player = context.player();
                    UUID playerId = player.getUUID();
                    UUID unpinNavigationId = WaypointIconCatalog.navigationWaypointId(payload.worldX(), payload.worldZ());

                    if (!removePinnedWaypoint(playerId, unpinNavigationId)) {
                        return;
                    }

                    sendRemovedPinnedWaypoint(player, unpinNavigationId);
                })
        );
        ServerPlayNetworking.registerGlobalReceiver(
                RemoveAtlasMapPayload.TYPE,
                (payload, context) -> context.server().execute(() -> {
                    var player = context.player();
                    ItemStack atlasStack = player.getMainHandItem();
                    if (!atlasStack.is(ModItems.ATLAS)) {
                        return;
                    }

                    AtlasContents contents = atlasStack.getOrDefault(ModComponents.ATLAS_CONTENTS, AtlasContents.EMPTY);
                    if (!contents.mapIds().equals(payload.atlasMapIds())) {
                        return;
                    }

                    AtlasContents updated = removeMapFromAtlas(player, contents, payload.mapId());
                    if (updated == null) {
                        return;
                    }

                    atlasStack.set(ModComponents.ATLAS_CONTENTS, updated);
                    giveRemovedMapToPlayer(player, payload.mapId());
                    sendMapRefreshWithoutAtlasWaypoints(player, payload.mapId());
                    reconcilePinnedWaypoints(player, updated.waypoints());
                    sendImmediateWaypointRefresh(player, updated);
                })
        );
        ServerPlayConnectionEvents.DISCONNECT.register((handler, _) ->
                PINNED_NAVIGATION_IDS.remove(handler.player.getUUID())
        );
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (PINNED_NAVIGATION_IDS.isEmpty()) {
                return;
            }

            if (server.getTickCount() % PINNED_WAYPOINT_RECONCILE_INTERVAL_TICKS != 0) {
                return;
            }

            for (UUID playerId : new ArrayList<>(PINNED_NAVIGATION_IDS.keySet())) {
                var player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    PINNED_NAVIGATION_IDS.remove(playerId);
                    continue;
                }

                if (playerLacksAtlasInInventory(player)) {
                    clearPinnedWaypoints(player);
                }
            }
        });
    }

    private static boolean playerLacksAtlasInInventory(net.minecraft.server.level.ServerPlayer player) {
        int size = player.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            if (player.getInventory().getItem(i).is(ModItems.ATLAS)) {
                return false;
            }
        }
        return true;
    }

    private static void clearPinnedWaypoints(net.minecraft.server.level.ServerPlayer player) {
        Set<UUID> removedNavigationIds = PINNED_NAVIGATION_IDS.remove(player.getUUID());
        if (removedNavigationIds == null || removedNavigationIds.isEmpty()) {
            return;
        }

        removedNavigationIds.forEach(waypointId -> sendRemovedPinnedWaypoint(player, waypointId));
    }

    private static boolean addPinnedWaypoint(UUID playerId, UUID waypointId) {
        return PINNED_NAVIGATION_IDS.computeIfAbsent(playerId, _ -> new HashSet<>()).add(waypointId);
    }

    private static boolean removePinnedWaypoint(UUID playerId, UUID waypointId) {
        Set<UUID> pinnedIds = PINNED_NAVIGATION_IDS.get(playerId);
        if (pinnedIds == null || !pinnedIds.remove(waypointId)) {
            return false;
        }

        if (pinnedIds.isEmpty()) {
            PINNED_NAVIGATION_IDS.remove(playerId);
        }
        return true;
    }

    private static void sendPinnedWaypoint(net.minecraft.server.level.ServerPlayer player, UUID waypointId, Waypoint.Icon icon, BlockPos pos) {
        player.connection.send(ClientboundTrackedWaypointPacket.addWaypointPosition(waypointId, icon, pos));
    }

    private static void sendRemovedPinnedWaypoint(net.minecraft.server.level.ServerPlayer player, UUID waypointId) {
        player.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(waypointId));
    }

    private static void reconcilePinnedWaypoints(
            net.minecraft.server.level.ServerPlayer player,
            List<AtlasContents.WaypointData> sanitizedWaypoints
    ) {
        Set<UUID> pinnedIds = PINNED_NAVIGATION_IDS.get(player.getUUID());
        if (pinnedIds == null || pinnedIds.isEmpty()) {
            return;
        }

        Set<UUID> validWaypointIds = new HashSet<>();
        for (AtlasContents.WaypointData waypoint : sanitizedWaypoints) {
            validWaypointIds.add(WaypointIconCatalog.navigationWaypointId(waypoint.worldX(), waypoint.worldZ()));
        }

        List<UUID> stalePinnedIds = pinnedIds.stream()
                .filter(id -> !validWaypointIds.contains(id))
                .toList();
        if (stalePinnedIds.isEmpty()) {
            return;
        }

        for (UUID staleId : stalePinnedIds) {
            sendRemovedPinnedWaypoint(player, staleId);
            removePinnedWaypoint(player.getUUID(), staleId);
        }
    }

    private static List<AtlasContents.WaypointData> sanitizeWaypoints(List<AtlasContents.WaypointData> waypoints) {
        if (waypoints.isEmpty()) {
            return List.of();
        }

        int maxWaypoints = SimpleAtlasConfigManager.getMaxWaypoints();
        List<AtlasContents.WaypointData> sanitized = new ArrayList<>(Math.min(waypoints.size(), maxWaypoints));
        for (AtlasContents.WaypointData waypoint : waypoints) {
            if (sanitized.size() >= maxWaypoints) {
                break;
            }

             if (!Double.isFinite(waypoint.worldX()) || !Double.isFinite(waypoint.worldZ())) {
                continue;
            }

            sanitized.add(new AtlasContents.WaypointData(
                    waypoint.worldX(),
                    waypoint.worldZ(),
                    waypoint.name(),
                    waypoint.iconIndex(),
                    AtlasContents.sanitizeDimension(waypoint.dimension())
            ));
        }
        return sanitized;
    }

    private static AtlasContents removeMapFromAtlas(
            net.minecraft.server.level.ServerPlayer player,
            AtlasContents contents,
            int removedMapId
    ) {
        if (!contents.contains(removedMapId)) {
            return null;
        }

        MapItemSavedData removedMapData = player.level().getMapData(new MapId(removedMapId));
        if (removedMapData == null) {
            return null;
        }

        List<Integer> updatedMapIds = new ArrayList<>(Math.max(0, contents.mapIds().size() - 1));
        for (int mapId : contents.mapIds()) {
            if (mapId != removedMapId) {
                updatedMapIds.add(mapId);
            }
        }

        List<Integer> updatedSubMapIds = new ArrayList<>();
        for (int subId : contents.subMapIds()) {
            MapItemSavedData subData = player.level().getMapData(new MapId(subId));
            if (subData != null && subData.dimension.equals(removedMapData.dimension)
                    && Math.abs(subData.centerX - removedMapData.centerX) <= 64
                    && Math.abs(subData.centerZ - removedMapData.centerZ) <= 64) {
                continue;
            }
            updatedSubMapIds.add(subId);
        }

        List<AtlasContents.WaypointData> filteredWaypoints = contents.waypoints().stream()
                .filter(waypoint -> !isWaypointOnMap(waypoint, removedMapData))
                .toList();

        return new AtlasContents(
                updatedMapIds,
                filteredWaypoints,
                contents.selectedWaypointIconIndex(),
                contents.nextWaypointNumber(),
                0,
                updatedSubMapIds
        );
    }

    private static boolean isWaypointOnMap(AtlasContents.WaypointData waypoint, MapItemSavedData mapData) {
        String mapDimension = mapData.dimension.identifier().toString();
        if (!mapDimension.equals(waypoint.dimension())) {
            return false;
        }

        int mapSpan = 128 << mapData.scale;
        double minX = mapData.centerX - mapSpan / 2.0;
        double minZ = mapData.centerZ - mapSpan / 2.0;
        double maxX = minX + mapSpan;
        double maxZ = minZ + mapSpan;

        return waypoint.worldX() >= minX
                && waypoint.worldX() < maxX
                && waypoint.worldZ() >= minZ
                && waypoint.worldZ() < maxZ;
    }

    private static void giveRemovedMapToPlayer(net.minecraft.server.level.ServerPlayer player, int removedMapId) {
        ItemStack removedMap = new ItemStack(Items.FILLED_MAP);
        removedMap.set(DataComponents.MAP_ID, new MapId(removedMapId));

        if (!player.getInventory().add(removedMap)) {
            player.drop(removedMap, false);
        }
    }

    private static void sendMapRefreshWithoutAtlasWaypoints(net.minecraft.server.level.ServerPlayer player, int rawMapId) {
        MapId mapId = new MapId(rawMapId);
        MapItemSavedData mapData = player.level().getMapData(mapId);
        if (mapData == null) {
            return;
        }

        mapData.getHoldingPlayer(player);
        List<MapDecoration> currentDecorations = new ArrayList<>();
        mapData.getDecorations().forEach(currentDecorations::add);

        Packet<?> packet = new ClientboundMapItemDataPacket(
                mapId,
                mapData.scale,
                mapData.locked,
                currentDecorations,
                null
        );
        player.connection.send(packet);
    }

    private static void sendImmediateWaypointRefresh(net.minecraft.server.level.ServerPlayer player, AtlasContents contents) {
        Set<Integer> relevantMapIds = collectRelevantMapIds(player);
        for (int rawId : contents.mapIds()) {
            if (!relevantMapIds.isEmpty() && !relevantMapIds.contains(rawId)) {
                continue;
            }

            MapId mapId = new MapId(rawId);
            MapItemSavedData mapData = player.level().getMapData(mapId);
            if (mapData == null) {
                continue;
            }

            mapData.getHoldingPlayer(player);
            Packet<?> packet = mapData.getUpdatePacket(mapId, player);

            // Force a one-shot packet when vanilla has no dirty update, so waypoint edits apply instantly.
            if (packet == null) {
                List<MapDecoration> currentDecorations = new ArrayList<>();
                mapData.getDecorations().forEach(currentDecorations::add);
                packet = new ClientboundMapItemDataPacket(mapId, mapData.scale, mapData.locked, currentDecorations, null);
            }

            boolean includeAtlasWaypoints = !AtlasViewManager.isViewing(player);
            Packet<?> augmentedPacket = AtlasWaypointDecorations.withAtlasWaypointDecorations(packet, mapData, contents, includeAtlasWaypoints);
            if (augmentedPacket != null) {
                player.connection.send(augmentedPacket);
            }
        }
    }

    private static Set<Integer> collectRelevantMapIds(net.minecraft.server.level.ServerPlayer player) {
        Set<Integer> relevantMapIds = new HashSet<>();
        collectRelevantMapIdsFromStack(player.getMainHandItem(), relevantMapIds);
        collectRelevantMapIdsFromStack(player.getOffhandItem(), relevantMapIds);
        relevantMapIds.addAll(AtlasViewManager.getViewedMaps(player));
        return relevantMapIds;
    }

    private static void collectRelevantMapIdsFromStack(ItemStack stack, Set<Integer> relevantMapIds) {
        if (!stack.is(ModItems.ATLAS)) {
            return;
        }

        AtlasContents contents = stack.getOrDefault(ModComponents.ATLAS_CONTENTS, AtlasContents.EMPTY);
        relevantMapIds.addAll(contents.mapIds());

        MapId currentMapId = stack.get(DataComponents.MAP_ID);
        if (currentMapId != null) {
            relevantMapIds.add(currentMapId.id());
        }
    }

    private static void refreshHeldAtlasWaypoints(net.minecraft.server.level.ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.is(ModItems.ATLAS)) {
            AtlasContents contents = mainHand.getOrDefault(ModComponents.ATLAS_CONTENTS, AtlasContents.EMPTY);
            sendImmediateWaypointRefresh(player, contents);
        }

        ItemStack offHand = player.getOffhandItem();
        if (offHand.is(ModItems.ATLAS) && offHand != mainHand) {
            AtlasContents contents = offHand.getOrDefault(ModComponents.ATLAS_CONTENTS, AtlasContents.EMPTY);
            sendImmediateWaypointRefresh(player, contents);
        }
    }
}
