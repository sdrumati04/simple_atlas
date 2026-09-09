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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

        List<Integer> subMapIds = new java.util.ArrayList<>();
        MapItemSavedData firstOriginal = level.getMapData(new MapId(contents.mapIds().getFirst()));
        if (firstOriginal != null && firstOriginal.scale == 0) {
            subMapIds.addAll(contents.mapIds());
        }

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
        if (parent == null || subMap == null || parent.scale != 1 || subMap.scale != 0) {
            return;
        }

        int parentStartX = (quadrant % 2 == 0) ? 0 : 64;
        int parentStartZ = (quadrant < 2) ? 0 : 64;
        boolean hasRemapped = MapModCompat.isRemappedLoaded();

        for (int subX = 0; subX < MAP_SIZE; subX++) {
            int parentPx = parentStartX + (subX / 2);
            for (int subZ = 0; subZ < MAP_SIZE; subZ++) {
                int parentPz = parentStartZ + (subZ / 2);
                int pIdx = parentPx + parentPz * MAP_SIZE;
                byte pColor = parent.colors[pIdx];

                if (pColor == 0) {
                    subMap.setColor(subX, subZ, (byte) 0);
                    if (hasRemapped) {
                        MapModCompat.setRemappedColor(subMap, subX, subZ, 0);
                    }
                    continue;
                }

                subMap.setColor(subX, subZ, pColor);
                if (hasRemapped) {
                    int pRemapped = MapModCompat.getRemappedColor(parent, parentPx, parentPz);
                    if (pRemapped != 0) {
                        MapModCompat.setRemappedColor(subMap, subX, subZ, pRemapped);
                    }
                }
            }
        }
    }

    public static void syncSubMapToParent(MapItemSavedData parent, MapItemSavedData subMap) {
        if (parent == null || subMap == null || parent.scale != 1 || subMap.scale != 0) {
            return;
        }
        int quadrant = (subMap.centerX > parent.centerX ? 1 : 0) + (subMap.centerZ > parent.centerZ ? 2 : 0);
        int parentStartX = (quadrant % 2 == 0) ? 0 : 64;
        int parentStartZ = (quadrant < 2) ? 0 : 64;

        boolean hasRemapped = MapModCompat.isRemappedLoaded();
        boolean parentChanged = false;

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
                                if (hasRemapped) {
                                    int sRem = MapModCompat.getRemappedColor(subMap, sIdx);
                                    if (sRem != 0) {
                                        sampleSubRemapped = sRem;
                                    }
                                }
                            }
                        }
                    }

                    if (sampleSubColor != 0) {
                        parent.setColor(pX, pZ, sampleSubColor);
                        if (sampleSubRemapped != 0) {
                            MapModCompat.setRemappedColor(parent, pX, pZ, sampleSubRemapped);
                        }
                        parentChanged = true;
                    }
                }
            }
        }

        if (parentChanged) {
            parent.setDirty();
        }
    }

    public static boolean needs1to1Exploration(MapItemSavedData parent, MapItemSavedData subMap, int quadrant) {
        if (parent == null || subMap == null || parent.scale != 1 || subMap.scale != 0) {
            return false;
        }

        int parentStartX = (quadrant % 2 == 0) ? 0 : 64;
        int parentStartZ = (quadrant < 2) ? 0 : 64;

        int totalExploredPairs = 0;
        int identicalPairs = 0;
        boolean missingParentCoverage = false;

        for (int y = 0; y < MAP_SIZE; y += 4) {
            int parentPz = parentStartZ + (y / 2);
            for (int x = 0; x < MAP_SIZE; x += 4) {
                int parentPx = parentStartX + (x / 2);
                byte pCol = parent.colors[parentPx + parentPz * MAP_SIZE];
                byte sCol1 = subMap.colors[x + y * MAP_SIZE];
                byte sCol2 = subMap.colors[(x + 1) + y * MAP_SIZE];

                if (pCol != 0 && sCol1 == 0) {
                    missingParentCoverage = true;
                }

                if (sCol1 != 0 && sCol2 != 0) {
                    totalExploredPairs++;
                    if (sCol1 == sCol2) {
                        identicalPairs++;
                    }
                }
            }
        }

        if (missingParentCoverage) {
            return true;
        }

        return totalExploredPairs > 20 && identicalPairs == totalExploredPairs;
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
            player.setPosRaw(subMap.centerX, origPos.y, subMap.centerZ);
            for (int step = 0; step < 16; step++) {
                ((MapItem) Items.FILLED_MAP).update(level, player, subMap);
            }
        } finally {
            player.setPosRaw(origPos.x, origPos.y, origPos.z);
        }

        boolean hasRemapped = MapModCompat.isRemappedLoaded();
        for (int subX = 0; subX < MAP_SIZE; subX++) {
            int parentPx = parentStartX + (subX / 2);
            for (int subZ = 0; subZ < MAP_SIZE; subZ++) {
                int parentPz = parentStartZ + (subZ / 2);
                if (parent.colors[parentPx + parentPz * MAP_SIZE] == 0) {
                    subMap.setColor(subX, subZ, (byte) 0);
                    if (hasRemapped) {
                        MapModCompat.setRemappedColor(subMap, subX, subZ, 0);
                    }
                }
            }
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
        if (first == null || first.scale != 1) {
            return contents;
        }

        LinkedHashSet<Integer> resultSubMapIds = new LinkedHashSet<>();
        boolean changed = false;

        int[] dxOffsets = {-64, 64, -64, 64};
        int[] dzOffsets = {-64, -64, 64, 64};

        for (int parentRawId : contents.mapIds()) {
            MapItemSavedData parent = level.getMapData(new MapId(parentRawId));
            if (parent == null || parent.scale != 1) {
                continue;
            }

            for (int q = 0; q < 4; q++) {
                int targetX = parent.centerX + dxOffsets[q];
                int targetZ = parent.centerZ + dzOffsets[q];

                Integer matchingSubId = null;
                MapItemSavedData matchingSubData = null;

                for (int subRawId : contents.subMapIds()) {
                    MapItemSavedData subData = level.getMapData(new MapId(subRawId));
                    if (subData != null
                            && subData.scale == 0
                            && subData.dimension.equals(parent.dimension)
                            && subData.centerX == targetX
                            && subData.centerZ == targetZ) {
                        matchingSubId = subRawId;
                        matchingSubData = subData;
                        break;
                    }
                }

                boolean isNew = (matchingSubId == null);
                if (isNew) {
                    MapId newSubId = level.getFreeMapId();
                    matchingSubData = MapItemSavedData.createFresh(
                            targetX,
                            targetZ,
                            (byte) 0,
                            true,
                            false,
                            parent.dimension
                    );
                    level.setMapData(newSubId, matchingSubData);
                    matchingSubId = newSubId.id();
                    changed = true;
                }

                resultSubMapIds.add(matchingSubId);

                // Initialize if newly created, completely empty, or corrupted with black dots
                boolean isEmpty = true;
                for (byte b : matchingSubData.colors) {
                    if (b != 0) {
                        isEmpty = false;
                        break;
                    }
                }

                boolean hasRemapped = MapModCompat.isRemappedLoaded();
                int corruptedBlackDots = 0;
                int parentStartX = (q % 2 == 0) ? 0 : 64;
                int parentStartZ = (q < 2) ? 0 : 64;

                if (!isNew && !isEmpty && hasRemapped) {
                    for (int subX = 0; subX < MAP_SIZE; subX++) {
                        int parentPx = parentStartX + (subX / 2);
                        for (int subZ = 0; subZ < MAP_SIZE; subZ++) {
                            int parentPz = parentStartZ + (subZ / 2);
                            int pRem = MapModCompat.getRemappedColor(parent, parentPx, parentPz);
                            int sRem = MapModCompat.getRemappedColor(matchingSubData, subX, subZ);
                            // If parent has a non-black color, but submap pixel is black (0 or #000000)
                            if ((pRem & 0xFFFFFF) != 0 && (sRem & 0xFFFFFF) == 0 && matchingSubData.colors[subX + subZ * MAP_SIZE] != 0) {
                                corruptedBlackDots++;
                            }
                        }
                    }
                }

                if (isNew || isEmpty || corruptedBlackDots > 10) {
                    initializeSubMapFromParent(parent, matchingSubData, q);
                    matchingSubData.setDirty();
                } else if (corruptedBlackDots > 0) {
                    for (int subX = 0; subX < MAP_SIZE; subX++) {
                        int parentPx = parentStartX + (subX / 2);
                        for (int subZ = 0; subZ < MAP_SIZE; subZ++) {
                            int parentPz = parentStartZ + (subZ / 2);
                            int pRem = MapModCompat.getRemappedColor(parent, parentPx, parentPz);
                            int sRem = MapModCompat.getRemappedColor(matchingSubData, subX, subZ);
                            int sIdx = subX + subZ * MAP_SIZE;
                            if ((pRem & 0xFFFFFF) != 0 && (sRem & 0xFFFFFF) == 0 && matchingSubData.colors[sIdx] != 0) {
                                matchingSubData.setColor(subX, subZ, parent.colors[parentPx + parentPz * MAP_SIZE]);
                                MapModCompat.setRemappedColor(matchingSubData, subX, subZ, pRem);
                            }
                        }
                    }
                    matchingSubData.setDirty();
                }

                if (player != null && level.dimension().equals(parent.dimension) && needs1to1Exploration(parent, matchingSubData, q)) {
                    fullyExploreSubMap(level, player, parent, matchingSubData, q);
                }
            }
        }

        // Also preserve any existing submaps that belong to other valid finer maps
        for (int subRawId : contents.subMapIds()) {
            if (!resultSubMapIds.contains(subRawId)) {
                MapItemSavedData subData = level.getMapData(new MapId(subRawId));
                if (subData != null && subData.scale == 0) {
                    boolean duplicateCoord = false;
                    for (int existingId : resultSubMapIds) {
                        MapItemSavedData existingData = level.getMapData(new MapId(existingId));
                        if (existingData != null
                                && existingData.dimension.equals(subData.dimension)
                                && existingData.centerX == subData.centerX
                                && existingData.centerZ == subData.centerZ) {
                            duplicateCoord = true;
                            break;
                        }
                    }
                    if (!duplicateCoord) {
                        resultSubMapIds.add(subRawId);
                    } else {
                        changed = true;
                    }
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
                if (finerData.scale == 0 && !contents.subMapIds().contains(finerId.id())) {
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
        if (finerData.scale == 0 && !updated.subMapIds().contains(finerId.id())) {
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

        for (int sourceY = 0; sourceY < MAP_SIZE; sourceY++) {
            for (int sourceX = 0; sourceX < MAP_SIZE; sourceX++) {
                byte color = source.colors[sourceX + sourceY * MAP_SIZE];
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
                    if (MapModCompat.isRemappedLoaded()) {
                        int remColor = MapModCompat.getRemappedColor(source, sourceX, sourceY);
                        if (remColor != 0) {
                            MapModCompat.setRemappedColor(target, targetX, targetY, remColor);
                        }
                    }
                }
            }
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
