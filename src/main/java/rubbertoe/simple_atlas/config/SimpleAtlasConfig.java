package rubbertoe.simple_atlas.config;

public final class SimpleAtlasConfig {
    public static final int MIN_ATLAS_MAP_COUNT = 1;
    public static final int MAX_ATLAS_MAP_COUNT = 256;
    public static final int DEFAULT_MAX_ATLAS_MAP_COUNT = 256;

    public static final int MIN_WAYPOINT_COUNT = 1;
    public static final int MAX_WAYPOINT_COUNT = 256;
    public static final int DEFAULT_MAX_WAYPOINT_COUNT = 256;
    public static final boolean DEFAULT_BANNER_WAYPOINTS_ONLY = false;

    public static final double MIN_ICON_SIZE = 0.5;
    public static final double MAX_ICON_SIZE = 2.0;
    public static final double DEFAULT_WAYPOINT_ICON_SIZE = 1.0;
    public static final double DEFAULT_PLAYER_ICON_SIZE = 1.0;
    public static final boolean DEFAULT_CONSUME_PAPER_FOR_HIGHER_SCALES = true;

    public int maxAtlasMapCount = DEFAULT_MAX_ATLAS_MAP_COUNT;
    public int maxWaypoints = DEFAULT_MAX_WAYPOINT_COUNT;
    public boolean bannerWaypointsOnly = DEFAULT_BANNER_WAYPOINTS_ONLY;
    public double waypointIconSize = DEFAULT_WAYPOINT_ICON_SIZE;
    public double playerIconSize = DEFAULT_PLAYER_ICON_SIZE;
    public boolean consumePaperForHigherScales = DEFAULT_CONSUME_PAPER_FOR_HIGHER_SCALES;

    public static int clampMaxAtlasMapCount(int value) {
        return Math.clamp(value, MIN_ATLAS_MAP_COUNT, MAX_ATLAS_MAP_COUNT);
    }

    public static int clampMaxWaypoints(int value) {
        return Math.clamp(value, MIN_WAYPOINT_COUNT, MAX_WAYPOINT_COUNT);
    }

    public static double clampIconSize(double value) {
        return Math.clamp(value, MIN_ICON_SIZE, MAX_ICON_SIZE);
    }
}

