package rubbertoe.simple_atlas.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import rubbertoe.simple_atlas.config.SimpleAtlasConfig;
import rubbertoe.simple_atlas.config.SimpleAtlasConfigManager;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.SequencedSet;

public final class AtlasContents {
    public static final int HARD_MAX_ATLAS_MAP_COUNT = SimpleAtlasConfig.MAX_ATLAS_MAP_COUNT;
    public static final AtlasContents EMPTY = new AtlasContents(List.of(), List.of(), 0, 1, 0, -1, 0, false);
    public static final String DEFAULT_DIMENSION = "minecraft:overworld";

    private static final int MAX_WAYPOINT_NAME_LENGTH = 32;

    public record WaypointData(double worldX, double worldZ, String name, int iconIndex, String dimension) {
        public static final Codec<WaypointData> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.DOUBLE.fieldOf("world_x").forGetter(WaypointData::worldX),
                        Codec.DOUBLE.fieldOf("world_z").forGetter(WaypointData::worldZ),
                        Codec.STRING.fieldOf("name").forGetter(WaypointData::name),
                        Codec.INT.fieldOf("icon_index").forGetter(WaypointData::iconIndex),
                        Codec.STRING.optionalFieldOf("dimension", "minecraft:overworld").forGetter(WaypointData::dimension)
                ).apply(instance, WaypointData::new)
        );

        public WaypointData {
            name = sanitizeName(name);
            iconIndex = Math.max(0, iconIndex);
            dimension = sanitizeDimension(dimension);
        }
    }

    public static final Codec<AtlasContents> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.listOf()
                            .optionalFieldOf("map_ids", List.of())
                            .forGetter(AtlasContents::mapIds),
                    WaypointData.CODEC.listOf()
                            .optionalFieldOf("waypoints", List.of())
                            .forGetter(AtlasContents::waypoints),
                    Codec.INT.optionalFieldOf("selected_waypoint_icon_index", 0)
                            .forGetter(AtlasContents::selectedWaypointIconIndex),
                    Codec.INT.optionalFieldOf("next_waypoint_number", 1)
                            .forGetter(AtlasContents::nextWaypointNumber),
                    Codec.INT.optionalFieldOf("blank_map_count", 0)
                            .forGetter(AtlasContents::blankMapCount),
                    Codec.INT.listOf()
                            .optionalFieldOf("sub_map_ids", List.of())
                            .forGetter(_ -> List.of()),
                    Codec.INT.optionalFieldOf("selected_scale", -1)
                            .forGetter(AtlasContents::selectedScale),
                    Codec.INT.optionalFieldOf("paper_count", 0)
                            .forGetter(AtlasContents::paperCount),
                    Codec.BOOL.optionalFieldOf("transcribing", false)
                            .forGetter(AtlasContents::transcribing)
            ).apply(instance, (mapIds, waypoints, selectedIcon, nextNum, blankCount, _legacySubMaps, selectedScale, paperCount, transcribing) ->
                    new AtlasContents(mapIds, waypoints, selectedIcon, nextNum, blankCount, selectedScale, paperCount, transcribing)
            )
    );

    // LinkedHashSet: O(1) contains() + insertion-order iteration
    private final SequencedSet<Integer> mapIdSet;
    private final List<WaypointData> waypoints;
    private final int selectedWaypointIconIndex;
    private final int nextWaypointNumber;
    private final int blankMapCount;
    private final int selectedScale;
    private final int paperCount;
    private final boolean transcribing;

    public AtlasContents(
            List<Integer> mapIds,
            List<WaypointData> waypoints,
            int selectedWaypointIconIndex,
            int nextWaypointNumber,
            int blankMapCount,
            int selectedScale
    ) {
        this(mapIds, waypoints, selectedWaypointIconIndex, nextWaypointNumber, blankMapCount, selectedScale, 0, false);
    }

    public AtlasContents(
            List<Integer> mapIds,
            List<WaypointData> waypoints,
            int selectedWaypointIconIndex,
            int nextWaypointNumber,
            int blankMapCount,
            int selectedScale,
            int paperCount
    ) {
        this(mapIds, waypoints, selectedWaypointIconIndex, nextWaypointNumber, blankMapCount, selectedScale, paperCount, false);
    }

    public AtlasContents(
            List<Integer> mapIds,
            List<WaypointData> waypoints,
            int selectedWaypointIconIndex,
            int nextWaypointNumber,
            int blankMapCount,
            int selectedScale,
            int paperCount,
            boolean transcribing
    ) {
        this.mapIdSet = cappedMapIdSet(mapIds);
        this.waypoints = List.copyOf(waypoints);
        this.selectedWaypointIconIndex = Math.max(0, selectedWaypointIconIndex);
        this.nextWaypointNumber = Math.max(1, nextWaypointNumber);
        this.blankMapCount = Math.max(0, blankMapCount);
        this.selectedScale = selectedScale;
        this.paperCount = Math.max(0, paperCount);
        this.transcribing = transcribing;
    }

    /** Returns map IDs in insertion order. */
    public List<Integer> mapIds() {
        return List.copyOf(mapIdSet);
    }

    /** Returns map IDs (alias for mapIds for compatibility). */
    public List<Integer> allMapIds() {
        return mapIds();
    }

    /** Legacy compatibility method returning empty list. */
    public List<Integer> subMapIds() {
        return List.of();
    }

    public List<WaypointData> waypoints() {
        return waypoints;
    }

    public int selectedWaypointIconIndex() {
        return selectedWaypointIconIndex;
    }

    public int nextWaypointNumber() {
        return nextWaypointNumber;
    }

    public int blankMapCount() {
        return blankMapCount;
    }

    public int paperCount() {
        return paperCount;
    }

    public boolean transcribing() {
        return transcribing;
    }

    public int selectedScale() {
        return selectedScale;
    }

    public boolean contains(int mapId) {
        return mapIdSet.contains(mapId);
    }

    public AtlasContents withSelectedScale(int scale) {
        if (this.selectedScale == scale) {
            return this;
        }
        return new AtlasContents(mapIds(), waypoints, selectedWaypointIconIndex, nextWaypointNumber, blankMapCount, scale, paperCount, transcribing);
    }

    public AtlasContents withTranscribing(boolean transcribing) {
        if (this.transcribing == transcribing) {
            return this;
        }
        return new AtlasContents(mapIds(), waypoints, selectedWaypointIconIndex, nextWaypointNumber, blankMapCount, selectedScale, paperCount, transcribing);
    }

    public AtlasContents withAdded(int mapId) {
        if (contains(mapId) || mapIdSet.size() >= configuredMapLimit()) {
            return this;
        }

        LinkedHashSet<Integer> updated = new LinkedHashSet<>(mapIdSet);
        updated.add(mapId);
        return new AtlasContents(List.copyOf(updated), waypoints, selectedWaypointIconIndex, nextWaypointNumber, blankMapCount, selectedScale, paperCount, transcribing);
    }

    public AtlasContents withAddedAll(java.util.Collection<Integer> newMapIds) {
        if (newMapIds == null || newMapIds.isEmpty()) {
            return this;
        }

        int limit = configuredMapLimit();
        LinkedHashSet<Integer> updated = new LinkedHashSet<>(mapIdSet);
        for (int id : newMapIds) {
            if (updated.size() >= limit) {
                break;
            }
            updated.add(id);
        }
        return new AtlasContents(List.copyOf(updated), waypoints, selectedWaypointIconIndex, nextWaypointNumber, blankMapCount, selectedScale, paperCount, transcribing);
    }

    public AtlasContents withRemoved(int mapId) {
        if (!contains(mapId)) {
            return this;
        }

        LinkedHashSet<Integer> updated = new LinkedHashSet<>(mapIdSet);
        updated.remove(mapId);
        return new AtlasContents(List.copyOf(updated), waypoints, selectedWaypointIconIndex, nextWaypointNumber, blankMapCount, selectedScale, paperCount, transcribing);
    }

    public AtlasContents withWaypointState(List<WaypointData> waypoints, int selectedWaypointIconIndex, int nextWaypointNumber) {
        return new AtlasContents(mapIds(), waypoints, selectedWaypointIconIndex, nextWaypointNumber, blankMapCount, selectedScale, paperCount, transcribing);
    }

    public AtlasContents withBlankMapCount(int count) {
        int sanitized = Math.max(0, count);
        if (this.blankMapCount == sanitized) {
            return this;
        }
        return new AtlasContents(mapIds(), waypoints, selectedWaypointIconIndex, nextWaypointNumber, sanitized, selectedScale, paperCount, transcribing);
    }

    public AtlasContents withAddedBlankMaps(int amount) {
        if (amount <= 0) {
            return this;
        }
        return withBlankMapCount(this.blankMapCount + amount);
    }

    public AtlasContents withConsumedBlankMap() {
        if (this.blankMapCount <= 0) {
            return this;
        }
        return withBlankMapCount(this.blankMapCount - 1);
    }

    public AtlasContents withPaperCount(int count) {
        int sanitized = Math.max(0, count);
        if (this.paperCount == sanitized) {
            return this;
        }
        return new AtlasContents(mapIds(), waypoints, selectedWaypointIconIndex, nextWaypointNumber, blankMapCount, selectedScale, sanitized, transcribing);
    }

    public AtlasContents withAddedPaper(int amount) {
        if (amount <= 0) {
            return this;
        }
        return withPaperCount(this.paperCount + amount);
    }

    public boolean canAddMapId() {
        return mapIdSet.size() < configuredMapLimit();
    }

    public boolean canAddMapCount(int count) {
        return mapIdSet.size() + count <= configuredMapLimit();
    }

    public int size() {
        return mapIdSet.size();
    }

    private static String sanitizeName(String name) {
        if (name == null) {
            return "";
        }

        String trimmed = name.trim();
        if (trimmed.length() > MAX_WAYPOINT_NAME_LENGTH) {
            return trimmed.substring(0, MAX_WAYPOINT_NAME_LENGTH);
        }

        return trimmed;
    }

    public static String sanitizeDimension(String dimension) {
        if (dimension == null) {
            return DEFAULT_DIMENSION;
        }

        String trimmed = dimension.trim();
        if (trimmed.isEmpty() || Identifier.tryParse(trimmed) == null) {
            return DEFAULT_DIMENSION;
        }

        return trimmed;
    }

    private static SequencedSet<Integer> cappedMapIdSet(List<Integer> mapIds) {
        int mapLimit = configuredMapLimit();
        LinkedHashSet<Integer> capped = new LinkedHashSet<>();
        for (int mapId : mapIds) {
            if (capped.size() >= mapLimit) {
                break;
            }
            capped.add(mapId);
        }
        return capped;
    }

    private static int configuredMapLimit() {
        return Math.min(SimpleAtlasConfigManager.getMaxAtlasMapCount(), HARD_MAX_ATLAS_MAP_COUNT);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AtlasContents other)) return false;
        return mapIdSet.equals(other.mapIdSet)
                && waypoints.equals(other.waypoints)
                && selectedWaypointIconIndex == other.selectedWaypointIconIndex
                && nextWaypointNumber == other.nextWaypointNumber
                && blankMapCount == other.blankMapCount
                && selectedScale == other.selectedScale
                && paperCount == other.paperCount
                && transcribing == other.transcribing;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mapIdSet, waypoints, selectedWaypointIconIndex, nextWaypointNumber, blankMapCount, selectedScale, paperCount, transcribing);
    }

    @Override
    public String toString() {
        return "AtlasContents{mapIds=" + mapIdSet + ", waypoints=" + waypoints.size() + ", blankMaps=" + blankMapCount + ", paper=" + paperCount + ", selectedScale=" + selectedScale + ", transcribing=" + transcribing + "}";
    }
}
