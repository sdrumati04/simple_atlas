package rubbertoe.simple_atlas.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SimpleAtlasConfigScreen {
    private SimpleAtlasConfigScreen() {}

    public static Screen create(Screen parent) {
        SimpleAtlasConfig config = SimpleAtlasConfigManager.get();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("config.simple_atlas.title"))
                .setSavingRunnable(SimpleAtlasConfigManager::save);

        ConfigCategory generalCategory = builder.getOrCreateCategory(Component.translatable("config.simple_atlas.category.general"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        generalCategory.addEntry(
                entryBuilder
                        .startIntField(Component.translatable("config.simple_atlas.max_atlas_map_count"), config.maxAtlasMapCount)
                        .setTooltip(Component.translatable("config.simple_atlas.max_atlas_map_count.tooltip"))
                        .setMin(SimpleAtlasConfig.MIN_ATLAS_MAP_COUNT)
                        .setMax(SimpleAtlasConfig.MAX_ATLAS_MAP_COUNT)
                        .setDefaultValue(SimpleAtlasConfig.DEFAULT_MAX_ATLAS_MAP_COUNT)
                        .setSaveConsumer(value -> config.maxAtlasMapCount = SimpleAtlasConfig.clampMaxAtlasMapCount(value))
                        .build()
        );

        generalCategory.addEntry(
                entryBuilder
                        .startIntField(Component.translatable("config.simple_atlas.max_waypoints"), config.maxWaypoints)
                        .setTooltip(Component.translatable("config.simple_atlas.max_waypoints.tooltip"))
                        .setMin(SimpleAtlasConfig.MIN_WAYPOINT_COUNT)
                        .setMax(SimpleAtlasConfig.MAX_WAYPOINT_COUNT)
                        .setDefaultValue(SimpleAtlasConfig.DEFAULT_MAX_WAYPOINT_COUNT)
                        .setSaveConsumer(value -> config.maxWaypoints = SimpleAtlasConfig.clampMaxWaypoints(value))
                        .build()
        );

        generalCategory.addEntry(
                entryBuilder
                        .startBooleanToggle(Component.translatable("config.simple_atlas.banner_waypoints_only"), config.bannerWaypointsOnly)
                        .setTooltip(Component.translatable("config.simple_atlas.banner_waypoints_only.tooltip"))
                        .setSaveConsumer(value -> config.bannerWaypointsOnly = value)
                        .build()
        );

        generalCategory.addEntry(
                entryBuilder
                        .startBooleanToggle(Component.translatable("config.simple_atlas.consume_paper_for_higher_scales"), config.consumePaperForHigherScales)
                        .setTooltip(Component.translatable("config.simple_atlas.consume_paper_for_higher_scales.tooltip"))
                        .setDefaultValue(SimpleAtlasConfig.DEFAULT_CONSUME_PAPER_FOR_HIGHER_SCALES)
                        .setSaveConsumer(value -> config.consumePaperForHigherScales = value)
                        .build()
        );

        generalCategory.addEntry(
                entryBuilder
                        .startDoubleField(Component.translatable("config.simple_atlas.waypoint_icon_size"), config.waypointIconSize)
                        .setTooltip(Component.translatable("config.simple_atlas.waypoint_icon_size.tooltip"))
                        .setMin(SimpleAtlasConfig.MIN_ICON_SIZE)
                        .setMax(SimpleAtlasConfig.MAX_ICON_SIZE)
                        .setDefaultValue(SimpleAtlasConfig.DEFAULT_WAYPOINT_ICON_SIZE)
                        .setSaveConsumer(value -> config.waypointIconSize = SimpleAtlasConfig.clampIconSize(value))
                        .build()
        );

        generalCategory.addEntry(
                entryBuilder
                        .startDoubleField(Component.translatable("config.simple_atlas.player_icon_size"), config.playerIconSize)
                        .setTooltip(Component.translatable("config.simple_atlas.player_icon_size.tooltip"))
                        .setMin(SimpleAtlasConfig.MIN_ICON_SIZE)
                        .setMax(SimpleAtlasConfig.MAX_ICON_SIZE)
                        .setDefaultValue(SimpleAtlasConfig.DEFAULT_PLAYER_ICON_SIZE)
                        .setSaveConsumer(value -> config.playerIconSize = SimpleAtlasConfig.clampIconSize(value))
                        .build()
        );

        return builder.build();
    }
}

