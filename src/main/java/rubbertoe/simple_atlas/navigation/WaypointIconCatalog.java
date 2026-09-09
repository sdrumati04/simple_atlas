package rubbertoe.simple_atlas.navigation;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.waypoints.WaypointStyleAsset;
import net.minecraft.world.waypoints.WaypointStyleAssets;
import rubbertoe.simple_atlas.SimpleAtlas;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public final class WaypointIconCatalog {
    private static final List<String> ICON_KEYS = List.of(
            "home",
            "nether_portal",
            "red_x",
            "target_point",
            "target_x",
            "skull",
            "jungle_temple",
            "ocean_monument",
            "plains_village",
            "savanna_village",
            "snowy_village",
            "swamp_hut",
            "taiga_village",
            "trial_chambers",
            "woodland_mansion",
            "axe",
            "pickaxe",
            "shovel",
            "sword",
            "coal",
            "copper_ingot",
            "diamond",
            "emerald",
            "gold_ingot",
            "iron_ingot",
            "lapis_lazuli",
            "redstone_dust",
            "red_banner",
            "orange_banner",
            "yellow_banner",
            "lime_banner",
            "green_banner",
            "cyan_banner",
            "light_blue_banner",
            "blue_banner",
            "purple_banner",
            "magenta_banner",
            "pink_banner",
            "white_banner",
            "light_gray_banner",
            "gray_banner",
            "black_banner",
            "brown_banner"
    );

    private WaypointIconCatalog() {}

    public static List<String> getAvailableIconKeys() {
        return ICON_KEYS;
    }

    public static int sanitizeIconIndex(int index) {
        return ICON_KEYS.isEmpty() ? 0 : Math.floorMod(index, ICON_KEYS.size());
    }

    public static int bannerIconIndexForColor(DyeColor color) {
        if (ICON_KEYS.isEmpty()) {
            return 0;
        }

        String bannerKey = color.getName() + "_banner";
        int colorMatch = ICON_KEYS.indexOf(bannerKey);
        if (colorMatch >= 0) {
            return colorMatch;
        }

        int redFallback = ICON_KEYS.indexOf("red_banner");
        return Math.max(redFallback, 0);
    }

    public static ResourceKey<WaypointStyleAsset> styleKeyForIndex(int iconIndex) {
        String key = ICON_KEYS.get(sanitizeIconIndex(iconIndex));
        return ResourceKey.create(
                WaypointStyleAssets.ROOT_ID,
                Identifier.fromNamespaceAndPath(SimpleAtlas.MOD_ID, key)
        );
    }

    public static UUID navigationWaypointId(String dimension, double worldX, double worldZ) {
        int x = (int) Math.floor(worldX);
        int z = (int) Math.floor(worldZ);
        String seed = "simple_atlas_nav:" + (dimension != null ? dimension : "") + ":" + x + ":" + z;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    public static UUID navigationWaypointId(double worldX, double worldZ) {
        return navigationWaypointId("", worldX, worldZ);
    }
}

