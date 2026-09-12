package rubbertoe.simple_atlas.compat;

import net.fabricmc.loader.api.FabricLoader;
import rubbertoe.simple_atlas.item.ModItems;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class ImmersiveOverlaysCompat {
    private static final boolean IMMERSIVE_OVERLAYS_LOADED = FabricLoader.getInstance().isModLoaded("immersiveoverlays");

    private ImmersiveOverlaysCompat() {}

    public static void init() {
        if (!IMMERSIVE_OVERLAYS_LOADED) {
            return;
        }

        try {
            // Update ModConfig so biome overlay includes simple-atlas:atlas,
            // and ensure atlas is NOT treated as a compass (which would cause coordinates like "X: ..." to render).
            Class<?> configClass = Class.forName("cc.cassian.immersiveoverlays.config.ModConfig");
            Method getMethod = configClass.getMethod("get");
            Object config = getMethod.invoke(null);
            if (config != null) {
                // Ensure locator bar requirement for compass is disabled so atlas waypoints show on locator bar
                try {
                    Field locatorBarField = configClass.getField("locator_bar");
                    locatorBarField.setBoolean(config, false);
                } catch (Throwable ignored) {
                }

                // Remove atlas from compass items to prevent unwanted coordinate HUD (e.g. X: -260)
                removeConfigItem(config, "compass_x_items", "simple-atlas:atlas");
                removeConfigItem(config, "compass_anchor_items", "simple-atlas:atlas");

                // Atlas is a map, so show biome overlay
                appendConfigItem(config, "biome_items", "simple-atlas:atlas");
            }

            // Also directly update active ModLists item lists in case loadLists() already ran
            Class<?> listsClass = Class.forName("cc.cassian.immersiveoverlays.helpers.ModLists");
            removeListItem(listsClass, "compass_x_items", ModItems.ATLAS);
            removeListItem(listsClass, "compass_anchor_items", ModItems.ATLAS);
            appendListItem(listsClass, "biome_items", ModItems.ATLAS);
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void appendConfigItem(Object config, String fieldName, String value) {
        try {
            Field field = config.getClass().getField(fieldName);
            Object listObj = field.get(config);
            if (listObj instanceof List list) {
                if (!list.contains(value)) {
                    try {
                        list.add(value);
                    } catch (UnsupportedOperationException e) {
                        List<String> mutable = new ArrayList<>(list);
                        mutable.add(value);
                        field.set(config, mutable);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void removeConfigItem(Object config, String fieldName, String value) {
        try {
            Field field = config.getClass().getField(fieldName);
            Object listObj = field.get(config);
            if (listObj instanceof List list) {
                if (list.contains(value)) {
                    try {
                        list.remove(value);
                    } catch (UnsupportedOperationException e) {
                        List<String> mutable = new ArrayList<>(list);
                        mutable.remove(value);
                        field.set(config, mutable);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void appendListItem(Class<?> listsClass, String fieldName, Object item) {
        try {
            Field field = listsClass.getField(fieldName);
            Object listObj = field.get(null);
            if (listObj instanceof List list) {
                if (!list.contains(item)) {
                    list.add(item);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void removeListItem(Class<?> listsClass, String fieldName, Object item) {
        try {
            Field field = listsClass.getField(fieldName);
            Object listObj = field.get(null);
            if (listObj instanceof List list) {
                list.remove(item);
            }
        } catch (Throwable ignored) {
        }
    }
}
