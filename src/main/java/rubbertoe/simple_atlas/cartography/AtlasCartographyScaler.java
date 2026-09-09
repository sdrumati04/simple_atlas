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
        LinkedHashMap<ScaledMapKey, TargetPixelBuffer> bufferByKey = new LinkedHashMap<>();

        boolean hasRemapped = MapModCompat.isRemappedLoaded();

        for (int rawId : contents.mapIds()) {
            MapItemSavedData mapData = level.getMapData(new MapId(rawId));
            if (mapData == null || mapData.locked || mapData.scale != targetScale) {
                continue;
            }

            MapItemSavedData scaled = mapData.scaled();
            ScaledMapKey key = ScaledMapKey.from(scaled);
            MapItemSavedData target = scaledByKey.computeIfAbsent(key, _ -> scaled);
            TargetPixelBuffer buffer = bufferByKey.computeIfAbsent(key, _ -> new TargetPixelBuffer(hasRemapped));
            buffer.collectSamples(mapData, target);
        }

        if (scaledByKey.isEmpty()) {
            return null;
        }

        if (!contents.canAddMapCount(scaledByKey.size())) {
            return null;
        }

        LinkedHashSet<Integer> newMapIds = new LinkedHashSet<>();
        for (Map.Entry<ScaledMapKey, MapItemSavedData> entry : scaledByKey.entrySet()) {
            ScaledMapKey key = entry.getKey();
            MapItemSavedData target = entry.getValue();
            TargetPixelBuffer buffer = bufferByKey.get(key);

            if (buffer != null) {
                ProjectionMask projection = buffer.resolveTargetPixels(target);
                applyExplorationEdgeShading(target, projection.newlyFilled, projection.projectedCoverage, buffer.getTargetRem());
                if (buffer.getTargetRem() != null) {
                    MapModCompat.setRemappedColors(target, buffer.getTargetRem());
                }
            }

            MapId newId = level.getFreeMapId();
            level.setMapData(newId, target);
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

    // ----- High-fidelity modal downsampling buffer -----

    private static final class TargetPixelBuffer {
        private static final int MAX_SAMPLES_PER_PIXEL = 8;
        private final byte[] sampleCounts = new byte[MAP_PIXEL_COUNT];
        private final byte[] vanillaSamples = new byte[MAP_PIXEL_COUNT * MAX_SAMPLES_PER_PIXEL];
        private final int[] remappedSamples;
        private final ArrayList<Integer> targetRem;

        TargetPixelBuffer(boolean hasRemapped) {
            this.remappedSamples = hasRemapped ? new int[MAP_PIXEL_COUNT * MAX_SAMPLES_PER_PIXEL] : null;
            this.targetRem = hasRemapped ? new ArrayList<>(Collections.nCopies(MAP_PIXEL_COUNT, 0)) : null;
        }

        public @Nullable ArrayList<Integer> getTargetRem() {
            return targetRem;
        }

        public void collectSamples(MapItemSavedData source, MapItemSavedData target) {
            int sourceScaleFactor = 1 << source.scale;
            int targetScaleFactor = 1 << target.scale;

            double sourceMinX = source.centerX - 64.0 * sourceScaleFactor;
            double sourceMinZ = source.centerZ - 64.0 * sourceScaleFactor;
            double targetMinX = target.centerX - 64.0 * targetScaleFactor;
            double targetMinZ = target.centerZ - 64.0 * targetScaleFactor;

            ArrayList<Integer> sourceRem = remappedSamples != null ? MapModCompat.getRemappedColors(source) : null;

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
                    int count = sampleCounts[targetIndex] & 0xFF;
                    if (count < MAX_SAMPLES_PER_PIXEL) {
                        int base = targetIndex * MAX_SAMPLES_PER_PIXEL + count;
                        vanillaSamples[base] = color;
                        if (remappedSamples != null) {
                            int remColor = (sourceRem != null && srcIdx < sourceRem.size()) ? sourceRem.get(srcIdx) : 0;
                            remappedSamples[base] = remColor;
                        }
                        sampleCounts[targetIndex] = (byte) (count + 1);
                    }
                }
            }
        }

        public ProjectionMask resolveTargetPixels(MapItemSavedData target) {
            boolean[] newlyFilled = new boolean[MAP_PIXEL_COUNT];
            boolean[] projectedCoverage = new boolean[MAP_PIXEL_COUNT];

            for (int targetIndex = 0; targetIndex < MAP_PIXEL_COUNT; targetIndex++) {
                int count = sampleCounts[targetIndex] & 0xFF;
                if (count == 0) {
                    continue;
                }

                projectedCoverage[targetIndex] = true;
                if (target.colors[targetIndex] != 0) {
                    continue;
                }

                int base = targetIndex * MAX_SAMPLES_PER_PIXEL;
                byte bestColor;
                int bestRemColor = 0;

                if (count == 1) {
                    bestColor = vanillaSamples[base];
                    if (remappedSamples != null) {
                        bestRemColor = remappedSamples[base];
                    }
                } else {
                    // 1. Dominant material: (col & 0xFF) >> 2 (vanilla MapColor index)
                    int bestMat = -1;
                    int bestMatVotes = 0;
                    for (int i = 0; i < count; i++) {
                        int mat = (vanillaSamples[base + i] & 0xFF) >> 2;
                        int votes = 0;
                        for (int j = 0; j < count; j++) {
                            if (((vanillaSamples[base + j] & 0xFF) >> 2) == mat) {
                                votes++;
                            }
                        }
                        if (votes > bestMatVotes) {
                            bestMatVotes = votes;
                            bestMat = mat;
                        }
                    }

                    // 2. Dominant brightness / exact color among the dominant material
                    bestColor = 0;
                    int bestColorVotes = 0;
                    for (int i = 0; i < count; i++) {
                        byte col = vanillaSamples[base + i];
                        if (((col & 0xFF) >> 2) != bestMat) {
                            continue;
                        }
                        int votes = 0;
                        for (int j = 0; j < count; j++) {
                            if (vanillaSamples[base + j] == col) {
                                votes++;
                            }
                        }
                        if (votes > bestColorVotes) {
                            bestColorVotes = votes;
                            bestColor = col;
                        }
                    }

                    // 3. Dominant Remapped RGB color among the dominant material
                    if (remappedSamples != null) {
                        int bestRemVotes = 0;
                        for (int i = 0; i < count; i++) {
                            if (((vanillaSamples[base + i] & 0xFF) >> 2) != bestMat) {
                                continue;
                            }
                            int rem = remappedSamples[base + i];
                            if (rem == 0) {
                                continue;
                            }
                            int votes = 0;
                            for (int j = 0; j < count; j++) {
                                if (remappedSamples[base + j] == rem) {
                                    votes++;
                                }
                            }
                            if (votes > bestRemVotes) {
                                bestRemVotes = votes;
                                bestRemColor = rem;
                            }
                        }
                        // If no non-zero remColor was found with bestMat, fallback to first non-zero sample
                        if (bestRemColor == 0) {
                            for (int i = 0; i < count; i++) {
                                if (remappedSamples[base + i] != 0) {
                                    bestRemColor = remappedSamples[base + i];
                                    break;
                                }
                            }
                        }
                    }
                }

                int x = targetIndex % MAP_SIZE;
                int y = targetIndex / MAP_SIZE;
                target.setColor(x, y, bestColor);
                newlyFilled[targetIndex] = true;

                if (targetRem != null && bestRemColor != 0) {
                    targetRem.set(targetIndex, bestRemColor);
                }
            }

            return new ProjectionMask(newlyFilled, projectedCoverage);
        }
    }

    // ----- Exploration edge shading -----

    private static void applyExplorationEdgeShading(
            MapItemSavedData target,
            boolean[] newlyFilled,
            boolean[] projectedCoverage,
            @Nullable ArrayList<Integer> targetRem
    ) {
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
                int index = x + y * MAP_SIZE;
                byte cls = borderClass[index];
                if (cls == 0) continue;

                boolean erase = (x + y) % 2 == 0;
                if (erase) {
                    target.setColor(x, y, (byte) 0);
                    if (targetRem != null && index < targetRem.size()) {
                        targetRem.set(index, 0);
                    }
                }
            }
        }
    }

    private record ProjectionMask(boolean[] newlyFilled, boolean[] projectedCoverage) {}

    private record ScaledMapKey(net.minecraft.resources.ResourceKey<Level> dimension, int centerX, int centerZ, byte scale) {
        private static ScaledMapKey from(MapItemSavedData mapData) {
            return new ScaledMapKey(mapData.dimension, mapData.centerX, mapData.centerZ, mapData.scale);
        }
    }
}
