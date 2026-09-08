package rubbertoe.simple_atlas.compat;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class MapModCompat {
    private static final boolean REMAPPED_LOADED = FabricLoader.getInstance().isModLoaded("remapped");
    private static Method getRemappedPacketsMethod = null;
    private static Method getRemappedColorsMethod = null;
    private static Method setRemappedColorMethod = null;
    private static Method setRemappedColorsMethod = null;

    static {
        if (REMAPPED_LOADED) {
            try {
                Class<?> duckClass = Class.forName("dev.worldgen.remapped.duck.MapDataDuck");
                getRemappedPacketsMethod = duckClass.getMethod("getRemappedPackets", MapId.class, ServerPlayer.class);
                getRemappedColorsMethod = duckClass.getMethod("getRemappedColors");
                setRemappedColorMethod = duckClass.getMethod("setRemappedColor", int.class, int.class, int.class);
                setRemappedColorsMethod = duckClass.getMethod("setRemappedColors", List.class);
            } catch (Throwable ignored) {
                getRemappedPacketsMethod = null;
                getRemappedColorsMethod = null;
                setRemappedColorMethod = null;
                setRemappedColorsMethod = null;
            }
        }
    }

    private MapModCompat() {}

    public static boolean isRemappedLoaded() {
        return REMAPPED_LOADED && getRemappedPacketsMethod != null;
    }

    @SuppressWarnings("unchecked")
    public static ArrayList<Integer> getRemappedColors(MapItemSavedData mapData) {
        if (getRemappedColorsMethod != null && mapData != null) {
            try {
                return (ArrayList<Integer>) getRemappedColorsMethod.invoke(mapData);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static int getRemappedColor(MapItemSavedData mapData, int x, int y) {
        return getRemappedColor(mapData, x + y * 128);
    }

    public static int getRemappedColor(MapItemSavedData mapData, int index) {
        if (index < 0 || index >= 16384 || mapData == null) {
            return 0;
        }
        ArrayList<Integer> colors = getRemappedColors(mapData);
        if (colors != null && index < colors.size()) {
            Integer c = colors.get(index);
            return c != null ? c : 0;
        }
        return 0;
    }

    public static void setRemappedColor(MapItemSavedData mapData, int x, int y, int color) {
        if (setRemappedColorMethod != null && mapData != null && x >= 0 && x < 128 && y >= 0 && y < 128) {
            try {
                setRemappedColorMethod.invoke(mapData, x, y, color);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void setRemappedColors(MapItemSavedData mapData, List<Integer> colors) {
        if (setRemappedColorsMethod != null && mapData != null && colors != null) {
            try {
                setRemappedColorsMethod.invoke(mapData, colors);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void sendRemappedPackets(ServerPlayer player, MapId mapId, MapItemSavedData mapData) {
        if (getRemappedPacketsMethod != null && mapData != null && player != null) {
            try {
                @SuppressWarnings("unchecked")
                List<CustomPacketPayload> packets = (List<CustomPacketPayload>) getRemappedPacketsMethod.invoke(mapData, mapId, player);
                if (packets != null) {
                    for (CustomPacketPayload packet : packets) {
                        ServerPlayNetworking.send(player, packet);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
