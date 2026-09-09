package rubbertoe.simple_atlas.cartography;

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.Nullable;
import rubbertoe.simple_atlas.compat.MapModCompat;
import rubbertoe.simple_atlas.component.AtlasContents;
import rubbertoe.simple_atlas.config.SimpleAtlasConfigManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

        LinkedHashSet<Integer> newMapIds = new LinkedHashSet<>();
        for (Map.Entry<ScaledMapKey, MapItemSavedData> entry : scaledByKey.entrySet()) {
            ScaledMapKey key = entry.getKey();
            MapItemSavedData target = entry.getValue();
            TargetPixelBuffer buffer = bufferByKey.get(key);

            if (buffer != null) {
                ProjectionMask projection = buffer.resolveTargetPixels(target);
                ServerLevel mapLevel = level.getServer().getLevel(target.dimension);
                if (mapLevel == null) {
                    mapLevel = level;
                }
                scanWorldBlocksForScaledMap(mapLevel, target, projection.projectedCoverage, buffer.getTargetRem());
                applyExplorationEdgeShading(target, projection.newlyFilled, projection.projectedCoverage, buffer.getTargetRem());
                if (buffer.getTargetRem() != null) {
                    MapModCompat.setRemappedColors(target, buffer.getTargetRem());
                }
            }

            MapId newId = level.getFreeMapId();
            level.setMapData(newId, target);
            target.setDirty();
            newMapIds.add(newId.id());
        }

        LinkedHashSet<Integer> resultingIds = new LinkedHashSet<>();
        for (int rawId : contents.mapIds()) {
            MapItemSavedData d = level.getMapData(new MapId(rawId));
            if (d != null && d.scale != targetScale) {
                resultingIds.add(rawId);
            }
        }
        resultingIds.addAll(newMapIds);

        return new AtlasContents(
                List.copyOf(resultingIds),
                contents.waypoints(),
                contents.selectedWaypointIconIndex(),
                contents.nextWaypointNumber(),
                0,
                targetScale + 1
        );
    }

    private static boolean validateScaleInputs(ServerLevel level, AtlasContents contents) {
        if (contents.mapIds().isEmpty()) {
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

    // ----- Atlas-wide downscale (Shears + Atlas) -----

    public static boolean canDownscaleAtlas(ServerLevel level, AtlasContents contents) {
        if (contents.mapIds().isEmpty()) {
            return false;
        }

        int targetScale = -1;
        for (int rawId : contents.mapIds()) {
            MapItemSavedData mapData = level.getMapData(new MapId(rawId));
            if (mapData != null && !mapData.locked && mapData.scale > 0) {
                if (targetScale < 0 || mapData.scale < targetScale) {
                    targetScale = mapData.scale;
                }
            }
        }

        if (targetScale <= 0) {
            return false;
        }

        int maxMaps = SimpleAtlasConfigManager.getMaxAtlasMapCount();
        int estimatedNewMaps = 0;
        for (int rawId : contents.mapIds()) {
            MapItemSavedData mapData = level.getMapData(new MapId(rawId));
            if (mapData != null && mapData.scale == targetScale) {
                for (int qz = 0; qz < 2; qz++) {
                    for (int qx = 0; qx < 2; qx++) {
                        if (hasExploredPixelsInQuadrant(mapData, qx, qz)) {
                            estimatedNewMaps++;
                        }
                    }
                }
            } else {
                estimatedNewMaps++;
            }
        }

        return estimatedNewMaps > 0 && estimatedNewMaps <= maxMaps;
    }

    private static boolean hasExploredPixelsInQuadrant(MapItemSavedData data, int qx, int qz) {
        for (int y = qz * 64; y < (qz + 1) * 64; y++) {
            for (int x = qx * 64; x < (qx + 1) * 64; x++) {
                if (data.colors[x + y * MAP_SIZE] != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    public static @Nullable AtlasContents downscaleAtlas(ServerLevel level, AtlasContents contents) {
        if (!canDownscaleAtlas(level, contents)) {
            return null;
        }

        int targetScale = -1;
        for (int rawId : contents.mapIds()) {
            MapItemSavedData mapData = level.getMapData(new MapId(rawId));
            if (mapData != null && !mapData.locked && mapData.scale > 0) {
                if (targetScale < 0 || mapData.scale < targetScale) {
                    targetScale = mapData.scale;
                }
            }
        }

        if (targetScale <= 0) {
            return null;
        }

        int childScale = targetScale - 1;
        int childScaleFactor = 1 << childScale;
        int childSpan = 128 * childScaleFactor;
        int parentScaleFactor = 1 << targetScale;
        int parentSpan = 128 * parentScaleFactor;

        boolean hasRemapped = MapModCompat.isRemappedLoaded();
        LinkedHashMap<ScaledMapKey, MapItemSavedData> childMapsByKey = new LinkedHashMap<>();

        for (int rawId : contents.mapIds()) {
            MapItemSavedData mapData = level.getMapData(new MapId(rawId));
            if (mapData == null || mapData.locked || mapData.scale != targetScale) {
                continue;
            }

            int parentMinX = mapData.centerX - parentSpan / 2;
            int parentMinZ = mapData.centerZ - parentSpan / 2;

            ArrayList<Integer> parentRem = hasRemapped ? MapModCompat.getRemappedColors(mapData) : null;

            for (int iz = 0; iz < 2; iz++) {
                for (int ix = 0; ix < 2; ix++) {
                    if (!hasExploredPixelsInQuadrant(mapData, ix, iz)) {
                        continue;
                    }

                    int childMinX = parentMinX + ix * childSpan;
                    int childMinZ = parentMinZ + iz * childSpan;
                    int childCenterX = childMinX + childSpan / 2;
                    int childCenterZ = childMinZ + childSpan / 2;

                    ScaledMapKey key = new ScaledMapKey(mapData.dimension, childCenterX, childCenterZ, (byte) childScale);
                    MapItemSavedData childData = childMapsByKey.get(key);
                    if (childData == null) {
                        childData = MapItemSavedData.createFresh(
                                childCenterX,
                                childCenterZ,
                                (byte) childScale,
                                true,
                                false,
                                mapData.dimension
                        );
                        childMapsByKey.put(key, childData);
                    }

                    boolean[] coverageOnChild = new boolean[MAP_PIXEL_COUNT];
                    boolean hasChildCoverage = false;
                    ArrayList<Integer> childRem = hasRemapped ? MapModCompat.getRemappedColors(childData) : null;
                    if (hasRemapped && childRem == null) {
                        childRem = new ArrayList<>(Collections.nCopies(MAP_PIXEL_COUNT, 0));
                    }

                    for (int cy = 0; cy < MAP_SIZE; cy++) {
                        for (int cx = 0; cx < MAP_SIZE; cx++) {
                            double worldX = childMinX + (cx + 0.5) * childScaleFactor;
                            double worldZ = childMinZ + (cy + 0.5) * childScaleFactor;

                            int parentX = (int) Math.floor((worldX - parentMinX) / parentScaleFactor);
                            int parentY = (int) Math.floor((worldZ - parentMinZ) / parentScaleFactor);

                            if (parentX >= 0 && parentX < MAP_SIZE && parentY >= 0 && parentY < MAP_SIZE) {
                                int parentIdx = parentX + parentY * MAP_SIZE;
                                byte parentColor = mapData.colors[parentIdx];
                                if (parentColor != 0) {
                                    int childIdx = cx + cy * MAP_SIZE;
                                    coverageOnChild[childIdx] = true;
                                    hasChildCoverage = true;
                                    childData.setColor(cx, cy, parentColor);
                                    if (hasRemapped && parentRem != null && parentIdx < parentRem.size() && childRem != null) {
                                        int remVal = parentRem.get(parentIdx);
                                        if (remVal != 0) {
                                            childRem.set(childIdx, remVal);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (hasChildCoverage) {
                        ServerLevel mapLevel = level.getServer().getLevel(mapData.dimension);
                        if (mapLevel == null) {
                            mapLevel = level;
                        }
                        scanWorldBlocksForScaledMap(mapLevel, childData, coverageOnChild, childRem);
                    }

                    if (childRem != null) {
                        MapModCompat.setRemappedColors(childData, childRem);
                    }
                }
            }
        }

        if (childMapsByKey.isEmpty()) {
            return null;
        }

        int maxMaps = SimpleAtlasConfigManager.getMaxAtlasMapCount();
        LinkedHashSet<Integer> newMapIds = new LinkedHashSet<>();

        for (MapItemSavedData childData : childMapsByKey.values()) {
            MapId newId = level.getFreeMapId();
            level.setMapData(newId, childData);
            childData.setDirty();
            newMapIds.add(newId.id());
        }

        LinkedHashSet<Integer> resultingIds = new LinkedHashSet<>();
        for (int rawId : contents.mapIds()) {
            MapItemSavedData d = level.getMapData(new MapId(rawId));
            if (d != null && d.scale != targetScale) {
                resultingIds.add(rawId);
            }
        }
        resultingIds.addAll(newMapIds);

        if (resultingIds.size() > maxMaps) {
            return null;
        }

        return new AtlasContents(
                List.copyOf(resultingIds),
                contents.waypoints(),
                contents.selectedWaypointIconIndex(),
                contents.nextWaypointNumber(),
                0,
                childScale
        );
    }

    private static void sendMapSyncPacket(ServerPlayer player, MapId mapId, MapItemSavedData mapData) {
        mapData.getHoldingPlayer(player);
        List<MapDecoration> currentDecorations = new ArrayList<>();
        mapData.getDecorations().forEach(currentDecorations::add);
        Packet<?> packet = new ClientboundMapItemDataPacket(
                mapId,
                mapData.scale,
                mapData.locked,
                Optional.of(currentDecorations),
                Optional.of(new MapItemSavedData.MapPatch(0, 0, MAP_SIZE, MAP_SIZE, mapData.colors))
        );
        player.connection.send(packet);
        MapModCompat.sendRemappedPackets(player, mapId, mapData);
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

    // ----- Real world block scanning (1:1 identical to walking on foot) -----

    private static void scanWorldBlocksForScaledMap(
            ServerLevel level,
            MapItemSavedData target,
            boolean[] projectedCoverage,
            @Nullable ArrayList<Integer> targetRem
    ) {
        int scaleFactor = 1 << target.scale;
        int centerX = target.centerX;
        int centerZ = target.centerZ;
        boolean hasCeiling = level.dimensionType().hasCeiling();
        boolean useRemapped = MapModCompat.isRemappedLoaded();

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos fluidPos = new BlockPos.MutableBlockPos();

        int lastChunkX = Integer.MIN_VALUE;
        int lastChunkZ = Integer.MIN_VALUE;
        LevelChunk cachedChunk = null;

        for (int x = 0; x < MAP_SIZE; ++x) {
            double d0 = 0.0D;

            for (int y = -1; y < MAP_SIZE; ++y) {
                boolean isExplored = y >= 0 && projectedCoverage[x + y * MAP_SIZE];
                boolean nextIsExplored = y < MAP_SIZE - 1 && projectedCoverage[x + (y + 1) * MAP_SIZE];

                if (!isExplored && !nextIsExplored) {
                    d0 = 0.0D;
                    continue;
                }

                int startBlockX = (centerX / scaleFactor + x - 64) * scaleFactor;
                int startBlockZ = (centerZ / scaleFactor + y - 64) * scaleFactor;

                int chunkX = SectionPos.blockToSectionCoord(startBlockX);
                int chunkZ = SectionPos.blockToSectionCoord(startBlockZ);
                if (cachedChunk == null || lastChunkX != chunkX || lastChunkZ != chunkZ) {
                    lastChunkX = chunkX;
                    lastChunkZ = chunkZ;
                    cachedChunk = level.getChunk(chunkX, chunkZ);
                }

                if (cachedChunk == null || cachedChunk.isEmpty()) {
                    d0 = 0.0D;
                    continue;
                }

                int fluidDepthSum = 0;
                double sumY = 0.0D;
                Multiset<MapColor> vanillaCounts = LinkedHashMultiset.create();
                Multiset<Object> remappedCounts = useRemapped ? LinkedHashMultiset.create() : null;

                if (hasCeiling) {
                    int k = startBlockX + startBlockZ * 231871;
                    k = k * k * 31287121 + k * 11;
                    if ((k >> 20 & 1) == 0) {
                        vanillaCounts.add(Blocks.DIRT.defaultBlockState().getMapColor(level, BlockPos.ZERO), 10);
                        if (useRemapped) {
                            Object dirtDuck = MapModCompat.getRemappedDirt(level);
                            if (dirtDuck != null) {
                                remappedCounts.add(dirtDuck, 10);
                            }
                        }
                    } else {
                        vanillaCounts.add(Blocks.STONE.defaultBlockState().getMapColor(level, BlockPos.ZERO), 100);
                        if (useRemapped) {
                            Object stoneDuck = MapModCompat.getRemappedStone(level);
                            if (stoneDuck != null) {
                                remappedCounts.add(stoneDuck, 100);
                            }
                        }
                    }
                    sumY = 100.0D;
                } else {
                    for (int dx = 0; dx < scaleFactor; ++dx) {
                        for (int dz = 0; dz < scaleFactor; ++dz) {
                            int worldX = startBlockX + dx;
                            int worldZ = startBlockZ + dz;
                            int bChunkX = SectionPos.blockToSectionCoord(worldX);
                            int bChunkZ = SectionPos.blockToSectionCoord(worldZ);
                            if (cachedChunk == null || lastChunkX != bChunkX || lastChunkZ != bChunkZ) {
                                lastChunkX = bChunkX;
                                lastChunkZ = bChunkZ;
                                cachedChunk = level.getChunk(bChunkX, bChunkZ);
                            }
                            if (cachedChunk == null || cachedChunk.isEmpty()) {
                                continue;
                            }

                            pos.set(worldX, 0, worldZ);
                            int surfaceY = cachedChunk.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) + 1;
                            BlockState state;
                            if (surfaceY <= level.getMinY()) {
                                state = Blocks.BEDROCK.defaultBlockState();
                            } else {
                                do {
                                    --surfaceY;
                                    pos.setY(surfaceY);
                                    state = cachedChunk.getBlockState(pos);
                                } while (isStateInvisible(level, pos, state, useRemapped) && surfaceY > level.getMinY());

                                if (surfaceY > level.getMinY() && !state.getFluidState().isEmpty()) {
                                    int fluidDepth = surfaceY - 1;
                                    fluidPos.set(pos);
                                    BlockState fluidState;
                                    do {
                                        fluidPos.setY(fluidDepth--);
                                        fluidState = cachedChunk.getBlockState(fluidPos);
                                        fluidDepthSum++;
                                    } while (fluidDepth > level.getMinY() && !fluidState.getFluidState().isEmpty());

                                    state = getCorrectStateForFluidBlock(level, state, pos);
                                }
                            }

                            target.checkBanners(level, pos.getX(), pos.getZ());
                            sumY += (double) surfaceY / (double) (scaleFactor * scaleFactor);

                            vanillaCounts.add(state.getMapColor(level, pos));
                            if (useRemapped) {
                                Object matchingDuck = MapModCompat.getMatchingColor(level, pos, state);
                                if (matchingDuck != null) {
                                    remappedCounts.add(matchingDuck);
                                }
                            }
                        }
                    }
                }

                fluidDepthSum /= (scaleFactor * scaleFactor);

                MapColor dominantVanilla = Iterables.getFirst(Multisets.copyHighestCountFirst(vanillaCounts), MapColor.NONE);

                Object dominantDuck = null;
                boolean useDithering = false;
                if (useRemapped && remappedCounts != null) {
                    dominantDuck = Iterables.getFirst(Multisets.copyHighestCountFirst(remappedCounts), null);
                    if (dominantDuck != null) {
                        useDithering = MapModCompat.useDithering(dominantDuck);
                    }
                } else if (dominantVanilla == MapColor.WATER) {
                    useDithering = true;
                }

                MapColor.Brightness brightness;
                if (useDithering) {
                    double depthDither = (double) fluidDepthSum * 0.1D + (double) (x + y & 1) * 0.2D;
                    if (depthDither < 0.5D) {
                        brightness = MapColor.Brightness.HIGH;
                    } else if (depthDither > 0.9D) {
                        brightness = MapColor.Brightness.LOW;
                    } else {
                        brightness = MapColor.Brightness.NORMAL;
                    }
                } else {
                    double delta = (sumY - d0) * 4.0D / (double) (scaleFactor + 4) + ((double) (x + y & 1) - 0.5D) * 0.4D;
                    if (delta > 0.6D) {
                        brightness = MapColor.Brightness.HIGH;
                    } else if (delta < -0.6D) {
                        brightness = MapColor.Brightness.LOW;
                    } else {
                        brightness = MapColor.Brightness.NORMAL;
                    }
                }

                d0 = sumY;

                if (isExplored) {
                    int targetIndex = x + y * MAP_SIZE;
                    if (dominantVanilla != null && dominantVanilla != MapColor.NONE) {
                        target.setColor(x, y, dominantVanilla.getPackedId(brightness));
                    }
                    if (useRemapped && dominantDuck != null) {
                        MapModCompat.putColor(target, x, y, dominantDuck, brightness.id);
                        if (targetRem != null) {
                            targetRem.set(targetIndex, MapModCompat.getRemappedColor(target, targetIndex));
                        }
                    }
                }
            }
        }
    }

    private static BlockState getCorrectStateForFluidBlock(Level level, BlockState state, BlockPos pos) {
        FluidState fluidState = state.getFluidState();
        if (!fluidState.isEmpty() && !state.isFaceSturdy(level, pos, Direction.UP)) {
            return fluidState.createLegacyBlock();
        }
        return state;
    }

    private static boolean isStateInvisible(Level level, BlockPos pos, BlockState state, boolean useRemapped) {
        if (useRemapped) {
            Object duck = MapModCompat.getMatchingColor(level, pos, state);
            return duck == null || MapModCompat.getDuckColor(duck) == 0;
        }
        return state.getMapColor(level, pos) == MapColor.NONE;
    }

    private record ProjectionMask(boolean[] newlyFilled, boolean[] projectedCoverage) {}

    private record ScaledMapKey(net.minecraft.resources.ResourceKey<Level> dimension, int centerX, int centerZ, byte scale) {
        private static ScaledMapKey from(MapItemSavedData mapData) {
            return new ScaledMapKey(mapData.dimension, mapData.centerX, mapData.centerZ, mapData.scale);
        }
    }
}
