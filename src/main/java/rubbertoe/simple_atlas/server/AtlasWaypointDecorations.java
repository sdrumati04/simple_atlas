package rubbertoe.simple_atlas.server;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import rubbertoe.simple_atlas.component.AtlasContents;
import rubbertoe.simple_atlas.map.ModMapDecorationTypes;
import rubbertoe.simple_atlas.navigation.WaypointIconCatalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AtlasWaypointDecorations {
    private AtlasWaypointDecorations() {}

    public static Packet<?> createFullWaypointPacket(
            MapId mapId,
            MapItemSavedData mapData,
            AtlasContents contents
    ) {
        List<MapDecoration> merged = new ArrayList<>();
        mapData.getDecorations().forEach(merged::add);
        for (AtlasContents.WaypointData waypoint : contents.waypoints()) {
            MapDecoration decoration = toDecoration(mapData, waypoint);
            if (decoration != null) {
                merged.add(decoration);
            }
        }
        return new ClientboundMapItemDataPacket(
                mapId,
                mapData.scale,
                mapData.locked,
                Optional.of(merged),
                Optional.empty()
        );
    }

    public static Packet<?> withAtlasWaypointDecorations(
            Packet<?> packet,
            MapItemSavedData mapData,
            AtlasContents contents
    ) {
        return withAtlasWaypointDecorations(packet, mapData, contents, true);
    }

    public static Packet<?> withAtlasWaypointDecorations(
            Packet<?> packet,
            MapItemSavedData mapData,
            AtlasContents contents,
            boolean includeAtlasWaypoints
    ) {
        if (!(packet instanceof ClientboundMapItemDataPacket(
                net.minecraft.world.level.saveddata.maps.MapId mapId, byte scale, boolean locked,
                Optional<List<MapDecoration>> decorations, Optional<MapItemSavedData.MapPatch> colorPatch
        ))) {
            return packet;
        }

        if (!includeAtlasWaypoints) {
            return packet;
        }

        // Vanilla omits decoration payload on many ticks; if we force an empty list here,
        // client decorations are cleared and the player marker appears to blink.
        if (decorations.isEmpty()) {
            return packet;
        }

        List<MapDecoration> merged = new ArrayList<>(decorations.orElseGet(List::of));
        for (AtlasContents.WaypointData waypoint : contents.waypoints()) {
            MapDecoration decoration = toDecoration(mapData, waypoint);
            if (decoration != null && !merged.contains(decoration)) {
                merged.add(decoration);
            }
        }

        return new ClientboundMapItemDataPacket(
                mapId,
                scale,
                locked,
                Optional.of(merged),
                colorPatch
        );
    }

    public static MapDecoration toDecoration(MapItemSavedData mapData, AtlasContents.WaypointData waypoint) {
        if (!mapData.dimension.identifier().toString().equals(waypoint.dimension())) {
            return null;
        }

        int scaleFactor = 1 << mapData.scale;
        float xDeltaFromCenter = (float) ((waypoint.worldX() - mapData.centerX) / scaleFactor);
        float zDeltaFromCenter = (float) ((waypoint.worldZ() - mapData.centerZ) / scaleFactor);

        if (xDeltaFromCenter < -64.0F || xDeltaFromCenter > 64.0F || zDeltaFromCenter < -64.0F || zDeltaFromCenter > 64.0F) {
            return null;
        }

        return new MapDecoration(
                decorationTypeForWaypoint(waypoint.iconIndex()),
                clampMapCoordinate(xDeltaFromCenter),
                clampMapCoordinate(zDeltaFromCenter),
                // Vanilla target decorations use 180deg defaults, which avoids upside-down variants.
                (byte) 8,
                waypoint.name().isBlank() ? Optional.empty() : Optional.of(Component.literal(waypoint.name()))
        );
    }

    public static byte clampMapCoordinate(float deltaFromCenter) {
        float coord = deltaFromCenter * 2.0F;
        if (coord <= -128.0F) {
            return -128;
        }
        if (coord >= 127.0F) {
            return 127;
        }
        return (byte) Math.round(coord);
    }

    public static Holder<MapDecorationType> decorationTypeForWaypoint(int iconIndex) {
        String key = WaypointIconCatalog.getAvailableIconKeys().get(WaypointIconCatalog.sanitizeIconIndex(iconIndex));
        return switch (key) {
            case "home" -> ModMapDecorationTypes.HOME;
            case "nether_portal" -> ModMapDecorationTypes.NETHER_PORTAL;
            case "red_x" -> MapDecorationTypes.RED_X;
            case "target_point" -> MapDecorationTypes.TARGET_POINT;
            case "target_x" -> MapDecorationTypes.TARGET_X;
            case "skull" -> ModMapDecorationTypes.SKULL;
            case "woodland_mansion" -> MapDecorationTypes.WOODLAND_MANSION;
            case "ocean_monument" -> MapDecorationTypes.OCEAN_MONUMENT;
            case "plains_village" -> MapDecorationTypes.PLAINS_VILLAGE;
            case "savanna_village" -> MapDecorationTypes.SAVANNA_VILLAGE;
            case "snowy_village" -> MapDecorationTypes.SNOWY_VILLAGE;
            case "taiga_village" -> MapDecorationTypes.TAIGA_VILLAGE;
            case "jungle_temple" -> MapDecorationTypes.JUNGLE_TEMPLE;
            case "swamp_hut" -> MapDecorationTypes.SWAMP_HUT;
            case "trial_chambers" -> MapDecorationTypes.TRIAL_CHAMBERS;
            case "axe" -> ModMapDecorationTypes.AXE;
            case "pickaxe" -> ModMapDecorationTypes.PICKAXE;
            case "shovel" -> ModMapDecorationTypes.SHOVEL;
            case "sword" -> ModMapDecorationTypes.SWORD;
            case "coal" -> ModMapDecorationTypes.COAL;
            case "copper_ingot" -> ModMapDecorationTypes.COPPER_INGOT;
            case "diamond" -> ModMapDecorationTypes.DIAMOND;
            case "emerald" -> ModMapDecorationTypes.EMERALD;
            case "gold_ingot" -> ModMapDecorationTypes.GOLD_INGOT;
            case "iron_ingot" -> ModMapDecorationTypes.IRON_INGOT;
            case "lapis_lazuli" -> ModMapDecorationTypes.LAPIS_LAZULI;
            case "redstone_dust" -> ModMapDecorationTypes.REDSTONE_DUST;
            case "white_banner" -> MapDecorationTypes.WHITE_BANNER;
            case "orange_banner" -> MapDecorationTypes.ORANGE_BANNER;
            case "magenta_banner" -> MapDecorationTypes.MAGENTA_BANNER;
            case "light_blue_banner" -> MapDecorationTypes.LIGHT_BLUE_BANNER;
            case "yellow_banner" -> MapDecorationTypes.YELLOW_BANNER;
            case "lime_banner" -> MapDecorationTypes.LIME_BANNER;
            case "pink_banner" -> MapDecorationTypes.PINK_BANNER;
            case "gray_banner" -> MapDecorationTypes.GRAY_BANNER;
            case "light_gray_banner" -> MapDecorationTypes.LIGHT_GRAY_BANNER;
            case "cyan_banner" -> MapDecorationTypes.CYAN_BANNER;
            case "purple_banner" -> MapDecorationTypes.PURPLE_BANNER;
            case "blue_banner" -> MapDecorationTypes.BLUE_BANNER;
            case "brown_banner" -> MapDecorationTypes.BROWN_BANNER;
            case "green_banner" -> MapDecorationTypes.GREEN_BANNER;
            case "red_banner" -> MapDecorationTypes.RED_BANNER;
            case "black_banner" -> MapDecorationTypes.BLACK_BANNER;
            // These atlas-only icon styles do not have vanilla map sprites.
            default -> MapDecorationTypes.RED_MARKER;
        };
    }
}
