package rubbertoe.simple_atlas.cartography;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.Nullable;
import rubbertoe.simple_atlas.compat.MapModCompat;
import rubbertoe.simple_atlas.component.AtlasContents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class AtlasCartographyScaler {
    private static final int MAP_SIZE = 128;
    private static final int MAP_PIXEL_COUNT = MAP_SIZE * MAP_SIZE;

    private AtlasCartographyScaler() {}

    // ----- Atlas-wide upscale (Paper + Atlas) -----

    public static boolean canScaleAtlas(ServerLevel level, AtlasContents contents) {
        return validateScaleInputs(level, contents);
    }

    public static @Nullable AtlasContents scaleAtlas(ServerLevel level, AtlasContents contents) {
        if (!validateScaleInputs(level, contents)) {
            return null;
        }

        // Determine the highest scale present in the atlas that can still be scaled (< MAX_SCALE).
        // This ensures successive paper additions progressively create the next overview tier:
        // Scale 0 -> Scale 1, then Scale 1 -> Scale 2, etc.
        int targetScale = -1;
        for (int rawId : contents.mapIds()) {
            MapItemSavedData mapData = level.getMapData(new MapId(rawId));
            if (mapData != null && !mapData.locked && mapData.scale < MapItemSavedData.MAX_SCALE) {
                if (mapData.scale > targetScale) {
                    targetScale = mapData.scale;
                }
            }
        }

        if (targetScale < 0) {
            return null;
        }

        LinkedHashMap<ScaledMapKey, MapItemSavedData> scaledByKey = new LinkedHashMap<>();
        LinkedHashMap<ScaledMapKey, ProjectionAccumulator> projectionByKey = new LinkedHashMap<>();

        for (int rawId : contents.mapIds()) {
            MapItemSavedData mapData = level.getMapData(new MapId(rawId));
            if (mapData == null || mapData.locked || mapData.scale != targetScale) {
                continue;
            }

            MapItemSavedData scaled = mapData.scaled();
            ScaledMapKey key = ScaledMapKey.from(scaled);
            MapItemSavedData target = scaledByKey.computeIfAbsent(key, _ -> scaled);
            ProjectionMask projection = projectKnownPixelsIntoScaledMap(mapData, target);
            projectionByKey.computeIfAbsent(key, _ -> new ProjectionAccumulator()).merge(projection);
        }

        if (scaledByKey.isEmpty()) {
            return null;
        }

        if (!contents.canAddMapCount(scaledByKey.size())) {
            return null;
        }

        LinkedHashSet<Integer> newMapIds = new LinkedHashSet<>();
        for (Map.Entry<ScaledMapKey, MapItemSavedData> entry : scaledByKey.entrySet()) {
            ProjectionAccumulator projection = projectionByKey.get(entry.getKey());
            if (projection != null) {
                applyExplorationEdgeShading(entry.getValue(), projection.newlyFilled, projection.projectedCoverage);
            }
            MapId newId = level.getFreeMapId();
            level.setMapData(newId, entry.getValue());
            newMapIds.add(newId.id());
        }

        return contents.withAddedAll(newMapIds);
    }

    private static boolean validateScaleInputs(ServerLevel level, AtlasContents contents) {
        if (contents.mapIds().isEmpty() || !contents.canAddMapId()) {
            return false;
        }

        for (int rawId : contents.mapIds()) {
            MapItemSavedData mapData = level.getMapData(new MapId(rawId));
            if (mapData != null && !mapData.locked && mapData.scale < MapItemSavedData.MAX_SCALE) {
                return true;
            }
        }

        return false;
    }

    // ----- Shared pixel projection -----

    private static ProjectionMask projectKnownPixelsIntoScaledMap(MapItemSavedData source, MapItemSavedData target) {
        boolean[] newlyFilled = new boolean[MAP_PIXEL_COUNT];
        boolean[] projectedCoverage = new boolean[MAP_PIXEL_COUNT];

        int sourceScaleFactor = 1 << source.scale;
        int targetScaleFactor = 1 << target.scale;

        double sourceMinX = source.centerX - 64.0 * sourceScaleFactor;
        double sourceMinZ = source.centerZ - 64.0 * sourceScaleFactor;
        double targetMinX = target.centerX - 64.0 * targetScaleFactor;
        double targetMinZ = target.centerZ - 64.0 * targetScaleFactor;

        boolean hasRemapped = MapModCompat.isRemappedLoaded();
        ArrayList<Integer> sourceRem = hasRemapped ? MapModCompat.getRemappedColors(source) : null;
        ArrayList<Integer> targetRem = hasRemapped ? MapModCompat.getRemappedColors(target) : null;
        if (hasRemapped && (targetRem == null || targetRem.size() != MAP_PIXEL_COUNT)) {
            targetRem = new ArrayList<>(Collections.nCopies(MAP_PIXEL_COUNT, 0));
        }

        for (int sourceY = 0; sourceY < MAP_SIZE; sourceY++) {
            for (int sourceX = 0; sourceX < MAP_SIZE; sourceX++) {
                int srcIdx = sourceX + sourceY * MAP_SIZE;
                byte color = source.colors[srcIdx];
                if (color == 0) {
                    continue;
                }

                double sampleWorldX = sourceMinX + (sourceX + 0.5) * sourceScaleFactor;
                double sampleWorldZ = sourceMinZ + (sourceY + 0.5) * sourceScaleFactor;

                int targetX = (int) Math.floor((sampleWorldX - targetMinX) / targetScaleFactor);
                int targetY = (int) Math.floor((sampleWorldZ - targetMinZ) / targetScaleFactor);
                if (targetX < 0 || targetX >= MAP_SIZE || targetY < 0 || targetY >= MAP_SIZE) {
                    continue;
                }

                int targetIndex = targetX + targetY * MAP_SIZE;
                projectedCoverage[targetIndex] = true;

                if (target.colors[targetIndex] == 0) {
                    target.setColor(targetX, targetY, color);
                    newlyFilled[targetIndex] = true;
                    if (targetRem != null && sourceRem != null && srcIdx < sourceRem.size()) {
                        int remColor = sourceRem.get(srcIdx);
                        if (remColor != 0) {
                            targetRem.set(targetIndex, remColor);
                        }
                    }
                }
            }
        }

        if (targetRem != null) {
            MapModCompat.setRemappedColors(target, targetRem);
        }

        return new ProjectionMask(newlyFilled, projectedCoverage);
    }

    // ----- Exploration edge shading -----

    private static void applyExplorationEdgeShading(MapItemSavedData target, boolean[] newlyFilled, boolean[] projectedCoverage) {
        byte[] snapshot = target.colors.clone();

        byte[] borderClass = new byte[MAP_PIXEL_COUNT];

        for (int y = 0; y < MAP_SIZE; y++) {
            for (int x = 0; x < MAP_SIZE; x++) {
                int index = x + y * MAP_SIZE;
                if ((snapshot[index] & 0xFF) == 0 || !newlyFilled[index] || !projectedCoverage[index]) continue;

                boolean adjEmpty =
                        (y > 0   && snapshot[x + (y - 1) * MAP_SIZE] == 0) ||
                        (y < MAP_SIZE - 1 && snapshot[x + (y + 1) * MAP_SIZE] == 0) ||
                        (x > 0   && snapshot[(x - 1) + y * MAP_SIZE] == 0) ||
                        (x < MAP_SIZE - 1 && snapshot[(x + 1) + y * MAP_SIZE] == 0);

                if (adjEmpty) {
                    borderClass[index] = 1;
                }
            }
        }

        for (int y = 0; y < MAP_SIZE; y++) {
            for (int x = 0; x < MAP_SIZE; x++) {
                int index = x + y * MAP_SIZE;
                if ((snapshot[index] & 0xFF) == 0 || !newlyFilled[index]) continue;
                if (borderClass[index] != 0) continue;

                boolean adjOuter =
                        (y > 0   && borderClass[x + (y - 1) * MAP_SIZE] == 1) ||
                        (y < MAP_SIZE - 1 && borderClass[x + (y + 1) * MAP_SIZE] == 1) ||
                        (x > 0   && borderClass[(x - 1) + y * MAP_SIZE] == 1) ||
                        (x < MAP_SIZE - 1 && borderClass[(x + 1) + y * MAP_SIZE] == 1);

                if (adjOuter) {
                    borderClass[index] = 2;
                }
            }
        }

        for (int y = 0; y < MAP_SIZE; y++) {
            for (int x = 0; x < MAP_SIZE; x++) {
                byte cls = borderClass[x + y * MAP_SIZE];
                if (cls == 0) continue;

                boolean erase = (x + y) % 2 == 0;
                if (erase) {
                    target.setColor(x, y, (byte) 0);
                }
            }
        }
    }

    private record ProjectionMask(boolean[] newlyFilled, boolean[] projectedCoverage) {}

    private static final class ProjectionAccumulator {
        private final boolean[] newlyFilled = new boolean[MAP_PIXEL_COUNT];
        private final boolean[] projectedCoverage = new boolean[MAP_PIXEL_COUNT];

        private void merge(ProjectionMask mask) {
            for (int i = 0; i < MAP_PIXEL_COUNT; i++) {
                this.newlyFilled[i] |= mask.newlyFilled()[i];
                this.projectedCoverage[i] |= mask.projectedCoverage()[i];
            }
        }
    }

    private record ScaledMapKey(net.minecraft.resources.ResourceKey<Level> dimension, int centerX, int centerZ, byte scale) {
        private static ScaledMapKey from(MapItemSavedData mapData) {
            return new ScaledMapKey(mapData.dimension, mapData.centerX, mapData.centerZ, mapData.scale);
        }
    }
}
