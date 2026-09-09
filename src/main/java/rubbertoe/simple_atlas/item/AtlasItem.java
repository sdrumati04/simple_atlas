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

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import rubbertoe.simple_atlas.network.ModNetworking;
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
        String dimensionKey = ((ServerLevel) level).dimension().identifier().toString();
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

        level.playSound(null, bannerPos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1.0f, 1.0f);
        player.sendOverlayMessage(
                Component.translatable("message.simple_atlas.waypoint_added", waypointName)
        );

        if (player instanceof ServerPlayer serverPlayer) {
            ModCriteria.WAYPOINT_ADDED_VIA_BANNER.trigger(serverPlayer);
            ModNetworking.sendImmediateWaypointRefresh(serverPlayer, updated);
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
            java.util.TreeSet<Integer> ratios = new java.util.TreeSet<>();
            for (int rawId : contents.mapIds()) {
                MapItemSavedData mapData = context.mapData(new MapId(rawId));
                if (mapData != null) {
                    ratios.add(1 << mapData.scale);
                }
            }
            if (!ratios.isEmpty()) {
                int activeRatio = (contents.selectedScale() >= 0) ? (1 << contents.selectedScale()) : ratios.first();
                tooltipComponents.accept(
                        Component.translatable("tooltip.simple_atlas.scale", activeRatio)
                                .withStyle(ChatFormatting.GRAY)
                );
                if (ratios.size() > 1) {
                    String scalesStr = ratios.stream().map(r -> "1:" + r).collect(java.util.stream.Collectors.joining(", "));
                    tooltipComponents.accept(
                            Component.translatable("tooltip.simple_atlas.scales", scalesStr)
                                    .withStyle(ChatFormatting.DARK_GRAY)
                    );
                }
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

        syncAtlasMapsToPlayer((ServerPlayer) player, serverLevel, contents);
        AtlasViewManager.startViewing((ServerPlayer) player, contents.mapIds());
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
                preferredRawId,
                contents.selectedScale()
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

        // Multi-scale live exploration: also update any overlapping maps of different scales covering the player
        java.util.Map<Integer, MapItemSavedData> mapsByScale = new java.util.HashMap<>();
        for (int rawId : contents.mapIds()) {
            if (rawId == currentMapRawId) {
                continue;
            }
            MapItemSavedData data = level.getMapData(new MapId(rawId));
            if (data != null && !data.locked && data.dimension.equals(level.dimension())) {
                int halfSpan = 64 << data.scale;
                if (Math.abs(player.getX() - data.centerX) <= halfSpan && Math.abs(player.getZ() - data.centerZ) <= halfSpan) {
                    mapsByScale.putIfAbsent((int) data.scale, data);
                }
            }
        }
        for (MapItemSavedData overlappingMap : mapsByScale.values()) {
            overlappingMap.tickCarriedBy(player, stack, null);
            ((MapItem) Items.FILLED_MAP).update(level, player, overlappingMap);
        }
    }

    public static OpenAtlasScreenPayload createOpenPayload(ServerPlayer player, ServerLevel level, AtlasContents contents) {
        String playerDimension = player.level().dimension().identifier().toString();
        List<AtlasTilePayload> allTiles = new java.util.ArrayList<>();

        // Group maps by Dimension -> Scale -> List<MapId>
        java.util.Map<String, java.util.Map<Integer, List<Integer>>> mapsByDimAndScale = new java.util.LinkedHashMap<>();
        for (int rawId : contents.mapIds()) {
            MapItemSavedData mapData = level.getMapData(new MapId(rawId));
            if (mapData != null) {
                String dimKey = mapData.dimension.identifier().toString();
                mapsByDimAndScale
                        .computeIfAbsent(dimKey, _ -> new java.util.TreeMap<>())
                        .computeIfAbsent((int) mapData.scale, _ -> new java.util.ArrayList<>())
                        .add(rawId);
            }
        }

        for (var dimEntry : mapsByDimAndScale.entrySet()) {
            String dimKey = dimEntry.getKey();
            for (var scaleEntry : dimEntry.getValue().entrySet()) {
                int scale = scaleEntry.getKey();
                List<Integer> ids = scaleEntry.getValue();
                AtlasLayout layout = AtlasLayoutBuilder.build(level, ids);
                for (var entry : layout.entries()) {
                    allTiles.add(new AtlasTilePayload(
                            entry.mapId(),
                            entry.centerX(),
                            entry.centerZ(),
                            entry.tileX(),
                            entry.tileY(),
                            dimKey,
                            scale
                    ));
                }
            }
        }

        return new OpenAtlasScreenPayload(
                allTiles,
                contents.mapIds(),
                contents.waypoints(),
                contents.selectedWaypointIconIndex(),
                contents.nextWaypointNumber(),
                playerDimension,
                contents.selectedScale()
        );
    }

    public static void syncAtlasMapsToPlayer(ServerPlayer player, ServerLevel level, AtlasContents contents) {
        for (int rawId : contents.mapIds()) {
            MapId mapId = new MapId(rawId);
            MapItemSavedData mapData = level.getMapData(mapId);

            if (mapData == null) {
                continue;
            }

            mapData.getHoldingPlayer(player);
            // Send full map patch directly to client without marking map dirty on disk
            List<net.minecraft.world.level.saveddata.maps.MapDecoration> currentDecorations = new ArrayList<>();
            mapData.getDecorations().forEach(currentDecorations::add);
            Packet<?> packet = new net.minecraft.network.protocol.game.ClientboundMapItemDataPacket(
                    mapId,
                    mapData.scale,
                    mapData.locked,
                    java.util.Optional.of(currentDecorations),
                    java.util.Optional.of(new MapItemSavedData.MapPatch(0, 0, 128, 128, mapData.colors))
            );
            player.connection.send(packet);
            MapModCompat.sendRemappedPackets(player, mapId, mapData);
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
