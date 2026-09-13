package rubbertoe.simple_atlas.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import rubbertoe.simple_atlas.SimpleAtlas;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SimpleAtlasConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("simple-atlas.json");

    private static SimpleAtlasConfig config = new SimpleAtlasConfig();

    private SimpleAtlasConfigManager() {}

    public static SimpleAtlasConfig get() {
        return config;
    }

    public static int getMaxAtlasMapCount() {
        return config.maxAtlasMapCount;
    }

    public static int getMaxWaypoints() {
        return config.maxWaypoints;
    }

    public static boolean isBannerWaypointsOnly() {
        return config.bannerWaypointsOnly;
    }

    public static double getWaypointIconSize() {
        return config.waypointIconSize;
    }

    public static double getPlayerIconSize() {
        return config.playerIconSize;
    }

    public static boolean isConsumePaperForHigherScales() {
        return config.consumePaperForHigherScales;
    }

    public static void load() {
        if (Files.notExists(CONFIG_PATH)) {
            config = sanitize(new SimpleAtlasConfig());
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            SimpleAtlasConfig loaded = GSON.fromJson(reader, SimpleAtlasConfig.class);
            config = sanitize(loaded != null ? loaded : new SimpleAtlasConfig());
        } catch (Exception e) {
            SimpleAtlas.LOGGER.error("Failed to load {}, falling back to defaults.", CONFIG_PATH.getFileName(), e);
            config = sanitize(new SimpleAtlasConfig());
            save();
        }
    }

    public static void save() {
        config = sanitize(config);

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            SimpleAtlas.LOGGER.error("Failed to save {}.", CONFIG_PATH.getFileName(), e);
        }
    }

    private static SimpleAtlasConfig sanitize(SimpleAtlasConfig raw) {
        SimpleAtlasConfig sanitized = raw == null ? new SimpleAtlasConfig() : raw;
        sanitized.maxAtlasMapCount = SimpleAtlasConfig.clampMaxAtlasMapCount(sanitized.maxAtlasMapCount);
        sanitized.maxWaypoints = SimpleAtlasConfig.clampMaxWaypoints(sanitized.maxWaypoints);
        sanitized.waypointIconSize = SimpleAtlasConfig.clampIconSize(sanitized.waypointIconSize);
        sanitized.playerIconSize = SimpleAtlasConfig.clampIconSize(sanitized.playerIconSize);
        return sanitized;
    }
}

