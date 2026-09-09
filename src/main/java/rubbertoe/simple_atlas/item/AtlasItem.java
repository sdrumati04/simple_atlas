package rubbertoe.simple_atlas.item;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.item.MapItem;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import rubbertoe.simple_atlas.advancement.ModCriteria;
import rubbertoe.simple_atlas.config.SimpleAtlasConfigManager;
import rubbertoe.simple_atlas.layout.AtlasLayout;
import rubbertoe.simple_atlas.layout.AtlasLayoutBuilder;
import rubbertoe.simple_atlas.component.AtlasContents;
import rubbertoe.simple_atlas.component.ModComponents;
import rubbertoe.simple_atlas.navigation.WaypointIconCatalog;
import rubbertoe.simple_atlas.network.AtlasTilePayload;
import rubbertoe.simple_atlas.network.OpenAtlasScreenPayload;
import rubbertoe.simple_atlas.map.AtlasMapSelector;
import rubbertoe.simple_atlas.server.AtlasViewManager;
import rubbertoe.simple_atlas.cartography.AtlasCartographyScaler;
import rubbertoe.simple_atlas.compat.MapModCompat;

public class AtlasItem extends Item {
    public AtlasItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NonNull InteractionResult useOn(@NonNull UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || context.getHand() != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof BannerBlockEntity banner)) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ItemStack atlasStack = context.getItemInHand();
        AtlasContents contents = atlasStack.getOrDefault(ModComponents.ATLAS_CONTENTS, AtlasContents.EMPTY);

        int maxWaypoints = SimpleAtlasConfigManager.getMaxWaypoints();
        if (contents.waypoints().size() >= maxWaypoints) {
            player.sendSystemMessage(
                    Component.translatable("message.simple_atlas.waypoint_limit_reached", maxWaypoints)
                            .withStyle(ChatFormatting.RED)
            );
            return InteractionResult.SUCCESS_SERVER;
        }

        BlockPos bannerPos = context.getClickedPos();
        String dimensionKey = ((ServerLevel) level).getLevel().dimension().identifier().toString();
        if (hasWaypointAtBlock(contents, bannerPos, dimensionKey)) {
            return InteractionResult.SUCCESS_SERVER;
        }

        int bannerIconIndex = WaypointIconCatalog.bannerIconIndexForColor(banner.getBaseColor());
        String customName = banner.getCustomName() != null ? banner.getCustomName().getString() : "";
        String waypointName = customName.isBlank() ? "Banner " + contents.nextWaypointNumber() : customName;

        AtlasContents.WaypointData waypoint = new AtlasContents.WaypointData(
                bannerPos.getX() + 0.5,
                bannerPos.getZ() + 0.5,
                waypointName,
                bannerIconIndex,
                dimensionKey
        );

        AtlasContents updated = withAppendedWaypoint(contents, waypoint);
        atlasStack.set(ModComponents.ATLAS_CONTENTS, updated);

        if (player instanceof ServerPlayer serverPlayer) {
            ModCriteria.WAYPOINT_ADDED_VIA_BANNER.trigger(serverPlayer);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(
            @NonNull ItemStack stack,
            Item.@NonNull TooltipContext context,
            @NonNull TooltipDisplay tooltipDisplay,
            @NonNull Consumer<Component> tooltipComponents,
            @NonNull TooltipFlag flag
    ) {
        AtlasContents contents = stack.getOrDefault(ModComponents.ATLAS_CONTENTS, AtlasContents.EMPTY);

        if (contents.mapIds().isEmpty()) {
            tooltipComponents.accept(
                    Component.translatable("tooltip.simple_atlas.no_maps")
                            .withStyle(ChatFormatting.GRAY)
            );
        } else {

            MapItemSavedData mapData = context.mapData(new MapId(contents.mapIds().getFirst()));
            if (mapData != null) {
                int scaleLevel = mapData.scale;
                int scaleRatio = 1 << scaleLevel;
                tooltipComponents.accept(
                        Component.translatable("tooltip.simple_atlas.scale", scaleRatio)
                                .withStyle(ChatFormatting.GRAY)
                );
            }
        }
    }

    @Override
    public @NonNull InteractionResult use(@NonNull Level level, @NonNull Player player, @NonNull InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ServerLevel serverLevel = (ServerLevel) level;

        ItemStack atlasStack = player.getItemInHand(hand);
        AtlasContents contents = atlasStack.getOrDefault(
                ModComponents.ATLAS_CONTENTS,
                AtlasContents.EMPTY
        );

        if (contents.mapIds().isEmpty()) {
            player.sendSystemMessage(
                    Component.translatable("message.simple_atlas.no_maps_inserted")
                            .withStyle(ChatFormatting.YELLOW)
            );
            return InteractionResult.SUCCESS;
        }

        // Ensure sub-maps exist for any scale 1 map and are explored in 1:1
        AtlasContents ensuredContents = AtlasCartographyScaler.ensureSubMaps(serverLevel, contents, (ServerPlayer) player);
        if (!ensuredContents.equals(contents)) {
            atlasStack.set(ModComponents.ATLAS_CONTENTS, ensuredContents);
            contents = ensuredContents;
        }

        syncAtlasMapsToPlayer((ServerPlayer) player, serverLevel, contents);
        AtlasViewManager.startViewing((ServerPlayer) player, contents.allMapIds());
        ServerPlayNetworking.send((ServerPlayer) player, createOpenPayload((ServerPlayer) player, serverLevel, contents));

        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(@NonNull ItemStack stack, @NonNull ServerLevel level, @NonNull Entity entity, EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);

        if (!(entity instanceof Player player)) {
            return;
        }

        boolean heldInHand = slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND;
        if (!heldInHand) {
            removeMapIdIfPresent(stack);
            return;
        }

        AtlasContents contents = stack.getOrDefault(ModComponents.ATLAS_CONTENTS, AtlasContents.EMPTY);
        if (contents.mapIds().isEmpty()) {
            removeMapIdIfPresent(stack);
            return;
        }

        MapId existingId = stack.get(DataComponents.MAP_ID);
        Integer preferredRawId = existingId != null ? existingId.id() : null;
        Integer currentMapRawId = AtlasMapSelector.findCurrentMapRawId(
                level,
                player.getX(),
                player.getZ(),
                contents.mapIds(),
                preferredRawId
        );
        if (currentMapRawId == null) {
            removeMapIdIfPresent(stack);
            return;
        }

        MapId targetId = new MapId(currentMapRawId);
        if (!targetId.equals(existingId)) {
            stack.set(DataComponents.MAP_ID, targetId);
        }

        // Mirror vanilla carried-map behavior so the player marker is present on the held atlas map.
        Items.FILLED_MAP.inventoryTick(stack, level, entity, slot);

        // Also update sub-map if player is inside one and sync upwards through hierarchy
        if (!contents.subMapIds().isEmpty()) {
            Integer currentSubMapRawId = AtlasMapSelector.findCurrentMapRawId(
                    level,
                    player.getX(),
                    player.getZ(),
                    contents.subMapIds(),
                    null
            );
            if (currentSubMapRawId != null) {
                MapId subMapId = new MapId(currentSubMapRawId);
                MapItemSavedData subMapData = level.getMapData(subMapId);
                if (subMapData != null && !subMapData.locked) {
                    subMapData.getHoldingPlayer(player);
                    subMapData.tickCarriedBy(player, stack, null);
                    ((MapItem) Items.FILLED_MAP).update(level, player, subMapData);

                    syncHierarchyToParents(level, player.getX(), player.getZ(), contents);
                }
            }
        }
    }

    private static void syncHierarchyToParents(ServerLevel level, double x, double z, AtlasContents contents) {
        java.util.Map<Integer, MapItemSavedData> mapByScale = new java.util.HashMap<>();
        for (int rawId : contents.allMapIds()) {
            MapItemSavedData data = level.getMapData(new MapId(rawId));
            if (data != null && data.dimension.equals(level.dimension())) {
                int halfSpan = 64 << data.scale;
                if (Math.abs(x - data.centerX) <= halfSpan && Math.abs(z - data.centerZ) <= halfSpan) {
                    mapByScale.put((int) data.scale, data);
                }
            }
        }

        for (int s = 0; s < 4; s++) {
            MapItemSavedData child = mapByScale.get(s);
            MapItemSavedData parent = mapByScale.get(s + 1);
            if (child != null && parent != null) {
                AtlasCartographyScaler.syncSubMapToParent(parent, child);
            }
        }
    }

    public static OpenAtlasScreenPayload createOpenPayload(ServerPlayer player, ServerLevel level, AtlasContents contents) {
        String overworldKey = net.minecraft.world.level.Level.OVERWORLD.identifier().toString();
        String playerDimension = player.level().dimension().identifier().toString();
        List<AtlasTilePayload> allTiles = new java.util.ArrayList<>();

        java.util.Map<Integer, List<Integer>> mapsByScale = new java.util.TreeMap<>();
        for (int rawId : contents.allMapIds()) {
            MapItemSavedData mapData = level.getMapData(new MapId(rawId));
            if (mapData != null) {
                mapsByScale.computeIfAbsent((int) mapData.scale, _ -> new java.util.ArrayList<>()).add(rawId);
            }
        }

        for (java.util.Map.Entry<Integer, List<Integer>> entryByScale : mapsByScale.entrySet()) {
            int scale = entryByScale.getKey();
            List<Integer> ids = entryByScale.getValue();
            AtlasLayout layout = AtlasLayoutBuilder.build(level, ids);
            for (var entry : layout.entries()) {
                MapItemSavedData mapData = level.getMapData(new MapId(entry.mapId()));
                String dimension = (mapData != null) ? mapData.dimension.identifier().toString() : overworldKey;
                allTiles.add(new AtlasTilePayload(
                        entry.mapId(),
                        entry.centerX(),
                        entry.centerZ(),
                        entry.tileX(),
                        entry.tileY(),
                        dimension,
                        scale
                ));
            }
        }

        return new OpenAtlasScreenPayload(
                allTiles,
                contents.mapIds(),
                contents.waypoints(),
                contents.selectedWaypointIconIndex(),
                contents.nextWaypointNumber(),
                playerDimension
        );
    }

    public static void syncAtlasMapsToPlayer(ServerPlayer player, ServerLevel level, AtlasContents contents) {
        for (int rawId : contents.allMapIds()) {
            MapId mapId = new MapId(rawId);
            MapItemSavedData mapData = level.getMapData(mapId);

            if (mapData == null) {
                continue;
            }

            mapData.getHoldingPlayer(player);
            if (mapData.colors != null && mapData.colors.length > 0) {
                mapData.setColor(0, 0, mapData.colors[0]);
                mapData.setColor(127, 127, mapData.colors[mapData.colors.length - 1]);
            }
            MapModCompat.sendRemappedPackets(player, mapId, mapData);
            Packet<?> packet = mapData.getUpdatePacket(mapId, player);

            if (packet != null) {
                player.connection.send(packet);
            }
        }
    }

    private static boolean hasWaypointAtBlock(AtlasContents contents, BlockPos pos, String dimension) {
        for (AtlasContents.WaypointData waypoint : contents.waypoints()) {
            if (waypoint.dimension().equals(dimension)
                    && Mth.floor(waypoint.worldX()) == pos.getX()
                    && Mth.floor(waypoint.worldZ()) == pos.getZ()) {
                return true;
            }
        }
        return false;
    }

    private static AtlasContents withAppendedWaypoint(AtlasContents contents, AtlasContents.WaypointData waypoint) {
        var updatedWaypoints = new java.util.ArrayList<>(contents.waypoints());
        updatedWaypoints.add(waypoint);
        return contents.withWaypointState(
                updatedWaypoints,
                contents.selectedWaypointIconIndex(),
                contents.nextWaypointNumber() + 1
        );
    }

    private static void removeMapIdIfPresent(ItemStack stack) {
        if (stack.has(DataComponents.MAP_ID)) {
            stack.remove(DataComponents.MAP_ID);
        }
    }
}
