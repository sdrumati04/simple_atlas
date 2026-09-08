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
    public static final AtlasContents EMPTY = new AtlasContents(List.of(), List.of(), 0, 1, 0, List.of());
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
                            .forGetter(AtlasContents::subMapIds)
            ).apply(instance, AtlasContents::new)
    );

    // LinkedHashSet: O(1) contains() + insertion-order iteration
    private final SequencedSet<Integer> mapIdSet;
    private final SequencedSet<Integer> subMapIdSet;
    private final List<WaypointData> waypoints;
    private final int selectedWaypointIconIndex;
    private final int nextWaypointNumber;
    private final int blankMapCount;

    public AtlasContents(
            List<Integer> mapIds,
            List<WaypointData> waypoints,
            int selectedWaypointIconIndex,
            int nextWaypointNumber,
            int blankMapCount
    ) {
        this(mapIds, waypoints, selectedWaypointIconIndex, nextWaypointNumber, blankMapCount, List.of());
    }

    public AtlasContents(
            List<Integer> mapIds,
            List<WaypointData> waypoints,
            int selectedWaypointIconIndex,
            int nextWaypointNumber,
            int blankMapCount,
            List<Integer> subMapIds
    ) {
        this.mapIdSet = cappedMapIdSet(mapIds);
        this.subMapIdSet = cappedMapIdSet(subMapIds);
        this.waypoints = List.copyOf(waypoints);
        this.selectedWaypointIconIndex = Math.max(0, selectedWaypointIconIndex);
        this.nextWaypointNumber = Math.max(1, nextWaypointNumber);
        // Legacy compatibility field: blank-map inventory is no longer used.
        this.blankMapCount = 0;
    }

    /** Returns map IDs in insertion order. */
    public List<Integer> mapIds() {
        return List.copyOf(mapIdSet);
    }

    public List<Integer> subMapIds() {
        return List.copyOf(subMapIdSet);
    }

    public List<Integer> allMapIds() {
        LinkedHashSet<Integer> all = new LinkedHashSet<>(mapIdSet);
        all.addAll(subMapIdSet);
        return List.copyOf(all);
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

    public boolean contains(int mapId) {
        return mapIdSet.contains(mapId);
    }

    public boolean containsSubMap(int mapId) {
        return subMapIdSet.contains(mapId);
    }

    public AtlasContents withAdded(int mapId) {
        if (contains(mapId) || mapIdSet.size() >= configuredMapLimit()) {
            return this;
        }

        LinkedHashSet<Integer> updated = new LinkedHashSet<>(mapIdSet);
        updated.add(mapId);
        return new AtlasContents(List.copyOf(updated), waypoints, selectedWaypointIconIndex, nextWaypointNumber, blankMapCount, subMapIds());
    }

    public AtlasContents withSubMaps(List<Integer> subMapIds) {
        return new AtlasContents(mapIds(), waypoints, selectedWaypointIconIndex, nextWaypointNumber, blankMapCount, subMapIds);
    }

    public AtlasContents withWaypointState(List<WaypointData> waypoints, int selectedWaypointIconIndex, int nextWaypointNumber) {
        return new AtlasContents(mapIds(), waypoints, selectedWaypointIconIndex, nextWaypointNumber, blankMapCount, subMapIds());
    }

    public boolean canAddMapId() {
        return mapIdSet.size() < configuredMapLimit();
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
                && subMapIdSet.equals(other.subMapIdSet)
                && waypoints.equals(other.waypoints)
                && selectedWaypointIconIndex == other.selectedWaypointIconIndex
                && nextWaypointNumber == other.nextWaypointNumber;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mapIdSet, subMapIdSet, waypoints, selectedWaypointIconIndex, nextWaypointNumber);
    }

    @Override
    public String toString() {
        return "AtlasContents{mapIds=" + mapIdSet + ", subMapIds=" + subMapIdSet + ", waypoints=" + waypoints.size() + "}";
    }
}
