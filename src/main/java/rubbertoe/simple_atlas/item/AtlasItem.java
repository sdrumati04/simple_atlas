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
import org.jspecify.annotations.Nullable;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
import rubbertoe.simple_atlas.server.AtlasWaypointDecorations;
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
        if (player == null) {
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

        if (contents.transcribing()) {
            tooltipComponents.accept(
                    Component.translatable("tooltip.simple_atlas.transcribing")
                            .withStyle(ChatFormatting.GOLD)
            );
        }

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

        if (contents.blankMapCount() > 0) {
            tooltipComponents.accept(
                    Component.translatable("tooltip.simple_atlas.blank_maps", contents.blankMapCount())
                            .withStyle(ChatFormatting.GRAY)
            );
        }

        if (contents.paperCount() > 0) {
            tooltipComponents.accept(
                    Component.translatable("tooltip.simple_atlas.paper", contents.paperCount())
                            .withStyle(ChatFormatting.GRAY)
            );
        }
    }

    @Override
    public @NonNull InteractionResult use(@NonNull Level level, @NonNull Player player, @NonNull InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ServerLevel serverLevel = (ServerLevel) level;

        ItemStack atlasStack = player.getItemInHand(hand);
        AtlasContents contents = atlasStack.getOrDefault(
                ModComponents.ATLAS_CONTENTS,
                AtlasContents.EMPTY
        );

        if (contents.transcribing()) {
            if (rubbertoe.simple_atlas.cartography.AtlasTranscriptionManager.isTranscribing(contents)) {
                int progress = rubbertoe.simple_atlas.cartography.AtlasTranscriptionManager.getProgress(contents);
                serverLevel.playSound(
                        null,
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        SoundEvents.VILLAGER_WORK_CARTOGRAPHER,
                        SoundSource.PLAYERS,
                        0.8f,
                        1.0f
                );
                player.sendOverlayMessage(
                        Component.translatable("message.simple_atlas.transcribing_progress", progress)
                );
                return InteractionResult.SUCCESS;
            } else {
                contents = contents.withTranscribing(false);
                atlasStack.set(ModComponents.ATLAS_CONTENTS, contents);
            }
        }

        if (contents.mapIds().isEmpty()) {
            if ((contents.blankMapCount() > 0 || player.isCreative()) && contents.canAddMapId()) {
                Integer firstMapId = autoCreateMap(player, atlasStack, serverLevel, contents, true);
                if (firstMapId != null) {
                    contents = atlasStack.getOrDefault(ModComponents.ATLAS_CONTENTS, AtlasContents.EMPTY);
                    player.sendOverlayMessage(buildHeldInfoComponent(atlasStack, contents, serverLevel));
                } else {
                    return InteractionResult.SUCCESS;
                }
            }
        }

        if (contents.mapIds().isEmpty()) {
            serverLevel.playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS,
                    0.8f,
                    1.2f
            );
            player.sendOverlayMessage(
                    Component.translatable("message.simple_atlas.no_maps_inserted")
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

        checkAndSendEquipInfo(player, level);

        boolean heldInHand = slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND;
        if (!heldInHand) {
            removeMapIdIfPresent(stack);
            return;
        }

        AtlasContents contents = stack.getOrDefault(ModComponents.ATLAS_CONTENTS, AtlasContents.EMPTY);

        if (contents.transcribing()) {
            if (rubbertoe.simple_atlas.cartography.AtlasTranscriptionManager.isTranscribing(contents)) {
                if (level.getGameTime() % 20 == 0) {
                    int progress = rubbertoe.simple_atlas.cartography.AtlasTranscriptionManager.getProgress(contents);
                    player.sendOverlayMessage(Component.translatable("message.simple_atlas.transcribing_progress", progress));
                }
            } else {
                contents = contents.withTranscribing(false);
                stack.set(ModComponents.ATLAS_CONTENTS, contents);
            }
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

        boolean justAutoCreated = false;
        if (currentMapRawId == null) {
            if ((contents.blankMapCount() > 0 || player.isCreative()) && contents.canAddMapId()) {
                currentMapRawId = autoCreateMap(player, stack, level, contents, false);
                if (currentMapRawId != null) {
                    contents = stack.getOrDefault(ModComponents.ATLAS_CONTENTS, AtlasContents.EMPTY);
                    LAST_AUTO_EXPAND_WARN_TICK.remove(player.getUUID());
                    justAutoCreated = true;
                }
            } else if (!contents.mapIds().isEmpty()) {
                triggerAutoExpandFailure(player, level, contents);
            }
        }

        if (currentMapRawId == null) {
            removeMapIdIfPresent(stack);
            return;
        }

        MapId targetId = new MapId(currentMapRawId);
        if (!targetId.equals(existingId)) {
            setHeldMapId(stack, targetId);
        } else {
            ensureMapIdTooltipHidden(stack);
        }

        if (justAutoCreated) {
            player.sendOverlayMessage(buildHeldInfoComponent(stack, contents, level));
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
            overlappingMap.getHoldingPlayer(player);
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
            for (AtlasContents.WaypointData waypoint : contents.waypoints()) {
                net.minecraft.world.level.saveddata.maps.MapDecoration decoration = AtlasWaypointDecorations.toDecoration(mapData, waypoint);
                if (decoration != null) {
                    currentDecorations.add(decoration);
                }
            }
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

    private static final Map<UUID, ItemStack> LAST_HELD_MAIN_STACK = new ConcurrentHashMap<>();
    private static final Map<UUID, ItemStack> LAST_HELD_OFF_STACK = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_AUTO_EXPAND_WARN_TICK = new ConcurrentHashMap<>();

    public static void onPlayerDisconnect(UUID playerId) {
        LAST_HELD_MAIN_STACK.remove(playerId);
        LAST_HELD_OFF_STACK.remove(playerId);
        LAST_AUTO_EXPAND_WARN_TICK.remove(playerId);
    }

    private static void checkAndSendEquipInfo(Player player, ServerLevel level) {
        ItemStack currentMain = player.getMainHandItem();
        ItemStack lastMain = LAST_HELD_MAIN_STACK.get(player.getUUID());
        if (currentMain != lastMain) {
            LAST_HELD_MAIN_STACK.put(player.getUUID(), currentMain);
            if (currentMain.is(ModItems.ATLAS)) {
                AtlasContents contents = currentMain.getOrDefault(ModComponents.ATLAS_CONTENTS, AtlasContents.EMPTY);
                if (contents.transcribing() && rubbertoe.simple_atlas.cartography.AtlasTranscriptionManager.isTranscribing(contents)) {
                    int progress = rubbertoe.simple_atlas.cartography.AtlasTranscriptionManager.getProgress(contents);
                    player.sendOverlayMessage(Component.translatable("message.simple_atlas.transcribing_progress", progress));
                } else {
                    player.sendOverlayMessage(buildHeldInfoComponent(currentMain, contents, level));
                }
            }
        }

        ItemStack currentOff = player.getOffhandItem();
        ItemStack lastOff = LAST_HELD_OFF_STACK.get(player.getUUID());
        if (currentOff != lastOff) {
            LAST_HELD_OFF_STACK.put(player.getUUID(), currentOff);
            if (currentOff.is(ModItems.ATLAS) && !currentMain.is(ModItems.ATLAS)) {
                AtlasContents contents = currentOff.getOrDefault(ModComponents.ATLAS_CONTENTS, AtlasContents.EMPTY);
                if (contents.transcribing() && rubbertoe.simple_atlas.cartography.AtlasTranscriptionManager.isTranscribing(contents)) {
                    int progress = rubbertoe.simple_atlas.cartography.AtlasTranscriptionManager.getProgress(contents);
                    player.sendOverlayMessage(Component.translatable("message.simple_atlas.transcribing_progress", progress));
                } else {
                    player.sendOverlayMessage(buildHeldInfoComponent(currentOff, contents, level));
                }
            }
        }
    }

    public static Component buildHeldInfoComponent(ItemStack stack, AtlasContents contents, ServerLevel level) {
        net.minecraft.network.chat.MutableComponent comp = Component.empty();

        comp.append(stack.getHoverName().copy().withStyle(ChatFormatting.GOLD));

        comp.append(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY));
        if (contents.mapIds().isEmpty()) {
            comp.append(Component.translatable("message.simple_atlas.info_no_maps").withStyle(ChatFormatting.GRAY));
        } else {
            int ratio = 1;
            if (contents.selectedScale() >= 0) {
                ratio = 1 << contents.selectedScale();
            } else {
                for (int rawId : contents.mapIds()) {
                    MapItemSavedData data = level.getMapData(new MapId(rawId));
                    if (data != null) {
                        ratio = 1 << data.scale;
                        if (data.dimension.equals(level.dimension())) {
                            break;
                        }
                    }
                }
            }
            comp.append(Component.literal("1:" + ratio).withStyle(ChatFormatting.YELLOW));
        }
        comp.append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY));

        comp.append(Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY));
        comp.append(Component.translatable("message.simple_atlas.info_maps", contents.mapIds().size()).withStyle(ChatFormatting.WHITE));

        comp.append(Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY));
        ChatFormatting blankColor = contents.blankMapCount() > 0 ? ChatFormatting.GREEN : ChatFormatting.RED;
        comp.append(Component.translatable("message.simple_atlas.info_empty", contents.blankMapCount()).withStyle(blankColor));

        comp.append(Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY));
        ChatFormatting paperColor = contents.paperCount() > 0 ? ChatFormatting.GREEN : ChatFormatting.RED;
        comp.append(Component.translatable("message.simple_atlas.info_paper", contents.paperCount()).withStyle(paperColor));

        return comp;
    }

    private static void triggerAutoExpandFailure(Player player, ServerLevel level, AtlasContents contents) {
        long gameTime = level.getGameTime();
        Long lastWarn = LAST_AUTO_EXPAND_WARN_TICK.get(player.getUUID());
        if (lastWarn != null && gameTime - lastWarn < 60 && gameTime >= lastWarn) {
            return;
        }
        LAST_AUTO_EXPAND_WARN_TICK.put(player.getUUID(), gameTime);

        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.DISPENSER_FAIL,
                SoundSource.PLAYERS,
                0.8f,
                1.2f
        );

        if (!contents.canAddMapId()) {
            player.sendOverlayMessage(
                    Component.translatable("message.simple_atlas.map_limit_reached", SimpleAtlasConfigManager.getMaxAtlasMapCount())
            );
        } else if (contents.blankMapCount() <= 0 && !player.isCreative()) {
            player.sendOverlayMessage(
                    Component.translatable("message.simple_atlas.no_empty_maps")
            );
        }
    }

    public static @Nullable Integer autoCreateMap(Player player, ItemStack atlasStack, ServerLevel level, AtlasContents contents) {
        return autoCreateMap(player, atlasStack, level, contents, false);
    }

    public static @Nullable Integer autoCreateMap(Player player, ItemStack atlasStack, ServerLevel level, AtlasContents contents, boolean isManualUse) {
        if (!contents.canAddMapId()) {
            return null;
        }
        if (contents.blankMapCount() <= 0 && !player.isCreative()) {
            return null;
        }

        int targetScale = contents.selectedScale() >= 0 ? contents.selectedScale() : 0;
        if (contents.selectedScale() < 0 && !contents.mapIds().isEmpty()) {
            for (int rawId : contents.mapIds()) {
                MapItemSavedData data = level.getMapData(new MapId(rawId));
                if (data != null && data.dimension.equals(level.dimension())) {
                    targetScale = data.scale;
                    break;
                }
            }
        }
        if (targetScale < 0) {
            targetScale = 0;
        }

        int requiredPaper = targetScale;
        if (targetScale > 0 && SimpleAtlasConfigManager.isConsumePaperForHigherScales() && !player.isCreative()) {
            if (contents.paperCount() < requiredPaper) {
                long gameTime = level.getGameTime();
                Long lastWarn = LAST_AUTO_EXPAND_WARN_TICK.get(player.getUUID());
                if (isManualUse || lastWarn == null || gameTime - lastWarn >= 60 || gameTime < lastWarn) {
                    LAST_AUTO_EXPAND_WARN_TICK.put(player.getUUID(), gameTime);
                    level.playSound(
                            null,
                            player.getX(),
                            player.getY(),
                            player.getZ(),
                            SoundEvents.DISPENSER_FAIL,
                            SoundSource.PLAYERS,
                            0.8f,
                            1.2f
                    );
                    player.sendOverlayMessage(
                            Component.translatable("message.simple_atlas.not_enough_paper", requiredPaper)
                    );
                }
                return null;
            }
        }

        int blockX = (int) Math.floor(player.getX());
        int blockZ = (int) Math.floor(player.getZ());
        ItemStack newMapStack = MapItem.create(level, blockX, blockZ, (byte) targetScale, true, false);
        MapId newMapId = newMapStack.get(DataComponents.MAP_ID);
        if (newMapId == null) {
            return null;
        }

        int newBlankCount = player.isCreative() ? contents.blankMapCount() : Math.max(0, contents.blankMapCount() - 1);
        int newPaperCount = (player.isCreative() || targetScale <= 0 || !SimpleAtlasConfigManager.isConsumePaperForHigherScales())
                ? contents.paperCount()
                : Math.max(0, contents.paperCount() - requiredPaper);
        AtlasContents updated = contents
                .withBlankMapCount(newBlankCount)
                .withPaperCount(newPaperCount)
                .withAdded(newMapId.id());
        if (contents.selectedScale() < 0) {
            updated = updated.withSelectedScale(targetScale);
        }
        atlasStack.set(ModComponents.ATLAS_CONTENTS, updated);
        setHeldMapId(atlasStack, newMapId);

        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,
                SoundSource.PLAYERS,
                1.0f,
                1.0f
        );

        if (player instanceof ServerPlayer serverPlayer) {
            MapItemSavedData mapData = level.getMapData(newMapId);
            if (mapData != null) {
                MapModCompat.sendRemappedPackets(serverPlayer, newMapId, mapData);
            }
        }

        return newMapId.id();
    }

    public static void setHeldMapId(ItemStack stack, MapId mapId) {
        stack.set(DataComponents.MAP_ID, mapId);
        ensureMapIdTooltipHidden(stack);
    }

    public static void ensureMapIdTooltipHidden(ItemStack stack) {
        TooltipDisplay display = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        if (display.shows(DataComponents.MAP_ID)) {
            stack.set(DataComponents.TOOLTIP_DISPLAY, display.withHidden(DataComponents.MAP_ID, true));
        }
    }

    private static void removeMapIdIfPresent(ItemStack stack) {
        if (stack.has(DataComponents.MAP_ID)) {
            stack.remove(DataComponents.MAP_ID);
        }
    }
}
