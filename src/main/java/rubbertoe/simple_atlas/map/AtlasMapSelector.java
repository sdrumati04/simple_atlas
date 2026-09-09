package rubbertoe.simple_atlas.map;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class AtlasMapSelector {
    private AtlasMapSelector() {}

    public static @Nullable Integer findCurrentMapRawId(
            Level level,
            double x,
            double z,
            List<Integer> mapIds,
            @Nullable Integer preferredRawId
    ) {
        return findCurrentMapRawId(level, x, z, mapIds, preferredRawId, -1);
    }

    public static @Nullable Integer findCurrentMapRawId(
            Level level,
            double x,
            double z,
            List<Integer> mapIds,
            @Nullable Integer preferredRawId,
            int preferredScale
    ) {
        // If preferredScale is not set (< 0), default to the most detailed scale present in this dimension
        if (preferredScale < 0 && !mapIds.isEmpty()) {
            int minScale = Integer.MAX_VALUE;
            for (int rawId : mapIds) {
                MapItemSavedData data = level.getMapData(new MapId(rawId));
                if (data != null && data.dimension.equals(level.dimension()) && data.scale < minScale) {
                    minScale = data.scale;
                }
            }
            if (minScale != Integer.MAX_VALUE) {
                preferredScale = minScale;
            }
        }

        if (preferredRawId != null && mapIds.contains(preferredRawId)) {
            MapItemSavedData prefData = level.getMapData(new MapId(preferredRawId));
            if (mapContainsPosition(level, x, z, prefData)) {
                if (preferredScale < 0 || prefData.scale == preferredScale) {
                    return preferredRawId;
                }
            }
        }

        Integer bestMapId = null;
        double bestCenterDistanceSq = Double.MAX_VALUE;

        // Strictly search for a matching map covering the position at preferredScale ONLY.
        // If the player enters a zone not mapped at this scale, return null so the atlas
        // displays as the book rather than switching to an unexpected higher scale.
        if (preferredScale >= 0) {
            for (int rawId : mapIds) {
                MapItemSavedData mapData = level.getMapData(new MapId(rawId));
                if (!mapContainsPosition(level, x, z, mapData)) {
                    continue;
                }
                if (mapData.scale == preferredScale) {
                    double dx = x - mapData.centerX;
                    double dz = z - mapData.centerZ;
                    double centerDistanceSq = dx * dx + dz * dz;
                    if (bestMapId == null || centerDistanceSq < bestCenterDistanceSq) {
                        bestMapId = rawId;
                        bestCenterDistanceSq = centerDistanceSq;
                    }
                }
            }
            return bestMapId;
        }

        return null;
    }

    private static boolean mapContainsPosition(Level level, double x, double z, @Nullable MapItemSavedData mapData) {
        if (mapData == null) {
            return false;
        }

        // Never select atlas maps from another dimension.
        if (!mapData.dimension.equals(level.dimension())) {
            return false;
        }

        int scaleFactor = 1 << mapData.scale;
        double mapMinX = mapData.centerX - 64.0 * scaleFactor;
        double mapMinZ = mapData.centerZ - 64.0 * scaleFactor;
        double mapMaxX = mapData.centerX + 64.0 * scaleFactor;
        double mapMaxZ = mapData.centerZ + 64.0 * scaleFactor;
        return x >= mapMinX && x < mapMaxX && z >= mapMinZ && z < mapMaxZ;
    }
}
