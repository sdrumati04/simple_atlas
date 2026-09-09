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
        if (preferredRawId != null && mapIds.contains(preferredRawId)) {
            MapItemSavedData prefData = level.getMapData(new MapId(preferredRawId));
            if (mapContainsPosition(level, x, z, prefData)) {
                if (preferredScale < 0 || prefData.scale == preferredScale) {
                    return preferredRawId;
                }
            }
        }

        Integer bestMapId = null;
        int bestScaleFactor = Integer.MAX_VALUE;
        double bestCenterDistanceSq = Double.MAX_VALUE;

        // If preferredScale is specified, first search for a matching map covering the position
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
            if (bestMapId != null) {
                return bestMapId;
            }
        }

        // Fallback: prefer the most detailed map when coverage overlaps.
        for (int rawId : mapIds) {
            MapItemSavedData mapData = level.getMapData(new MapId(rawId));
            if (!mapContainsPosition(level, x, z, mapData)) {
                continue;
            }

            double dx = x - mapData.centerX;
            double dz = z - mapData.centerZ;
            double centerDistanceSq = dx * dx + dz * dz;
            int scaleFactor = 1 << mapData.scale;

            if (scaleFactor < bestScaleFactor
                    || (scaleFactor == bestScaleFactor && centerDistanceSq < bestCenterDistanceSq)) {
                bestMapId = rawId;
                bestScaleFactor = scaleFactor;
                bestCenterDistanceSq = centerDistanceSq;
            }
        }

        return bestMapId;
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
