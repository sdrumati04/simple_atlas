package rubbertoe.simple_atlas.cartography;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import rubbertoe.simple_atlas.component.AtlasContents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.player.Player;
import rubbertoe.simple_atlas.compat.MapModCompat;

public final class AtlasCartographyScaler {
    private static final int MAP_SIZE = 128;
    private static final int MAP_PIXEL_COUNT = MAP_SIZE * MAP_SIZE;

    private AtlasCartographyScaler() {}

    // ----- Atlas-wide upscale -----

    public static boolean canScaleAtlas(ServerLevel level, AtlasContents contents) {
        return validateScaleInputs(level, contents);
    }

    public static @Nullable AtlasContents scaleAtlas(ServerLevel level, AtlasContents contents) {
        if (!validateScaleInputs(level, contents)) {
            return null;
        }

        LinkedHashMap<ScaledMapKey, MapItemSavedData> scaledByKey = new LinkedHashMap<>();
        LinkedHashMap<ScaledMapKey, ProjectionAccumulator> projectionByKey = new LinkedHashMap<>();
        LinkedHashSet<Integer> scaledMapIds = new LinkedHashSet<>();

        for (int rawId : contents.mapIds()) {
            MapItemSavedData mapData = level.getMapData(new MapId(rawId));
            if (mapData == null) {
                return null;
            }

            MapItemSavedData scaled = mapData.scaled();
            ScaledMapKey key = ScaledMapKey.from(scaled);
            MapItemSavedData target = scaledByKey.computeIfAbsent(key, _ -> scaled);
            ProjectionMask projection = projectKnownPixelsIntoScaledMap(mapData, target);
            projectionByKey.computeIfAbsent(key, _ -> new ProjectionAccumulator()).merge(projection);
        }

        for (Map.Entry<ScaledMapKey, MapItemSavedData> entry : scaledByKey.entrySet()) {
            ProjectionAccumulator projection = projectionByKey.get(entry.getKey());
            if (projection != null) {
                applyExplorationEdgeShading(entry.getValue(), projection.newlyFilled, projection.projectedCoverage);
            }
            MapId newId = level.getFreeMapId();
            level.setMapData(newId, entry.getValue());
            scaledMapIds.add(newId.id());
        }

        List<Integer> subMapIds = new java.util.ArrayList<>(contents.allMapIds());

        AtlasContents scaledContents = new AtlasContents(
                List.copyOf(scaledMapIds),
                contents.waypoints(),
                contents.selectedWaypointIconIndex(),
                contents.nextWaypointNumber(),
                0,
                subMapIds
        );
        return ensureSubMaps(level, scaledContents);
    }

    public static void initializeSubMapFromParent(
            MapItemSavedData parent,
            MapItemSavedData subMap,
            int quadrant
    ) {
        if (parent == null || subMap == null || parent.scale <= 0 || subMap.scale != parent.scale - 1) {
            return;
        }

        int parentStartX = (quadrant % 2 == 0) ? 0 : 64;
        int parentStartZ = (quadrant < 2) ? 0 : 64;
        boolean hasRemapped = MapModCompat.isRemappedLoaded();
        ArrayList<Integer> parentRem = hasRemapped ? MapModCompat.getRemappedColors(parent) : null;
        ArrayList<Integer> subRem = hasRemapped ? MapModCompat.getRemappedColors(subMap) : null;
        if (hasRemapped && (subRem == null || subRem.size() != MAP_PIXEL_COUNT)) {
            subRem = new ArrayList<>(Collections.nCopies(MAP_PIXEL_COUNT, 0));
        }

        for (int subX = 0; subX < MAP_SIZE; subX++) {
            int parentPx = parentStartX + (subX / 2);
            for (int subZ = 0; subZ < MAP_SIZE; subZ++) {
                int parentPz = parentStartZ + (subZ / 2);
                int pIdx = parentPx + parentPz * MAP_SIZE;
                int sIdx = subX + subZ * MAP_SIZE;
                byte pColor = parent.colors[pIdx];

                if (pColor == 0) {
                    subMap.setColor(subX, subZ, (byte) 0);
                    if (subRem != null) {
                        subRem.set(sIdx, 0);
                    }
                    continue;
                }

                subMap.setColor(subX, subZ, pColor);
                if (subRem != null && parentRem != null && pIdx < parentRem.size()) {
                    subRem.set(sIdx, parentRem.get(pIdx));
                }
            }
        }

        if (subRem != null) {
            MapModCompat.setRemappedColors(subMap, subRem);
        }
        subMap.setDirty();
    }

    public static void syncSubMapToParent(MapItemSavedData parent, MapItemSavedData subMap) {
        if (parent == null || subMap == null || parent.scale <= 0 || subMap.scale != parent.scale - 1) {
            return;
        }
        int quadrant = (subMap.centerX > parent.centerX ? 1 : 0) + (subMap.centerZ > parent.centerZ ? 2 : 0);
        int parentStartX = (quadrant % 2 == 0) ? 0 : 64;
        int parentStartZ = (quadrant < 2) ? 0 : 64;

        boolean hasRemapped = MapModCompat.isRemappedLoaded();
        boolean parentChanged = false;
        ArrayList<Integer> parentRem = hasRemapped ? MapModCompat.getRemappedColors(parent) : null;
        ArrayList<Integer> subRem = hasRemapped ? MapModCompat.getRemappedColors(subMap) : null;

        for (int py = 0; py < 64; py++) {
            for (int px = 0; px < 64; px++) {
                int pX = parentStartX + px;
                int pZ = parentStartZ + py;
                int pIdx = pX + pZ * MAP_SIZE;

                // ONLY fill if parent pixel is currently unexplored (0)
                if (parent.colors[pIdx] == 0) {
                    int subBaseX = px * 2;
                    int subBaseZ = py * 2;

                    byte sampleSubColor = 0;
                    int sampleSubRemapped = 0;

                    for (int dy = 0; dy < 2; dy++) {
                        for (int dx = 0; dx < 2; dx++) {
                            int sX = subBaseX + dx;
                            int sZ = subBaseZ + dy;
                            int sIdx = sX + sZ * MAP_SIZE;

                            byte sColor = subMap.colors[sIdx];
                            if (sColor != 0) {
                                sampleSubColor = sColor;
                                if (subRem != null && sIdx < subRem.size()) {
                                    int sRem = subRem.get(sIdx);
                                    if (sRem != 0) {
                                        sampleSubRemapped = sRem;
                                    }
                                }
                            }
                        }
                    }

                    if (sampleSubColor != 0) {
                        parent.setColor(pX, pZ, sampleSubColor);
                        if (parentRem != null && pIdx < parentRem.size() && sampleSubRemapped != 0) {
                            parentRem.set(pIdx, sampleSubRemapped);
                        }
                        parentChanged = true;
                    }
                }
            }
        }

        if (parentChanged) {
            if (parentRem != null) {
                MapModCompat.setRemappedColors(parent, parentRem);
            }
            parent.setDirty();
        }
    }

    public static boolean isPlayerInsideMap(Player player, MapItemSavedData mapData) {
        if (player == null || mapData == null) {
            return false;
        }
        int halfSpan = 64 << mapData.scale;
        return Math.abs(player.getX() - mapData.centerX) <= halfSpan
                && Math.abs(player.getZ() - mapData.centerZ) <= halfSpan;
    }

    public static boolean needsNativeExploration(MapItemSavedData parent, MapItemSavedData subMap, int quadrant) {
        if (parent == null || subMap == null || parent.scale <= 0 || subMap.scale != parent.scale - 1) {
            return false;
        }

        int totalExploredPairs = 0;
        int identicalPairs = 0;

        for (int y = 0; y < MAP_SIZE; y += 4) {
            for (int x = 0; x < MAP_SIZE; x += 4) {
                byte sCol1 = subMap.colors[x + y * MAP_SIZE];
                byte sCol2 = subMap.colors[(x + 1) + y * MAP_SIZE];

                if (sCol1 != 0 && sCol2 != 0) {
                    totalExploredPairs++;
                    if (sCol1 == sCol2) {
                        identicalPairs++;
                    }
                }
            }
        }

        return totalExploredPairs > 20 && identicalPairs == totalExploredPairs;
    }

    public static boolean needs1to1Exploration(MapItemSavedData parent, MapItemSavedData subMap, int quadrant) {
        return needsNativeExploration(parent, subMap, quadrant);
    }

    public static void fullyExploreSubMap(ServerLevel level, ServerPlayer player, MapItemSavedData parent, MapItemSavedData subMap, int quadrant) {
        if (level == null || player == null || parent == null || subMap == null) {
            return;
        }

        if (!level.dimension().equals(subMap.dimension)) {
            return;
        }

        int parentStartX = (quadrant % 2 == 0) ? 0 : 64;
        int parentStartZ = (quadrant < 2) ? 0 : 64;
        boolean hasParentExplored = false;
        for (int py = 0; py < 64; py++) {
            for (int px = 0; px < 64; px++) {
                if (parent.colors[(parentStartX + px) + (parentStartZ + py) * MAP_SIZE] != 0) {
                    hasParentExplored = true;
                    break;
                }
            }
            if (hasParentExplored) break;
        }

        if (!hasParentExplored) {
            return;
        }

        Vec3 origPos = player.position();
        try {
            if (subMap.scale == 0) {
                player.setPosRaw(subMap.centerX, origPos.y, subMap.centerZ);
                for (int step = 0; step < 8; step++) {
                    ((MapItem) Items.FILLED_MAP).update(level, player, subMap);
                }
            } else {
                int scanStep = 32 << subMap.scale;
                int[] sDx = {-scanStep, scanStep, -scanStep, scanStep};
                int[] sDz = {-scanStep, -scanStep, scanStep, scanStep};
                for (int i = 0; i < 4; i++) {
                    player.setPosRaw(subMap.centerX + sDx[i], origPos.y, subMap.centerZ + sDz[i]);
                    for (int step = 0; step < 2; step++) {
                        ((MapItem) Items.FILLED_MAP).update(level, player, subMap);
                    }
                }
            }
        } finally {
            player.setPosRaw(origPos.x, origPos.y, origPos.z);
        }

        boolean hasRemapped = MapModCompat.isRemappedLoaded();
        ArrayList<Integer> subRem = hasRemapped ? MapModCompat.getRemappedColors(subMap) : null;
        for (int subX = 0; subX < MAP_SIZE; subX++) {
            int parentPx = parentStartX + (subX / 2);
            for (int subZ = 0; subZ < MAP_SIZE; subZ++) {
                int parentPz = parentStartZ + (subZ / 2);
                if (parent.colors[parentPx + parentPz * MAP_SIZE] == 0) {
                    subMap.setColor(subX, subZ, (byte) 0);
                    if (subRem != null) {
                        subRem.set(subX + subZ * MAP_SIZE, 0);
                    }
                }
            }
        }
        if (subRem != null) {
            MapModCompat.setRemappedColors(subMap, subRem);
        }
        subMap.setDirty();
    }

    public static AtlasContents ensureSubMaps(ServerLevel level, AtlasContents contents) {
        return ensureSubMaps(level, contents, null);
    }

    public static AtlasContents ensureSubMaps(ServerLevel level, AtlasContents contents, @Nullable ServerPlayer player) {
        if (contents.mapIds().isEmpty()) {
            return contents;
        }

        MapItemSavedData first = level.getMapData(new MapId(contents.mapIds().getFirst()));
        if (first == null || first.scale <= 0) {
            return contents;
        }

        int topScale = first.scale;
        AtlasContents current = contents;

        for (int tierScale = topScale; tierScale > 0; tierScale--) {
            if (current.subMapIds().size() >= AtlasContents.HARD_MAX_SUBMAP_COUNT) {
                break;
            }

            List<Integer> parentIds = getMapIdsForScale(level, current.allMapIds(), tierScale);
            if (parentIds.isEmpty()) {
                break;
            }

            if (current.subMapIds().size() + parentIds.size() * 4 > AtlasContents.HARD_MAX_SUBMAP_COUNT) {
                break;
            }

            current = ensureSubMapsForTier(level, current, parentIds, tierScale, player);
        }

        return current;
    }

    private static List<Integer> getMapIdsForScale(ServerLevel level, List<Integer> mapIds, int targetScale) {
        List<Integer> matching = new ArrayList<>();
        for (int rawId : mapIds) {
            MapItemSavedData data = level.getMapData(new MapId(rawId));
            if (data != null && data.scale == targetScale) {
                matching.add(rawId);
            }
        }
        return matching;
    }

    private record SubMapEntry(int id, MapItemSavedData data) {}

    private static AtlasContents ensureSubMapsForTier(
            ServerLevel level,
            AtlasContents contents,
            List<Integer> parentIds,
            int parentScale,
            @Nullable ServerPlayer player
    ) {
        int childScale = parentScale - 1;
        int step = 32 << parentScale;
        int[] dxOffsets = {-step, step, -step, step};
        int[] dzOffsets = {-step, -step, step, step};

        LinkedHashSet<Integer> resultSubMapIds = new LinkedHashSet<>(contents.subMapIds());
        boolean changed = false;

        Map<ScaledMapKey, SubMapEntry> subMapIndex = new HashMap<>();
        for (int subRawId : resultSubMapIds) {
            MapItemSavedData subData = level.getMapData(new MapId(subRawId));
            if (subData != null) {
                subMapIndex.put(ScaledMapKey.from(subData), new SubMapEntry(subRawId, subData));
            }
        }

        for (int parentRawId : parentIds) {
            MapItemSavedData parent = level.getMapData(new MapId(parentRawId));
            if (parent == null || parent.scale != parentScale) {
                continue;
            }

            for (int q = 0; q < 4; q++) {
                int targetX = parent.centerX + dxOffsets[q];
                int targetZ = parent.centerZ + dzOffsets[q];
                ScaledMapKey key = new ScaledMapKey(parent.dimension, targetX, targetZ, (byte) childScale);

                SubMapEntry existingEntry = subMapIndex.get(key);
                Integer matchingSubId;
                MapItemSavedData matchingSubData;
                boolean isNew = (existingEntry == null);

                if (isNew) {
                    MapId newSubId = level.getFreeMapId();
                    matchingSubData = MapItemSavedData.createFresh(
                            targetX,
                            targetZ,
                            (byte) childScale,
                            true,
                            false,
                            parent.dimension
                    );
                    level.setMapData(newSubId, matchingSubData);
                    matchingSubId = newSubId.id();
                    subMapIndex.put(key, new SubMapEntry(matchingSubId, matchingSubData));
                    resultSubMapIds.add(matchingSubId);
                    changed = true;
                } else {
                    matchingSubId = existingEntry.id();
                    matchingSubData = existingEntry.data();
                }

                // Initialize if newly created or completely empty
                boolean isEmpty = true;
                for (byte b : matchingSubData.colors) {
                    if (b != 0) {
                        isEmpty = false;
                        break;
                    }
                }

                if (isNew || isEmpty) {
                    initializeSubMapFromParent(parent, matchingSubData, q);
                    matchingSubData.setDirty();
                }

                // Only explore natively if the player is physically inside this submap!
                if (player != null && level.dimension().equals(parent.dimension)
                        && isPlayerInsideMap(player, matchingSubData)
                        && needsNativeExploration(parent, matchingSubData, q)) {
                    fullyExploreSubMap(level, player, parent, matchingSubData, q);
                }
            }
        }

        if (changed || resultSubMapIds.size() != contents.subMapIds().size()) {
            return contents.withSubMaps(List.copyOf(resultSubMapIds));
        }
        return contents;
    }

    private static boolean validateScaleInputs(ServerLevel level, AtlasContents contents) {
        if (contents.mapIds().isEmpty()) {
            return false;
        }

        Integer originScale = null;
        for (int rawId : contents.mapIds()) {
            MapItemSavedData mapData = level.getMapData(new MapId(rawId));
            if (mapData == null || mapData.locked || mapData.scale >= MapItemSavedData.MAX_SCALE) {
                return false;
            }

            int scale = mapData.scale;
            if (originScale == null) {
                originScale = scale;
                continue;
            }

            if (originScale != scale) {
                return false;
            }
        }

        return true;
    }

    // ----- Finer-map integration (insert lower-scale map into atlas) -----

    /**
     * Returns true if {@code finerId} can be integrated into {@code contents}:
     * the map must be finer (lower scale) than the atlas, and either an existing
     * atlas tile already covers that world area (enrichment case, no new slot
     * needed) or the atlas has room for a new tile.
     */
    public static boolean canIntegrateFinerMap(ServerLevel level, AtlasContents contents, MapId finerId) {
        if (contents.mapIds().isEmpty()) {
            return false;
        }

        MapItemSavedData finerData = level.getMapData(finerId);
        if (finerData == null) {
            return false;
        }

        MapItemSavedData originData = level.getMapData(new MapId(contents.mapIds().getFirst()));
        if (originData == null || finerData.scale >= originData.scale) {
            return false;
        }

        // Compute the atlas-scale cell that would cover the finer map's center.
        MapItemSavedData atlasCell = MapItemSavedData.createFresh(
                finerData.centerX, finerData.centerZ, originData.scale,
                true, false, finerData.dimension);

        // Accept if atlas already has a tile at that cell (enrichment, no new ID).
        for (int rawId : contents.mapIds()) {
            MapItemSavedData existing = level.getMapData(new MapId(rawId));
            if (existing != null
                    && existing.dimension.equals(finerData.dimension)
                    && existing.centerX == atlasCell.centerX
                    && existing.centerZ == atlasCell.centerZ
                    && existing.scale == originData.scale) {
                return true;
            }
        }

        // Accept if atlas has room for a brand-new tile.
        return contents.canAddMapId();
    }

    /**
     * Projects the pixels of a finer-scale map into the atlas:
     * <ul>
     *   <li>If an atlas tile already covers the area, it is enriched in-place.</li>
     *   <li>Otherwise a new atlas-scale tile is created and added to the atlas.</li>
     * </ul>
     * Waypoints and other atlas metadata are preserved unchanged.
     *
     * @return updated {@link AtlasContents}, or {@code null} if integration is not possible.
     */
    public static @Nullable AtlasContents integrateFinerMap(ServerLevel level, AtlasContents contents, MapId finerId) {
        if (contents.mapIds().isEmpty()) {
            return null;
        }

        MapItemSavedData finerData = level.getMapData(finerId);
        if (finerData == null) {
            return null;
        }

        MapItemSavedData originData = level.getMapData(new MapId(contents.mapIds().getFirst()));
        if (originData == null || finerData.scale >= originData.scale) {
            return null;
        }

        // Find the atlas-scale cell center for the finer map.
        MapItemSavedData atlasCell = MapItemSavedData.createFresh(
                finerData.centerX, finerData.centerZ, originData.scale,
                true, false, finerData.dimension);

        // Enrich an existing atlas tile if it covers this area.
        for (int rawId : contents.mapIds()) {
            MapItemSavedData existing = level.getMapData(new MapId(rawId));
            if (existing != null
                    && existing.dimension.equals(finerData.dimension)
                    && existing.centerX == atlasCell.centerX
                    && existing.centerZ == atlasCell.centerZ
                    && existing.scale == originData.scale) {
                projectKnownPixelsIntoScaledMap(finerData, existing);
                // Do NOT apply jagged edge effect when merging a finer-scale map into an atlas tile
                if (finerData.scale < originData.scale && !contents.subMapIds().contains(finerId.id())) {
                    var newSubs = new java.util.LinkedHashSet<>(contents.subMapIds());
                    newSubs.add(finerId.id());
                    return contents.withSubMaps(List.copyOf(newSubs));
                }
                return contents; // map_ids unchanged; underlying tile data enriched
            }
        }

        // No existing tile covers this area – create a new one.
        if (!contents.canAddMapId()) {
            return null;
        }

        projectKnownPixelsIntoScaledMap(finerData, atlasCell);
        // Do NOT apply jagged edge effect when merging a finer-scale map into an atlas tile
        MapId newId = level.getFreeMapId();
        level.setMapData(newId, atlasCell);
        AtlasContents updated = contents.withAdded(newId.id());
        if (finerData.scale < originData.scale && !updated.subMapIds().contains(finerId.id())) {
            var newSubs = new java.util.LinkedHashSet<>(updated.subMapIds());
            newSubs.add(finerId.id());
            updated = updated.withSubMaps(List.copyOf(newSubs));
        }
        return updated;
    }

    // ----- Shared pixel projection -----

    /**
     * Copies known (non-zero) pixels from a finer source map into a coarser target map,
     * skipping target pixels that are already filled.
     * Uses {@link MapItemSavedData#setColor} so dirty-marking and live player updates
     * are handled correctly for both new and existing registered maps.
     */
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

    /**
     * Simulates the jagged exploration-border look of a naturally explored vanilla map by
     * applying a checkerboard erasure pattern to the outermost two rows of filled pixels
     * that border unexplored (zero) pixels.
     * <p>
     * Two passes are used:
     * <ol>
     *   <li><b>Outer row</b> – pixels directly adjacent to an empty pixel are erased when
     *       {@code (x + y) % 2 == 0}.</li>
     *   <li><b>Inner row</b> – pixels whose only empty neighbour is through an outer-row pixel
     *       are erased when {@code (x + y) % 2 == 1} (complementary phase), creating the
     *       alternating appearance between the two rows.</li>
     * </ol>
     * A snapshot of the original colours is used for border classification so that the first
     * erasure pass does not affect the second.
     */
    private static void applyExplorationEdgeShading(MapItemSavedData target, boolean[] newlyFilled, boolean[] projectedCoverage) {
        byte[] snapshot = target.colors.clone();

        // Pass 1 – classify pixels as outer (directly adjacent to empty) or inner
        // (adjacent to an outer pixel but not itself adjacent to empty).
        // 0 = interior, 1 = outer border, 2 = inner border
        byte[] borderClass = new byte[MAP_PIXEL_COUNT];

        for (int y = 0; y < MAP_SIZE; y++) {
            for (int x = 0; x < MAP_SIZE; x++) {
                int index = x + y * MAP_SIZE;
                if ((snapshot[index] & 0xFF) == 0 || !newlyFilled[index] || !projectedCoverage[index]) continue;

                // Check 4-connected interior neighbours only – map edges are not exploration borders.
                boolean adjEmpty =
                        (y > 0   && snapshot[x + (y - 1) * MAP_SIZE] == 0) ||
                        (y < MAP_SIZE - 1 && snapshot[x + (y + 1) * MAP_SIZE] == 0) ||
                        (x > 0   && snapshot[(x - 1) + y * MAP_SIZE] == 0) ||
                        (x < MAP_SIZE - 1 && snapshot[(x + 1) + y * MAP_SIZE] == 0);

                if (adjEmpty) {
                    borderClass[index] = 1; // outer
                }
            }
        }

        for (int y = 0; y < MAP_SIZE; y++) {
            for (int x = 0; x < MAP_SIZE; x++) {
                int index = x + y * MAP_SIZE;
                if ((snapshot[index] & 0xFF) == 0 || !newlyFilled[index]) continue;
                if (borderClass[index] != 0) continue; // already classified

                // Inner: at least one 4-connected neighbour is an outer-border pixel.
                boolean adjOuter =
                        (y > 0   && borderClass[x + (y - 1) * MAP_SIZE] == 1) ||
                        (y < MAP_SIZE - 1 && borderClass[x + (y + 1) * MAP_SIZE] == 1) ||
                        (x > 0   && borderClass[(x - 1) + y * MAP_SIZE] == 1) ||
                        (x < MAP_SIZE - 1 && borderClass[(x + 1) + y * MAP_SIZE] == 1);

                if (adjOuter) {
                    borderClass[index] = 2; // inner
                }
            }
        }

        // Pass 2 – erase alternating pixels in each border row (checkerboard).
        for (int y = 0; y < MAP_SIZE; y++) {
            for (int x = 0; x < MAP_SIZE; x++) {
                byte cls = borderClass[x + y * MAP_SIZE];
                if (cls == 0) continue;

                // The natural +1 row/column offset between outer and inner shifts parity automatically,
                // so using the same comparison for both produces the interlocked alternating look.
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

    // ----- Shared dedupe key -----

    private record ScaledMapKey(net.minecraft.resources.ResourceKey<Level> dimension, int centerX, int centerZ, byte scale) {
        private static ScaledMapKey from(MapItemSavedData mapData) {
            return new ScaledMapKey(mapData.dimension, mapData.centerX, mapData.centerZ, mapData.scale);
        }
    }
}
