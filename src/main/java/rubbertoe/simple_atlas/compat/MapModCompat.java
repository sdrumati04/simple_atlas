package rubbertoe.simple_atlas.compat;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class MapModCompat {
    private static final boolean REMAPPED_LOADED = FabricLoader.getInstance().isModLoaded("remapped");
    private static Method getRemappedPacketsMethod = null;
    private static Method getRemappedColorsMethod = null;
    private static Method setRemappedColorMethod = null;
    private static Method setRemappedColorsMethod = null;
    private static Method getMatchingColorMethod = null;
    private static Method putColorMethod = null;
    private static Method getDuckColorMethod = null;
    private static Method useDitheringMethod = null;
    private static Method emptyDuckMethod = null;
    private static Method getHolderMethod = null;
    private static Object dirtBrownKey = null;
    private static Object stoneGrayKey = null;
    private static ResourceKey<?> remappedColorRegistryKey = null;

    static {
        if (REMAPPED_LOADED) {
            try {
                Class<?> duckClass = Class.forName("dev.worldgen.remapped.duck.MapDataDuck");
                getRemappedPacketsMethod = duckClass.getMethod("getRemappedPackets", MapId.class, ServerPlayer.class);
                getRemappedColorsMethod = duckClass.getMethod("getRemappedColors");
                setRemappedColorMethod = duckClass.getMethod("setRemappedColor", int.class, int.class, int.class);
                setRemappedColorsMethod = duckClass.getMethod("setRemappedColors", List.class);

                Class<?> utilsClass = Class.forName("dev.worldgen.remapped.util.RemappedUtils");
                Class<?> mapColorDuckClass = Class.forName("dev.worldgen.remapped.duck.MapColorDuck");
                getMatchingColorMethod = utilsClass.getMethod("getMatchingColor", Level.class, BlockPos.class, BlockState.class);
                putColorMethod = utilsClass.getMethod("putColor", MapItemSavedData.class, int.class, int.class, mapColorDuckClass, int.class);
                getDuckColorMethod = mapColorDuckClass.getMethod("getColor");
                useDitheringMethod = mapColorDuckClass.getMethod("useDithering");
                emptyDuckMethod = mapColorDuckClass.getMethod("empty");

                try {
                    Class<?> remappedColorClass = Class.forName("dev.worldgen.remapped.color.RemappedColor");
                    remappedColorRegistryKey = (ResourceKey<?>) remappedColorClass.getField("REGISTRY_KEY").get(null);
                    dirtBrownKey = utilsClass.getField("DIRT_BROWN").get(null);
                    stoneGrayKey = utilsClass.getField("STONE_GRAY").get(null);
                    getHolderMethod = utilsClass.getMethod("get", Registry.class, ResourceKey.class);
                } catch (Throwable ignored) {
                }
            } catch (Throwable ignored) {
                getRemappedPacketsMethod = null;
                getRemappedColorsMethod = null;
                setRemappedColorMethod = null;
                setRemappedColorsMethod = null;
                getMatchingColorMethod = null;
                putColorMethod = null;
                getDuckColorMethod = null;
                useDitheringMethod = null;
                emptyDuckMethod = null;
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
                        // Exclude Remapped's BaseMapUpdatePacket which carries unaugmented vanilla decorations
                        // and clears client-side atlas waypoints.
                        if (packet != null && !"base_map_update".equals(packet.type().id().getPath())) {
                            ServerPlayNetworking.send(player, packet);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public static @Nullable Object getMatchingColor(Level level, BlockPos pos, BlockState state) {
        if (getMatchingColorMethod != null) {
            try {
                return getMatchingColorMethod.invoke(null, level, pos, state);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static void putColor(MapItemSavedData mapData, int x, int y, Object colorDuck, int brightness) {
        if (putColorMethod != null && colorDuck != null) {
            try {
                putColorMethod.invoke(null, mapData, x, y, colorDuck, brightness);
            } catch (Throwable ignored) {
            }
        }
    }

    public static int getDuckColor(Object colorDuck) {
        if (getDuckColorMethod != null && colorDuck != null) {
            try {
                return (int) getDuckColorMethod.invoke(colorDuck);
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    public static boolean useDithering(Object colorDuck) {
        if (useDitheringMethod != null && colorDuck != null) {
            try {
                return (boolean) useDitheringMethod.invoke(colorDuck);
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public static @Nullable Object getDuckEmpty() {
        if (emptyDuckMethod != null) {
            try {
                return emptyDuckMethod.invoke(null);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static @Nullable Object getRemappedDirt(Level level) {
        if (getHolderMethod != null && dirtBrownKey != null && remappedColorRegistryKey != null && level != null) {
            try {
                Registry<?> reg = level.registryAccess().lookupOrThrow((ResourceKey) remappedColorRegistryKey);
                Holder<?> holder = (Holder<?>) getHolderMethod.invoke(null, reg, dirtBrownKey);
                return holder != null ? holder.value() : null;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static @Nullable Object getRemappedStone(Level level) {
        if (getHolderMethod != null && stoneGrayKey != null && remappedColorRegistryKey != null && level != null) {
            try {
                Registry<?> reg = level.registryAccess().lookupOrThrow((ResourceKey) remappedColorRegistryKey);
                Holder<?> holder = (Holder<?>) getHolderMethod.invoke(null, reg, stoneGrayKey);
                return holder != null ? holder.value() : null;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static boolean isEmptyMap(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.is(Items.MAP)) {
            return true;
        }
        return isRemappedEmptyMap(stack);
    }

    public static boolean isRemappedEmptyMap(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getNamespace().equals("remapped") && id.getPath().equals("empty_map");
    }
}
