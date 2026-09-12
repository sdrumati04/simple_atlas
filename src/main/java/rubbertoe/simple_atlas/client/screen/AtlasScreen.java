package rubbertoe.simple_atlas.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import rubbertoe.simple_atlas.SimpleAtlas;
import rubbertoe.simple_atlas.client.input.ModKeyBindings;
import rubbertoe.simple_atlas.component.AtlasContents;
import rubbertoe.simple_atlas.config.SimpleAtlasConfigManager;
import rubbertoe.simple_atlas.client.screen.icon.AtlasIcon;
import rubbertoe.simple_atlas.client.screen.icon.PlayerAtlasIcon;
import rubbertoe.simple_atlas.client.screen.icon.StaticAtlasIcon;
import rubbertoe.simple_atlas.network.AtlasTilePayload;
import rubbertoe.simple_atlas.network.CloseAtlasViewPayload;
import rubbertoe.simple_atlas.network.NavigateToWaypointPayload;
import rubbertoe.simple_atlas.network.OpenAtlasScreenPayload;
import rubbertoe.simple_atlas.network.RemoveAtlasMapPayload;
import rubbertoe.simple_atlas.network.SaveAtlasWaypointsPayload;
import rubbertoe.simple_atlas.network.UnpinWaypointPayload;
import rubbertoe.simple_atlas.navigation.WaypointIconCatalog;
import rubbertoe.simple_atlas.server.AtlasWaypointDecorations;

import java.util.*;

public class AtlasScreen extends Screen {
    // Atlas/book shell layout
    private static final Identifier ATLAS_BACKGROUND_TEXTURE = Identifier.fromNamespaceAndPath(SimpleAtlas.MOD_ID, "textures/gui/atlas_background.png");
    private static final int ATLAS_BACKGROUND_TEXTURE_WIDTH = 256;
    private static final int ATLAS_BACKGROUND_TEXTURE_HEIGHT = 180;
    private static final float BOOK_TARGET_UI_SCALE = 3.0f;
    private static final int BOOK_SCREEN_MARGIN = 16;
    private static final int PAGE_AREA_X = 10;
    private static final int PAGE_AREA_Y = 18;
    private static final int PAGE_AREA_WIDTH = 236;
    private static final int PAGE_AREA_HEIGHT = 143;

    // Atlas icon rendering
    private static final Identifier PLAYER_MARKER_TEXTURE = Identifier.fromNamespaceAndPath(SimpleAtlas.MOD_ID, "textures/gui/player_marker.png");
    private static final Identifier PINNED_WAYPOINT_MARKER_TEXTURE = Identifier.fromNamespaceAndPath(SimpleAtlas.MOD_ID, "textures/gui/waypoint_pinned_marker.png");
    private static final int PLAYER_MARKER_TEXTURE_SIZE = 14;
    private static final int PLAYER_MARKER_RENDER_SIZE = 12;
    private static final int PINNED_WAYPOINT_MARKER_TEXTURE_SIZE = 8;
    private static final int PINNED_WAYPOINT_MARKER_RENDER_SIZE = 7;
    private static final int PINNED_WAYPOINT_MARKER_OFFSET_X = -3;
    private static final int PINNED_WAYPOINT_MARKER_OFFSET_Y = -2;
    private static final int WAYPOINT_TEXTURE_SIZE = 16;
    private static final int WAYPOINT_RENDER_SIZE = 12;
    private static final int WAYPOINT_NAME_MAX_LENGTH = 32;
    private static final int WAYPOINT_PICKER_PREVIEW_SIZE = 20;
    private static final int WAYPOINT_PICKER_PANEL_WIDTH = 148;
    private static final int WAYPOINT_PICKER_PANEL_HEIGHT = 105;
    private static final int WAYPOINT_PICKER_PADDING = 8;
    private static final int WAYPOINT_PICKER_ARROW_SIZE = 12;
    private static final int WAYPOINT_PICKER_INPUT_HEIGHT = 16;

    // Bookmark tab rendering
    private static final Identifier BOOKMARK_TAB_TEXTURE = Identifier.fromNamespaceAndPath(SimpleAtlas.MOD_ID, "textures/gui/tabs/bookmark.png");
    private static final Identifier BOOKMARK_TAB_SELECTED_TEXTURE = Identifier.fromNamespaceAndPath(SimpleAtlas.MOD_ID, "textures/gui/tabs/bookmark_selected.png");
    private static final Identifier BOOKMARK_OVERWORLD_ICON_TEXTURE = Identifier.fromNamespaceAndPath(SimpleAtlas.MOD_ID, "textures/gui/tabs/overworld_icon.png");
    private static final Identifier BOOKMARK_NETHER_ICON_TEXTURE = Identifier.fromNamespaceAndPath(SimpleAtlas.MOD_ID, "textures/gui/tabs/nether_icon.png");
    private static final Identifier BOOKMARK_END_ICON_TEXTURE = Identifier.fromNamespaceAndPath(SimpleAtlas.MOD_ID, "textures/gui/tabs/end_icon.png");
    private static final Identifier BOOKMARK_OTHER_ICON_TEXTURE = Identifier.fromNamespaceAndPath(SimpleAtlas.MOD_ID, "textures/gui/tabs/other_icon.png");
    /** Known dimension keys in display order. Any not listed fall back to OTHER. */
    private static final List<String> DIMENSION_ORDER = List.of(
            "minecraft:overworld",
            "minecraft:the_nether",
            "minecraft:the_end"
    );
    private static final Map<String, Identifier> DIMENSION_ICON_MAP = Map.of(
            "minecraft:overworld", BOOKMARK_OVERWORLD_ICON_TEXTURE,
            "minecraft:the_nether", BOOKMARK_NETHER_ICON_TEXTURE,
            "minecraft:the_end", BOOKMARK_END_ICON_TEXTURE
    );
    private static final int BOOKMARK_TAB_WIDTH = 30;
    private static final int BOOKMARK_TAB_HEIGHT = 30;
    private static final int BOOKMARK_ICON_TEXTURE_SIZE = 16;
    private static final int BOOKMARK_ICON_RENDER_SIZE = 14;
    private static final int BOOKMARK_TABS_TOP_OFFSET = -6;
    private static final int BOOKMARK_TABS_RIGHT_OVERLAP = -2;
    private static final int BOOKMARK_TAB_GAP = -8;
    private static final int BOOKMARK_CLIP_LEFT_OFFSET = 0;
    private static final int BOOKMARK_UNSELECTED_RETRACTION_AMOUNT = 6;

    // Context menu
    private static final int WAYPOINT_CONTEXT_MENU_WIDTH = 132;
    private static final int WAYPOINT_CONTEXT_MENU_ROW_HEIGHT = 14;
    private static final int WAYPOINT_CONTEXT_MENU_WAYPOINT_ROWS = 5;
    private static final int WAYPOINT_CONTEXT_MENU_MAP_ROWS = 4;

    // Map viewport rendering
    private static final int ICON_HOVER_TITLE_PADDING = 4;
    private static final int GRID_DASH_LENGTH = 6;
    private static final int GRID_DASH_GAP = 4;
    private static final int GRID_DASH_COLOR = 0x50D1BFA1;
    private static final int TILE_SIZE = 64;
    private static final float MIN_ZOOM = 0.0625f;
    private static final float MAX_ZOOM = 16.0f;
    private static final float ZOOM_STEP = 1.1f;

    // Immutable atlas payload/state
    private final List<AtlasTilePayload> tiles;
    private final List<Integer> atlasMapIds;
    private final PlayerAtlasIcon playerIcon;
    private final List<AtlasIcon> atlasIcons;
    private final List<AtlasContents.WaypointData> atlasWaypoints;
    private final List<WaypointIconOption> waypointIconOptions;
    /** Ordered list of dimension keys present in this atlas (e.g. "minecraft:overworld"). */
    private final List<String> dimensionTabs;
    /** Player's current dimension when atlas was opened. */
    private final String playerDimension;

    // Render cache
    private String cachedDimension = null;
    private int cachedScale = -1;
    private List<AtlasTilePayload> cachedVisibleTiles = List.of();
    private DimensionTileBounds cachedDimensionBounds = DimensionTileBounds.EMPTY;
    private boolean waypointsDirty = false;

    // Map interaction state
    private final Map<Integer, MapRenderState> renderStates = new HashMap<>();
    private double panX = 0;
    private double panY = 0;
    private boolean leftDragging = false;
    private float zoom = 2.0f;
    private int selectedBookmarkTab = 0;
    private int activeViewScale = 1;
    private @Nullable Button prevScaleButton = null;
    private @Nullable Button scaleToggleButton = null;
    private @Nullable Button nextScaleButton = null;

    // Waypoint draft/context menu state
    private int selectedWaypointIconIndex;
    private int nextWaypointNumber;
    private WaypointDraft waypointDraft;
    private int editingWaypointIndex = -1;
    private int contextMenuWaypointIndex = -1;
    private int contextMenuX;
    private int contextMenuY;
    private @Nullable WorldPoint contextMenuWorldPoint;
    private int contextMenuMapId = -1;
    private boolean skipWaypointSaveOnClose;

    // Overlay widgets
    private @Nullable EditBox waypointNameEditBox = null;
    private @Nullable Button previousWaypointIconButton = null;
    private @Nullable Button nextWaypointIconButton = null;
    private @Nullable Button confirmWaypointButton = null;
    private @Nullable Button cancelWaypointButton = null;
    private final List<Button> contextMenuButtons = new ArrayList<>();

    private class AtlasTextButton extends Button {
        private final int textColor;
        private final int backgroundColor;
        private final int hoveredBackgroundColor;
        private final int borderColor;
        private final boolean drawBorder;
        private final boolean centered;
        private final int textPadding;

        protected AtlasTextButton(
                int x,
                int y,
                int width,
                int height,
                Component message,
                int textColor,
                int backgroundColor,
                int hoveredBackgroundColor,
                int borderColor,
                boolean drawBorder,
                boolean centered,
                int textPadding,
                Button.OnPress onPress
        ) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.textColor = textColor;
            this.backgroundColor = backgroundColor;
            this.hoveredBackgroundColor = hoveredBackgroundColor;
            this.borderColor = borderColor;
            this.drawBorder = drawBorder;
            this.centered = centered;
            this.textPadding = textPadding;
        }

        @Override
        protected void extractContents(final @NonNull GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
            int x = this.getX();
            int y = this.getY();
            int x2 = x + this.getWidth();
            int y2 = y + this.getHeight();

            int fillColor = this.isHoveredOrFocused() ? this.hoveredBackgroundColor : this.backgroundColor;
            if (fillColor != 0) {
                graphics.fill(x, y, x2, y2, fillColor);
            }

            if (this.drawBorder) {
                graphics.fill(x, y, x2, y + 1, this.borderColor);
                graphics.fill(x, y2 - 1, x2, y2, this.borderColor);
                graphics.fill(x, y, x + 1, y2, this.borderColor);
                graphics.fill(x2 - 1, y, x2, y2, this.borderColor);
            }

            int textWidth = AtlasScreen.this.font.width(this.getMessage());
            int textX = this.centered ? x + (this.getWidth() - textWidth) / 2 : x + this.textPadding;
            int textY = y + (this.getHeight() - AtlasScreen.this.font.lineHeight) / 2 + 1;
            graphics.textWithBackdrop(AtlasScreen.this.font, this.getMessage(), textX, textY, textWidth, this.textColor);
        }
    }

    private record WaypointIconOption(String name, Identifier texture) {}

    private static WaypointIconOption createIconOption(String filename) {
        String key = filename.endsWith(".png") ? filename.substring(0, filename.length() - 4) : filename;
        String label = key.replace('_', ' ');
        String[] words = label.split(" ");
        StringBuilder title = new StringBuilder(label.length());
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!title.isEmpty()) {
                title.append(' ');
            }
            title.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                title.append(word.substring(1));
            }
        }

        return new WaypointIconOption(
                title.toString(),
                Identifier.fromNamespaceAndPath(SimpleAtlas.MOD_ID, "textures/gui/icons/" + key + ".png")
        );
    }

    private static List<WaypointIconOption> createWaypointIconOptions() {
        return WaypointIconCatalog.getAvailableIconKeys().stream()
                .map(key -> createIconOption(key + ".png"))
                .toList();
    }

    private static class WaypointDraft {
        final double worldX;
        final double worldZ;
        String name;
        int iconIndex;
        String dimension;

        WaypointDraft(double worldX, double worldZ, String name, int iconIndex, String dimension) {
            this.worldX = worldX;
            this.worldZ = worldZ;
            this.name = name;
            this.iconIndex = iconIndex;
            this.dimension = dimension;
        }
    }

    private record WorldPoint(double x, double z) {}

    private record WaypointPickerLayout(
            int panelX,
            int panelY,
            int panelX2,
            int panelY2,
            int iconX,
            int iconY,
            int leftArrowX,
            int rightArrowX,
            int arrowY,
            int inputX,
            int inputY,
            int inputX2,
            int inputY2,
            int confirmButtonX,
            int confirmButtonY,
            int confirmButtonX2,
            int confirmButtonY2,
            int cancelButtonX,
            int cancelButtonY,
            int cancelButtonX2,
            int cancelButtonY2
    ) {}

    private record DimensionTileBounds(int minTileX, int minTileY, int width, int height) {
        private static final DimensionTileBounds EMPTY = new DimensionTileBounds(0, 0, 1, 1);
    }

    // ----- Construction -----

    public static AtlasScreen fromPayload(OpenAtlasScreenPayload payload) {
        return new AtlasScreen(
                payload.tiles(),
                payload.atlasMapIds(),
                payload.waypoints(),
                payload.selectedWaypointIconIndex(),
                payload.nextWaypointNumber(),
                payload.playerDimension(),
                payload.selectedScale()
        );
    }

    public AtlasScreen(
            List<AtlasTilePayload> tiles,
            List<Integer> atlasMapIds,
            List<AtlasContents.WaypointData> waypoints,
            int selectedWaypointIconIndex,
            int nextWaypointNumber,
            String playerDimension
    ) {
        this(tiles, atlasMapIds, waypoints, selectedWaypointIconIndex, nextWaypointNumber, playerDimension, -1);
    }

    public AtlasScreen(
            List<AtlasTilePayload> tiles,
            List<Integer> atlasMapIds,
            List<AtlasContents.WaypointData> waypoints,
            int selectedWaypointIconIndex,
            int nextWaypointNumber,
            String playerDimension,
            int selectedScale
    ) {
        super(Component.translatable("screen.simple_atlas.atlas.title"));
        this.tiles = new ArrayList<>(tiles);
        this.atlasMapIds = new ArrayList<>(atlasMapIds);
        this.atlasWaypoints = new ArrayList<>(waypoints);
        this.playerDimension = playerDimension;
        // Build ordered dimension tab list: known dimensions in order, then unknowns alphabetically.
        LinkedHashSet<String> dimsSeen = getStrings(tiles);
        this.dimensionTabs = List.copyOf(dimsSeen);

        // Select the tab for the player's current dimension, or default to first available tab
        this.selectedBookmarkTab = findDimensionTabIndex(playerDimension);

        String initialDim = getSelectedDimension();
        int initScale = -1;
        if (selectedScale >= 0) {
            for (AtlasTilePayload t : this.tiles) {
                if (t.dimension().equals(initialDim) && t.scale() == selectedScale) {
                    initScale = selectedScale;
                    break;
                }
            }
        }
        if (initScale < 0) {
            for (AtlasTilePayload t : this.tiles) {
                if (t.dimension().equals(initialDim)) {
                    initScale = t.scale();
                    break;
                }
            }
        }
        this.activeViewScale = initScale >= 0 ? initScale : 0;

        this.playerIcon = new PlayerAtlasIcon(
                PLAYER_MARKER_TEXTURE,
                PLAYER_MARKER_TEXTURE_SIZE,
                PLAYER_MARKER_TEXTURE_SIZE,
                (int) Math.round(PLAYER_MARKER_RENDER_SIZE * SimpleAtlasConfigManager.getPlayerIconSize()),
                (int) Math.round(PLAYER_MARKER_RENDER_SIZE * SimpleAtlasConfigManager.getPlayerIconSize())
        );
        this.atlasIcons = new ArrayList<>();
        this.atlasIcons.add(this.playerIcon);
        this.waypointIconOptions = createWaypointIconOptions();
        this.selectedWaypointIconIndex = this.waypointIconOptions.isEmpty()
                ? 0
                : Math.floorMod(selectedWaypointIconIndex, this.waypointIconOptions.size());
        this.nextWaypointNumber = Math.max(1, nextWaypointNumber);

        for (AtlasContents.WaypointData waypoint : this.atlasWaypoints) {
            atlasIcons.add(createWaypointIcon(
                    waypoint.worldX(),
                    waypoint.worldZ(),
                    Component.literal(waypoint.name()),
                    waypoint.iconIndex()
            ));
        }
    }

    private static @NonNull LinkedHashSet<String> getStrings(List<AtlasTilePayload> tiles) {
        LinkedHashSet<String> dimsSeen = new LinkedHashSet<>();
        for (String d : DIMENSION_ORDER) {
            for (AtlasTilePayload t : tiles) {
                if (t.dimension().equals(d)) { dimsSeen.add(d); break; }
            }
        }
        TreeSet<String> extras = new TreeSet<>();
        for (AtlasTilePayload t : tiles) {
            if (!DIMENSION_ORDER.contains(t.dimension())) extras.add(t.dimension());
        }
        dimsSeen.addAll(extras);
        return dimsSeen;
    }

    private void updateTileCacheIfNeeded() {
        String dimension = getSelectedDimension();
        if (dimension == null) {
            cachedDimension = null;
            cachedScale = -1;
            cachedVisibleTiles = List.of();
            cachedDimensionBounds = DimensionTileBounds.EMPTY;
            return;
        }
        if (!dimension.equals(cachedDimension) || activeViewScale != cachedScale) {
            cachedDimension = dimension;
            cachedScale = activeViewScale;
            cachedVisibleTiles = tiles.stream()
                    .filter(t -> t.dimension().equals(dimension) && t.scale() == activeViewScale)
                    .toList();
            if (cachedVisibleTiles.isEmpty()) {
                cachedDimensionBounds = DimensionTileBounds.EMPTY;
            } else {
                int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
                int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
                for (AtlasTilePayload tile : cachedVisibleTiles) {
                    minX = Math.min(minX, tile.tileX());
                    maxX = Math.max(maxX, tile.tileX());
                    minY = Math.min(minY, tile.tileY());
                    maxY = Math.max(maxY, tile.tileY());
                }
                int width = Math.max(1, maxX - minX + 1);
                int height = Math.max(1, maxY - minY + 1);
                cachedDimensionBounds = new DimensionTileBounds(minX, minY, width, height);
            }
        }
    }

    private DimensionTileBounds getDimensionTileBounds(String dimension) {
        List<AtlasTilePayload> visible = getVisibleTilesForDimension(dimension);
        if (visible.isEmpty()) {
            return DimensionTileBounds.EMPTY;
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (AtlasTilePayload tile : visible) {
            minX = Math.min(minX, tile.tileX());
            maxX = Math.max(maxX, tile.tileX());
            minY = Math.min(minY, tile.tileY());
            maxY = Math.max(maxY, tile.tileY());
        }
        int width = Math.max(1, maxX - minX + 1);
        int height = Math.max(1, maxY - minY + 1);
        return new DimensionTileBounds(minX, minY, width, height);
    }

    private int localTileX(String dimension, AtlasTilePayload tile) {
        return tile.tileX() - getDimensionTileBounds(dimension).minTileX();
    }

    private int localTileY(String dimension, AtlasTilePayload tile) {
        return tile.tileY() - getDimensionTileBounds(dimension).minTileY();
    }

    private int findDimensionTabIndex(String dimension) {
        for (int i = 0; i < dimensionTabs.size(); i++) {
            if (dimensionTabs.get(i).equals(dimension)) {
                return i;
            }
        }
        // Default to first tab if dimension not found
        return 0;
    }

    @Override
    protected void init() {
        super.init();

        rebuildScaleWidgets();

        centerOnPlayerPosition();

        // Play sound when atlas is opened
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
        }

        // Restore overlay widget positions if a modal UI was open (e.g. on resize)
        if (waypointDraft != null || isContextMenuOpen()) {
            rebuildOverlayWidgets();
        }
    }

    private void stepScale(int delta) {
        List<Integer> scales = getAvailableScales();
        if (scales.size() <= 1) {
            return;
        }
        int currentIdx = scales.indexOf(activeViewScale);
        if (currentIdx < 0) {
            currentIdx = 0;
        }
        int newIdx = currentIdx + delta;
        if (newIdx >= 0 && newIdx < scales.size()) {
            changeScalePreservingView(scales.get(newIdx));
        }
    }

    private void cycleScale() {
        List<Integer> scales = getAvailableScales();
        if (scales.size() <= 1) {
            return;
        }
        int currentIdx = scales.indexOf(activeViewScale);
        int nextIdx = (currentIdx + 1) % scales.size();
        changeScalePreservingView(scales.get(nextIdx));
    }

    private void changeScalePreservingView(int newScale) {
        if (newScale == activeViewScale) {
            return;
        }

        String dimension = getSelectedDimension();
        AtlasViewport viewport = getAtlasViewport();
        float oldScaledTileSize = TILE_SIZE * zoom;

        List<AtlasTilePayload> oldTiles = getVisibleTilesForDimension(dimension);
        List<AtlasTilePayload> newTiles = tiles.stream()
                .filter(t -> t.dimension().equals(dimension) && t.scale() == newScale)
                .toList();

        if (oldTiles.isEmpty() || newTiles.isEmpty()) {
            activeViewScale = newScale;
            rebuildScaleWidgets();
            centerOnSelectedDimensionAtCurrentZoom();
            return;
        }

        double screenCenterX = viewport.contentX() + viewport.contentWidth() / 2.0;
        double screenCenterY = viewport.contentY() + viewport.contentHeight() / 2.0;

        // 1. Calculate world point currently at the center of the viewport under old scale and old zoom
        AtlasTilePayload oldTile = oldTiles.getFirst();
        int oldScaleFactor = 1 << oldTile.scale();
        float oldOriginX = getMapOriginX(viewport, oldScaledTileSize, dimension);
        float oldOriginY = getMapOriginY(viewport, oldScaledTileSize, dimension);

        float oldTileScreenX = oldOriginX + (float) panX + localTileX(dimension, oldTile) * oldScaledTileSize;
        float oldTileScreenY = oldOriginY + (float) panY + localTileY(dimension, oldTile) * oldScaledTileSize;

        double oldTileWorldMinX = oldTile.centerX() - 64.0 * oldScaleFactor;
        double oldTileWorldMinZ = oldTile.centerZ() - 64.0 * oldScaleFactor;

        double centerWorldX = oldTileWorldMinX + ((screenCenterX - oldTileScreenX) / (oldScaledTileSize / 128.0)) * oldScaleFactor;
        double centerWorldZ = oldTileWorldMinZ + ((screenCenterY - oldTileScreenY) / (oldScaledTileSize / 128.0)) * oldScaleFactor;

        // 2. Set the new scale
        activeViewScale = newScale;
        rebuildScaleWidgets();

        // 3. Compensate zoom proportionally to prevent digital zoom in/out:
        // Keeps the exact same world area and physical scale on screen!
        AtlasTilePayload newTile = newTiles.getFirst();
        int newScaleFactor = 1 << newTile.scale();
        this.zoom = Math.clamp(this.zoom * ((float) newScaleFactor / (float) oldScaleFactor), MIN_ZOOM, MAX_ZOOM);

        float newScaledTileSize = TILE_SIZE * zoom;
        float newOriginX = getMapOriginX(viewport, newScaledTileSize, dimension);
        float newOriginY = getMapOriginY(viewport, newScaledTileSize, dimension);

        double newTileWorldMinX = newTile.centerX() - 64.0 * newScaleFactor;
        double newTileWorldMinZ = newTile.centerZ() - 64.0 * newScaleFactor;

        double newTileScreenX = screenCenterX - ((centerWorldX - newTileWorldMinX) / newScaleFactor) * (newScaledTileSize / 128.0);
        double newTileScreenY = screenCenterY - ((centerWorldZ - newTileWorldMinZ) / newScaleFactor) * (newScaledTileSize / 128.0);

        this.panX = newTileScreenX - newOriginX - localTileX(dimension, newTile) * newScaledTileSize;
        this.panY = newTileScreenY - newOriginY - localTileY(dimension, newTile) * newScaledTileSize;
    }

    private void rebuildScaleWidgets() {
        if (prevScaleButton != null) {
            this.removeWidget(prevScaleButton);
            prevScaleButton = null;
        }
        if (scaleToggleButton != null) {
            this.removeWidget(scaleToggleButton);
            scaleToggleButton = null;
        }
        if (nextScaleButton != null) {
            this.removeWidget(nextScaleButton);
            nextScaleButton = null;
        }

        List<Integer> availableScales = getAvailableScales();
        if (availableScales.size() > 1) {
            AtlasViewport viewport = getAtlasViewport();
            int btnY = (int) Math.floor(viewport.contentY() + 6);
            int startX = (int) Math.floor(viewport.contentX() + 6);

            int arrowBtnWidth = 16;
            int labelBtnWidth = 64;
            int btnHeight = 16;

            int currentIdx = availableScales.indexOf(activeViewScale);
            if (currentIdx < 0) {
                currentIdx = 0;
                activeViewScale = availableScales.getFirst();
            }

            final int idx = currentIdx;
            boolean canStepDown = idx > 0;
            boolean canStepUp = idx < availableScales.size() - 1;

            prevScaleButton = this.addRenderableWidget(new AtlasTextButton(
                    startX,
                    btnY,
                    arrowBtnWidth,
                    btnHeight,
                    Component.literal("<"),
                    canStepDown ? 0xFFFFFFFF : 0xFF707070,
                    0xD0181818,
                    canStepDown ? 0xE0383838 : 0xD0181818,
                    0xFF8A8A8A,
                    true,
                    true,
                    0,
                    _ -> stepScale(-1)
            ));

            scaleToggleButton = this.addRenderableWidget(new AtlasTextButton(
                    startX + arrowBtnWidth + 2,
                    btnY,
                    labelBtnWidth,
                    btnHeight,
                    getScaleButtonMessage(),
                    0xFFFFFFFF,
                    0xD0181818,
                    0xE0383838,
                    0xFF8A8A8A,
                    true,
                    true,
                    0,
                    _ -> cycleScale()
            ));

            nextScaleButton = this.addRenderableWidget(new AtlasTextButton(
                    startX + arrowBtnWidth + 2 + labelBtnWidth + 2,
                    btnY,
                    arrowBtnWidth,
                    btnHeight,
                    Component.literal(">"),
                    canStepUp ? 0xFFFFFFFF : 0xFF707070,
                    0xD0181818,
                    canStepUp ? 0xE0383838 : 0xD0181818,
                    0xFF8A8A8A,
                    true,
                    true,
                    0,
                    _ -> stepScale(1)
            ));
        }
    }

    private Component getScaleButtonMessage() {
        int ratio = 1 << activeViewScale;
        return Component.translatable("gui.simple_atlas.scale_ratio", ratio);
    }

    private List<Integer> getAvailableScales() {
        String dimension = getSelectedDimension();
        return tiles.stream()
                .filter(t -> t.dimension().equals(dimension))
                .map(AtlasTilePayload::scale)
                .distinct()
                .sorted()
                .toList();
    }

    private boolean hasMultipleScales() {
        return getAvailableScales().size() > 1;
    }

    private record AtlasViewport(float x, float y, float width, float height, float contentX, float contentY, float contentWidth, float contentHeight) {}

    private record MapInteractionContext(
            AtlasViewport viewport,
            float scaledTileSize,
            float mapOriginX,
            float mapOriginY
    ) {}

    // ----- Viewport + coordinate math -----

    private AtlasViewport getAtlasViewport() {
        float availableWidth = Math.max(32.0f, this.width - BOOK_SCREEN_MARGIN * 2.0f);
        float availableHeight = Math.max(32.0f, this.height - BOOK_SCREEN_MARGIN * 2.0f);
        float fitScaleX = availableWidth / ATLAS_BACKGROUND_TEXTURE_WIDTH;
        float fitScaleY = availableHeight / ATLAS_BACKGROUND_TEXTURE_HEIGHT;
        float scale = Math.clamp(fitScaleX, 0.25f, Math.min(BOOK_TARGET_UI_SCALE, fitScaleY));

        float width = ATLAS_BACKGROUND_TEXTURE_WIDTH * scale;
        float height = ATLAS_BACKGROUND_TEXTURE_HEIGHT * scale;
        float x = (this.width - width) / 2.0f;
        float y = (this.height - height) / 2.0f;

        float contentX = x + PAGE_AREA_X * scale;
        float contentY = y + PAGE_AREA_Y * scale;
        float contentWidth = PAGE_AREA_WIDTH * scale;
        float contentHeight = PAGE_AREA_HEIGHT * scale;

        return new AtlasViewport(x, y, width, height, contentX, contentY, contentWidth, contentHeight);
    }

    private float getMapOriginX(AtlasViewport viewport, float scaledTileSize, String dimension) {
        float atlasPixelWidth = getDimensionTileBounds(dimension).width() * scaledTileSize;
        return viewport.contentX() + (viewport.contentWidth() - atlasPixelWidth) / 2.0f;
    }

    private float getMapOriginY(AtlasViewport viewport, float scaledTileSize, String dimension) {
        float atlasPixelHeight = getDimensionTileBounds(dimension).height() * scaledTileSize;
        return viewport.contentY() + (viewport.contentHeight() - atlasPixelHeight) / 2.0f;
    }

    private MapInteractionContext buildMapInteractionContext() {
        AtlasViewport viewport = getAtlasViewport();
        float scaledTileSize = TILE_SIZE * zoom;
        String selectedDimension = getSelectedDimension();
        float originX = getMapOriginX(viewport, scaledTileSize, selectedDimension);
        float originY = getMapOriginY(viewport, scaledTileSize, selectedDimension);
        float mapOriginX = (float) (originX + panX);
        float mapOriginY = (float) (originY + panY);
        return new MapInteractionContext(viewport, scaledTileSize, mapOriginX, mapOriginY);
    }

    // ----- Camera + world/map transforms -----

    public void resetPerspective() {
        this.zoom = 1.0f;
        centerOnPlayerPosition();
    }

    private void centerOnPlayerPosition() {
        centerOnIcon(this.playerIcon, getVisibleTilesForDimension(playerDimension), playerDimension);
    }

    private void centerOnSelectedDimensionAtCurrentZoom() {
        String selectedDimension = getSelectedDimension();
        List<AtlasTilePayload> visibleTiles = getVisibleTilesForDimension(selectedDimension);
        if (visibleTiles.isEmpty()) {
            return;
        }

        if (selectedDimension.equals(playerDimension) && centerOnIcon(this.playerIcon, visibleTiles, selectedDimension)) {
            return;
        }

        centerOnTile(visibleTiles.getFirst(), selectedDimension);
    }

    private boolean centerOnIcon(AtlasIcon icon, List<AtlasTilePayload> visibleTiles, String dimension) {
        float scaledTileSize = TILE_SIZE * zoom;
        AtlasViewport viewport = getAtlasViewport();
        Minecraft minecraft = Minecraft.getInstance();

        // Start from neutral pan so anchor is computed in base atlas position
        this.panX = 0;
        this.panY = 0;

        float originX = getMapOriginX(viewport, scaledTileSize, dimension);
        float originY = getMapOriginY(viewport, scaledTileSize, dimension);

        AtlasIcon.Anchor anchor = icon.resolveAnchor(minecraft, visibleTiles, originX, originY, scaledTileSize);
        if (anchor == null) {
            return false;
        }

        this.panX = viewport.contentX() + viewport.contentWidth() / 2.0 - anchor.screenX();
        this.panY = viewport.contentY() + viewport.contentHeight() / 2.0 - anchor.screenY();
        return true;
    }

    private void centerOnTile(AtlasTilePayload tile, String dimension) {
        float scaledTileSize = TILE_SIZE * zoom;
        AtlasViewport viewport = getAtlasViewport();

        this.panX = 0;
        this.panY = 0;

        float originX = getMapOriginX(viewport, scaledTileSize, dimension);
        float originY = getMapOriginY(viewport, scaledTileSize, dimension);
        float tileCenterX = originX + (localTileX(dimension, tile) + 0.5f) * scaledTileSize;
        float tileCenterY = originY + (localTileY(dimension, tile) + 0.5f) * scaledTileSize;

        this.panX = viewport.contentX() + viewport.contentWidth() / 2.0 - tileCenterX;
        this.panY = viewport.contentY() + viewport.contentHeight() / 2.0 - tileCenterY;
    }

    private AtlasIcon createWaypointIcon(double worldX, double worldZ, Component title, int iconIndex) {
        int resolvedIconIndex = waypointIconOptions.isEmpty() ? 0 : Math.floorMod(iconIndex, waypointIconOptions.size());
        WaypointIconOption option = waypointIconOptions.get(resolvedIconIndex);
        int scaledWaypointRenderSize = (int) Math.round(WAYPOINT_RENDER_SIZE * SimpleAtlasConfigManager.getWaypointIconSize());
        return new StaticAtlasIcon(
                option.texture(),
                WAYPOINT_TEXTURE_SIZE,
                WAYPOINT_TEXTURE_SIZE,
                scaledWaypointRenderSize,
                scaledWaypointRenderSize,
                worldX,
                worldZ,
                title
        );
    }

    private Integer getAtlasScaleFactor() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || tiles.isEmpty()) {
            return null;
        }

        List<AtlasTilePayload> visible = getVisibleTilesForDimension(getSelectedDimension());
        if (visible.isEmpty()) {
            visible = tiles;
        }

        return 1 << visible.getFirst().scale();
    }

    private WorldPoint screenToWorldPoint(
            double mouseX,
            double mouseY,
            float mapOriginX,
            float mapOriginY,
            float scaledTileSize,
            List<AtlasTilePayload> visibleTiles,
            String dimension
    ) {
        Integer scaleFactor = getAtlasScaleFactor();
        if (scaleFactor == null) {
            return null;
        }

        for (AtlasTilePayload tile : visibleTiles) {
            float tileScreenX = mapOriginX + localTileX(dimension, tile) * scaledTileSize;
            float tileScreenY = mapOriginY + localTileY(dimension, tile) * scaledTileSize;

            if (mouseX < tileScreenX || mouseX >= tileScreenX + scaledTileSize || mouseY < tileScreenY || mouseY >= tileScreenY + scaledTileSize) {
                continue;
            }

            double localPixelX = (mouseX - tileScreenX) / (scaledTileSize / 128.0f);
            double localPixelY = (mouseY - tileScreenY) / (scaledTileSize / 128.0f);

            double tileWorldMinX = tile.centerX() - 64.0 * scaleFactor;
            double tileWorldMinZ = tile.centerZ() - 64.0 * scaleFactor;

            double worldX = tileWorldMinX + localPixelX * scaleFactor;
            double worldZ = tileWorldMinZ + localPixelY * scaleFactor;
            return new WorldPoint(worldX, worldZ);
        }

        return null;
    }

    private void cycleSelectedWaypointIcon(int step) {
        if (waypointIconOptions.isEmpty()) {
            return;
        }

        int size = waypointIconOptions.size();
        selectedWaypointIconIndex = Math.floorMod(selectedWaypointIconIndex + step, size);
        if (waypointDraft != null) {
            waypointDraft.iconIndex = selectedWaypointIconIndex;
        }
    }

    // ----- Networking + context menu state -----

    private void persistWaypointState() {
        ClientPlayNetworking.send(new SaveAtlasWaypointsPayload(
                atlasMapIds,
                atlasWaypoints,
                selectedWaypointIconIndex,
                nextWaypointNumber
        ));
        waypointsDirty = false;
    }

    private boolean isContextMenuOpen() {
        return contextMenuWaypointIndex >= 0 || contextMenuWorldPoint != null;
    }

    private void closeContextMenu() {
        contextMenuWaypointIndex = -1;
        contextMenuWorldPoint = null;
        contextMenuMapId = -1;
        rebuildOverlayWidgets();
    }

    private void positionContextMenu(double mouseX, double mouseY, int rowCount) {
        int menuHeight = WAYPOINT_CONTEXT_MENU_ROW_HEIGHT * rowCount;
        contextMenuX = Mth.clamp((int) mouseX, 4, Math.max(4, this.width - WAYPOINT_CONTEXT_MENU_WIDTH - 4));
        contextMenuY = Mth.clamp((int) mouseY, 4, Math.max(4, this.height - menuHeight - 4));
    }

    private boolean canUseTeleportCommand() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    private int getWaypointContextMenuRowCount() {
        return canUseTeleportCommand() ? WAYPOINT_CONTEXT_MENU_WAYPOINT_ROWS : WAYPOINT_CONTEXT_MENU_WAYPOINT_ROWS - 1;
    }

    private int getWorldPointContextMenuRowCount() {
        return canUseTeleportCommand() ? WAYPOINT_CONTEXT_MENU_MAP_ROWS : WAYPOINT_CONTEXT_MENU_MAP_ROWS - 1;
    }

    private int getContextMenuRowCount() {
        if (contextMenuWaypointIndex >= 0) {
            return getWaypointContextMenuRowCount();
        }
        return contextMenuWorldPoint != null ? getWorldPointContextMenuRowCount() : 0;
    }

    private void teleportToLocation(double worldX, double worldZ, String dimension) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !canUseTeleportCommand()) {
            return;
        }

        int blockX = Mth.floor(worldX);
        int blockZ = Mth.floor(worldZ);
        // Use /execute in <dimension> run tp to teleport to a specific dimension
        minecraft.player.connection.sendCommand("execute in " + dimension + " run tp " + blockX + " ~ " + blockZ);
        this.onClose();
    }

    private void copyCoordinatesToClipboard(double worldX, double worldZ) {
        int blockX = Mth.floor(worldX);
        int blockZ = Mth.floor(worldZ);
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.keyboardHandler.setClipboard(blockX + ", " + blockZ);
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.translatable("message.simple_atlas.coordinates_copied", blockX, blockZ));
        }
    }

    private void openWaypointContextMenu(int waypointIndex, double mouseX, double mouseY) {
        contextMenuWaypointIndex = waypointIndex;
        contextMenuWorldPoint = null;
        contextMenuMapId = -1;
        positionContextMenu(mouseX, mouseY, getWaypointContextMenuRowCount());
        rebuildOverlayWidgets();
    }

    private boolean isWithinAtlasContent(AtlasViewport viewport, double x, double y) {
        return x >= viewport.contentX() && x <= viewport.contentX() + viewport.contentWidth()
                && y >= viewport.contentY() && y <= viewport.contentY() + viewport.contentHeight();
    }

    private void openNewWaypointContextMenu(WorldPoint worldPoint, int mapId, double mouseX, double mouseY) {
        contextMenuWaypointIndex = -1;
        contextMenuWorldPoint = worldPoint;
        contextMenuMapId = mapId;
        positionContextMenu(mouseX, mouseY, getWorldPointContextMenuRowCount());
        rebuildOverlayWidgets();
    }

    private int findMapIdAtScreenPoint(
            double mouseX,
            double mouseY,
            float mapOriginX,
            float mapOriginY,
            float scaledTileSize,
            List<AtlasTilePayload> visibleTiles,
            String dimension
    ) {
        for (AtlasTilePayload tile : visibleTiles) {
            float tileScreenX = mapOriginX + localTileX(dimension, tile) * scaledTileSize;
            float tileScreenY = mapOriginY + localTileY(dimension, tile) * scaledTileSize;
            if (mouseX >= tileScreenX && mouseX < tileScreenX + scaledTileSize
                    && mouseY >= tileScreenY && mouseY < tileScreenY + scaledTileSize) {
                return tile.mapId();
            }
        }
        return -1;
    }

    // ----- Map click routing / waypoint hit-testing -----

    private boolean handleMapRightClick(MouseButtonEvent event, MapInteractionContext context) {
        if (!isWithinAtlasContent(context.viewport(), event.x(), event.y())) {
            return false;
        }

        String selectedDimension = getSelectedDimension();
        List<AtlasTilePayload> visibleTiles = getVisibleTilesForDimension(selectedDimension);

        int hoveredWaypointIndex = findHoveredWaypointIndex(
                Minecraft.getInstance(),
                context.mapOriginX(),
                context.mapOriginY(),
                context.scaledTileSize(),
                (int) event.x(),
                (int) event.y(),
                selectedDimension,
                visibleTiles
        );

        if (hoveredWaypointIndex >= 0) {
            openWaypointContextMenu(hoveredWaypointIndex, event.x(), event.y());
            playSelectionSound();
            leftDragging = false;
            return true;
        }

        WorldPoint worldPoint = screenToWorldPoint(
                event.x(),
                event.y(),
                context.mapOriginX(),
                context.mapOriginY(),
                context.scaledTileSize(),
                visibleTiles,
                selectedDimension
        );
        if (worldPoint != null) {
            int mapId = findMapIdAtScreenPoint(
                    event.x(),
                    event.y(),
                    context.mapOriginX(),
                    context.mapOriginY(),
                    context.scaledTileSize(),
                    visibleTiles,
                    selectedDimension
            );
            if (mapId < 0) {
                return false;
            }

            openNewWaypointContextMenu(worldPoint, mapId, event.x(), event.y());
            playSelectionSound();
            leftDragging = false;
            return true;
        }

        return false;
    }

    private int findHoveredWaypointIndex(
            Minecraft minecraft,
            float mapOriginX,
            float mapOriginY,
            float scaledTileSize,
            int mouseX,
            int mouseY,
            String selectedDimension,
            List<AtlasTilePayload> visibleTiles
    ) {
        AtlasIcon.Anchor playerAnchor = selectedDimension.equals(playerDimension)
                ? resolveHoveredAnchor(playerIcon, minecraft, visibleTiles, mapOriginX, mapOriginY, scaledTileSize, mouseX, mouseY)
                : null;
        if (playerAnchor != null) {
            return -1;
        }

        for (int i = atlasWaypoints.size() - 1; i >= 0; i--) {
            int iconListIndex = i + 1;
            if (iconListIndex >= atlasIcons.size()) {
                continue;
            }

            AtlasContents.WaypointData waypoint = atlasWaypoints.get(i);
            if (!waypoint.dimension().equals(selectedDimension)) {
                continue;
            }

            AtlasIcon icon = atlasIcons.get(iconListIndex);
            AtlasIcon.Anchor anchor = resolveHoveredAnchor(icon, minecraft, visibleTiles, mapOriginX, mapOriginY, scaledTileSize, mouseX, mouseY);
            if (anchor != null) {
                return i;
            }
        }

        return -1;
    }

    private AtlasContents.WaypointData getWaypoint(int waypointIndex) {
        if (waypointIndex < 0 || waypointIndex >= atlasWaypoints.size()) {
            return null;
        }
        return atlasWaypoints.get(waypointIndex);
    }

    // ----- Waypoint editing + locator-bar integration -----

    private @Nullable AtlasIcon getWaypointAtlasIcon(int waypointIndex) {
        int iconListIndex = waypointIndex + 1;
        if (waypointIndex < 0 || iconListIndex >= atlasIcons.size()) {
            return null;
        }
        return atlasIcons.get(iconListIndex);
    }

    private void beginEditWaypoint(int waypointIndex) {
        AtlasContents.WaypointData waypoint = getWaypoint(waypointIndex);
        if (waypoint == null) {
            return;
        }
        int iconIndex = waypointIconOptions.isEmpty() ? 0 : Math.floorMod(waypoint.iconIndex(), waypointIconOptions.size());
        this.selectedWaypointIconIndex = iconIndex;
        this.waypointDraft = new WaypointDraft(waypoint.worldX(), waypoint.worldZ(), waypoint.name(), iconIndex, waypoint.dimension());
        this.editingWaypointIndex = waypointIndex;
        rebuildOverlayWidgets();
    }

    private void beginNewWaypoint(WorldPoint worldPoint) {
        if (SimpleAtlasConfigManager.isBannerWaypointsOnly()) {
            return; // Waypoints can only be created via banners
        }
        String defaultName = "Waypoint " + nextWaypointNumber;
        String selectedDimension = (selectedBookmarkTab < dimensionTabs.size())
                ? dimensionTabs.get(selectedBookmarkTab)
                : (dimensionTabs.isEmpty() ? "minecraft:overworld" : dimensionTabs.getFirst());
        this.waypointDraft = new WaypointDraft(worldPoint.x(), worldPoint.z(), defaultName, selectedWaypointIconIndex, selectedDimension);
        this.editingWaypointIndex = -1;
        rebuildOverlayWidgets();
    }

    private void pinWaypointToLocatorBar(int waypointIndex) {
        AtlasContents.WaypointData waypoint = getWaypoint(waypointIndex);
        if (waypoint == null) {
            return;
        }
        ClientPlayNetworking.send(new NavigateToWaypointPayload(
                waypoint.worldX(),
                waypoint.worldZ(),
                waypoint.iconIndex(),
                waypoint.dimension()
        ));
    }

    private void unpinWaypointFromLocatorBar(int waypointIndex) {
        AtlasContents.WaypointData waypoint = getWaypoint(waypointIndex);
        if (waypoint == null) {
            return;
        }
        unpinWaypointFromLocatorBar(waypoint);
    }

    private void unpinWaypointFromLocatorBar(AtlasContents.WaypointData waypoint) {
        ClientPlayNetworking.send(new UnpinWaypointPayload(
                waypoint.worldX(),
                waypoint.worldZ(),
                waypoint.dimension()
        ));
    }

    private boolean isWaypointPinnedToLocatorBar(int waypointIndex) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        AtlasContents.WaypointData waypoint = getWaypoint(waypointIndex);
        if (waypoint == null) {
            return false;
        }
        UUID navigationId = WaypointIconCatalog.navigationWaypointId(waypoint.dimension(), waypoint.worldX(), waypoint.worldZ());
        final boolean[] matched = {false};
        minecraft.player.connection.getWaypointManager().forEachWaypoint(minecraft.player, trackedWaypoint -> {
            Either<UUID, String> id = trackedWaypoint.id();
            if (id.left().isPresent() && id.left().get().equals(navigationId)) {
                matched[0] = true;
            }
        });
        return matched[0];
    }

    private Set<UUID> getPinnedWaypointIds(Minecraft minecraft) {
        if (minecraft.player == null) {
            return Set.of();
        }

        Set<UUID> pinnedIds = new HashSet<>();
        minecraft.player.connection.getWaypointManager().forEachWaypoint(minecraft.player, trackedWaypoint -> {
            Either<UUID, String> id = trackedWaypoint.id();
            id.left().ifPresent(pinnedIds::add);
        });
        return pinnedIds;
    }

    private void renderPinnedWaypointMarkers(
            GuiGraphicsExtractor graphics,
            Minecraft minecraft,
            float mapOriginX,
            float mapOriginY,
            float scaledTileSize
    ) {
        Set<UUID> pinnedIds = getPinnedWaypointIds(minecraft);
        if (pinnedIds.isEmpty()) {
            return;
        }

        for (int i = 0; i < atlasWaypoints.size(); i++) {
            int iconListIndex = i + 1;
            if (iconListIndex >= atlasIcons.size()) {
                continue;
            }

            AtlasContents.WaypointData waypoint = atlasWaypoints.get(i);
            UUID waypointId = WaypointIconCatalog.navigationWaypointId(waypoint.dimension(), waypoint.worldX(), waypoint.worldZ());
            if (!pinnedIds.contains(waypointId)) {
                continue;
            }

            AtlasIcon icon = getWaypointAtlasIcon(i);
            if (icon == null) {
                continue;
            }
            AtlasIcon.Anchor anchor = icon.resolveAnchor(minecraft, tiles, mapOriginX, mapOriginY, scaledTileSize);
            if (anchor == null) {
                continue;
            }

            float markerX = anchor.screenX() + WAYPOINT_RENDER_SIZE / 2.0f + PINNED_WAYPOINT_MARKER_OFFSET_X;
            float markerY = anchor.screenY() - icon.renderHeight() / 2.0f + PINNED_WAYPOINT_MARKER_OFFSET_Y;

            graphics.pose().pushMatrix();
            graphics.pose().translate(markerX, markerY);
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    PINNED_WAYPOINT_MARKER_TEXTURE,
                    0,
                    0,
                    0.0f,
                    0.0f,
                    PINNED_WAYPOINT_MARKER_RENDER_SIZE,
                    PINNED_WAYPOINT_MARKER_RENDER_SIZE,
                    PINNED_WAYPOINT_MARKER_TEXTURE_SIZE,
                    PINNED_WAYPOINT_MARKER_TEXTURE_SIZE,
                    PINNED_WAYPOINT_MARKER_TEXTURE_SIZE,
                    PINNED_WAYPOINT_MARKER_TEXTURE_SIZE
            );
            graphics.pose().popMatrix();
        }
    }

    private void deleteWaypoint(int waypointIndex) {
        if (waypointIndex < 0 || waypointIndex >= atlasWaypoints.size()) {
            return;
        }

        AtlasContents.WaypointData removedWaypoint = atlasWaypoints.get(waypointIndex);
        if (isWaypointPinnedToLocatorBar(waypointIndex)) {
            unpinWaypointFromLocatorBar(removedWaypoint);
        }

        atlasWaypoints.remove(waypointIndex);
        atlasIcons.remove(waypointIndex + 1);
        waypointsDirty = true;
        persistWaypointState();
    }

    // ----- Atlas + waypoint state helpers -----

    private void clearWaypointDraft() {
        waypointDraft = null;
        editingWaypointIndex = -1;
        rebuildOverlayWidgets();
    }

    // ----- Overlay widgets -----

    private void initWaypointEditBox(String initialValue) {
        AtlasViewport viewport = getAtlasViewport();
        WaypointPickerLayout layout = getWaypointPickerLayout(viewport);
        int inputWidth = layout.inputX2() - layout.inputX();
        int inputHeight = layout.inputY2() - layout.inputY();
        waypointNameEditBox = new EditBox(
                this.font,
                layout.inputX(), layout.inputY(),
                inputWidth, inputHeight,
                Component.translatable("gui.simple_atlas.waypoint_name")
        );
        waypointNameEditBox.setMaxLength(WAYPOINT_NAME_MAX_LENGTH);
        waypointNameEditBox.setValue(initialValue);
        waypointNameEditBox.moveCursorToEnd(false);
        waypointNameEditBox.setCanLoseFocus(false);
        waypointNameEditBox.setFocused(true);
        waypointNameEditBox.setResponder(value -> {
            if (waypointDraft != null) {
                waypointDraft.name = value;
            }
        });
        this.addRenderableWidget(waypointNameEditBox);
    }

    private void clearOverlayWidgets() {
        if (waypointNameEditBox != null) {
            waypointNameEditBox.setFocused(false);
            this.removeWidget(waypointNameEditBox);
            waypointNameEditBox = null;
        }

        if (previousWaypointIconButton != null) {
            this.removeWidget(previousWaypointIconButton);
            previousWaypointIconButton = null;
        }

        if (nextWaypointIconButton != null) {
            this.removeWidget(nextWaypointIconButton);
            nextWaypointIconButton = null;
        }

        if (confirmWaypointButton != null) {
            this.removeWidget(confirmWaypointButton);
            confirmWaypointButton = null;
        }

        if (cancelWaypointButton != null) {
            this.removeWidget(cancelWaypointButton);
            cancelWaypointButton = null;
        }

        for (Button contextMenuButton : contextMenuButtons) {
            this.removeWidget(contextMenuButton);
        }
        contextMenuButtons.clear();
    }

    private void rebuildOverlayWidgets() {
        clearOverlayWidgets();

        if (scaleToggleButton != null) {
            scaleToggleButton.visible = (waypointDraft == null && !isContextMenuOpen());
        }

        if (waypointDraft != null && !waypointIconOptions.isEmpty()) {
            buildWaypointDraftWidgets();
        } else if (isContextMenuOpen()) {
            buildContextMenuWidgets();
        }
    }

    private Button createDraftControlButton(int x, int y, int width, int height, Component label, Button.OnPress onPress) {
        return this.addRenderableWidget(new AtlasTextButton(
                x,
                y,
                width,
                height,
                label,
                0xFFFFFFFF,
                0x70000000,
                0x70404040,
                0xFF505050,
                true,
                true,
                0,
                onPress
        ));
    }

    private void buildWaypointDraftWidgets() {
        WaypointPickerLayout layout = getWaypointPickerLayout(getAtlasViewport());
        initWaypointEditBox(waypointDraft != null ? waypointDraft.name : "");

        previousWaypointIconButton = createDraftControlButton(
                layout.leftArrowX(),
                layout.arrowY(),
                WAYPOINT_PICKER_ARROW_SIZE,
                WAYPOINT_PICKER_ARROW_SIZE,
                Component.literal("<"),
                ignored -> cycleSelectedWaypointIcon(-1)
        );
        nextWaypointIconButton = createDraftControlButton(
                layout.rightArrowX(),
                layout.arrowY(),
                WAYPOINT_PICKER_ARROW_SIZE,
                WAYPOINT_PICKER_ARROW_SIZE,
                Component.literal(">"),
                ignored -> cycleSelectedWaypointIcon(1)
        );
        cancelWaypointButton = createDraftControlButton(
                layout.cancelButtonX(),
                layout.cancelButtonY(),
                layout.cancelButtonX2() - layout.cancelButtonX(),
                layout.cancelButtonY2() - layout.cancelButtonY(),
                Component.translatable("gui.simple_atlas.cancel"),
                ignored -> clearWaypointDraft()
        );
        confirmWaypointButton = createDraftControlButton(
                layout.confirmButtonX(),
                layout.confirmButtonY(),
                layout.confirmButtonX2() - layout.confirmButtonX(),
                layout.confirmButtonY2() - layout.confirmButtonY(),
                Component.translatable("gui.simple_atlas.confirm"),
                ignored -> commitWaypointDraft()
        );

        if (waypointNameEditBox != null) {
            this.setInitialFocus(waypointNameEditBox);
        }
    }

    private void addContextMenuButton(int index, Component label, int textColor, Runnable action) {
        int y = contextMenuY + index * WAYPOINT_CONTEXT_MENU_ROW_HEIGHT;
        Button button = this.addRenderableWidget(new AtlasTextButton(
                contextMenuX,
                y,
                WAYPOINT_CONTEXT_MENU_WIDTH,
                WAYPOINT_CONTEXT_MENU_ROW_HEIGHT,
                label,
                textColor,
                0,
                0x50808080,
                0,
                false,
                false,
                6,
                ignored -> action.run()
        ));
        contextMenuButtons.add(button);
    }

    private void addWaypointContextMenuButton(int optionIndex, Component label, int textColor, int waypointIndex) {
        addContextMenuButton(optionIndex, label, textColor, () -> {
            closeContextMenu();
            handleWaypointContextMenuOption(optionIndex, waypointIndex);
        });
    }

    private void addWorldContextMenuButton(int optionIndex, Component label, int textColor, WorldPoint worldPoint) {
        addContextMenuButton(optionIndex, label, textColor, () -> {
            int mapIdAtClick = contextMenuMapId;
            closeContextMenu();
            handleWorldPointContextMenuOption(optionIndex, worldPoint, mapIdAtClick);
        });
    }

    private void buildContextMenuWidgets() {
        if (contextMenuWaypointIndex >= 0) {
            int waypointIndex = contextMenuWaypointIndex;
            boolean pinnedThisWaypoint = isWaypointPinnedToLocatorBar(waypointIndex);
            int optionIndex = 0;
            addWaypointContextMenuButton(0, Component.translatable(pinnedThisWaypoint ? "menu.simple_atlas.waypoint.stop_locating" : "menu.simple_atlas.waypoint.locate"), pinnedThisWaypoint ? 0xFFFFB366 : 0xFF8FE0FF, waypointIndex);
            optionIndex++;
            if (canUseTeleportCommand()) {
                addWaypointContextMenuButton(optionIndex++, Component.translatable("menu.simple_atlas.teleport"), 0xFFFF5EFF, waypointIndex);
            }
            addWaypointContextMenuButton(optionIndex++, Component.translatable("menu.simple_atlas.copy_coordinates"), 0xFFFFFFFF, waypointIndex);
            addWaypointContextMenuButton(optionIndex++, Component.translatable("menu.simple_atlas.waypoint.edit"), 0xFFFFFFFF, waypointIndex);
            addWaypointContextMenuButton(optionIndex, Component.translatable("menu.simple_atlas.waypoint.delete"), 0xFFFF8080, waypointIndex);
            return;
        }

        if (contextMenuWorldPoint != null) {
            WorldPoint worldPoint = contextMenuWorldPoint;
            int optionIndex = 0;
            if (canUseTeleportCommand()) {
                addWorldContextMenuButton(optionIndex++, Component.translatable("menu.simple_atlas.teleport"), 0xFFFF5EFF, worldPoint);
            }
            if (!SimpleAtlasConfigManager.isBannerWaypointsOnly()) {
                addWorldContextMenuButton(optionIndex++, Component.translatable("menu.simple_atlas.map.new_waypoint"), 0xFFFFFFFF, worldPoint);
            }
            addWorldContextMenuButton(optionIndex++, Component.translatable("menu.simple_atlas.copy_coordinates"), 0xFFFFFFFF, worldPoint);
            addWorldContextMenuButton(optionIndex, Component.translatable("menu.simple_atlas.map.remove"), 0xFFFF8080, worldPoint);
        }
    }

    private void playSelectionSound() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }

    private void playBookmarkTabSound() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
        }
    }

    private void playMapRemovalSound() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.SHEEP_SHEAR, 1.0f));
        }
    }

    private void requestAtlasMapRemoval(int removedMapId) {
        if (removedMapId < 0) {
            return;
        }

        List<Integer> atlasMapIdsSnapshot = List.copyOf(atlasMapIds);
        ClientPlayNetworking.send(new RemoveAtlasMapPayload(atlasMapIdsSnapshot, removedMapId));

        atlasMapIds.remove(Integer.valueOf(removedMapId));
        List<AtlasTilePayload> removedTiles = tiles.stream()
                .filter(t -> t.mapId() == removedMapId)
                .toList();
        tiles.removeIf(t -> t.mapId() == removedMapId);
        renderStates.remove(removedMapId);

        if (atlasMapIds.isEmpty() || tiles.isEmpty()) {
            skipWaypointSaveOnClose = true;
            playMapRemovalSound();
            onClose();
            return;
        }

        // Remove waypoints that were on the removed tile and are not covered by any remaining tile
        for (int i = atlasWaypoints.size() - 1; i >= 0; i--) {
            AtlasContents.WaypointData waypoint = atlasWaypoints.get(i);
            boolean wasOnRemovedTile = false;
            for (AtlasTilePayload rt : removedTiles) {
                if (isWaypointOnTile(waypoint, rt)) {
                    wasOnRemovedTile = true;
                    break;
                }
            }
            if (wasOnRemovedTile && !isWaypointCoveredByAnyTile(waypoint, tiles)) {
                if (isWaypointPinnedToLocatorBar(i)) {
                    unpinWaypointFromLocatorBar(waypoint);
                }
                atlasWaypoints.remove(i);
                atlasIcons.remove(i + 1);
            }
        }

        cachedDimension = null;
        cachedScale = -1;
        updateTileCacheIfNeeded();

        List<Integer> availableScales = getAvailableScales();
        if (!availableScales.contains(activeViewScale) && !availableScales.isEmpty()) {
            activeViewScale = availableScales.getFirst();
            cachedDimension = null;
            cachedScale = -1;
            updateTileCacheIfNeeded();
        }

        rebuildScaleWidgets();
        rebuildOverlayWidgets();
        playMapRemovalSound();
    }

    private static boolean isWaypointOnTile(AtlasContents.WaypointData waypoint, AtlasTilePayload tile) {
        if (!waypoint.dimension().equals(tile.dimension())) {
            return false;
        }
        int mapSpan = 128 << tile.scale();
        double minX = tile.centerX() - mapSpan / 2.0;
        double minZ = tile.centerZ() - mapSpan / 2.0;
        double maxX = minX + mapSpan;
        double maxZ = minZ + mapSpan;

        return waypoint.worldX() >= minX
                && waypoint.worldX() < maxX
                && waypoint.worldZ() >= minZ
                && waypoint.worldZ() < maxZ;
    }

    private static boolean isWaypointCoveredByAnyTile(AtlasContents.WaypointData waypoint, List<AtlasTilePayload> tiles) {
        for (AtlasTilePayload tile : tiles) {
            if (isWaypointOnTile(waypoint, tile)) {
                return true;
            }
        }
        return false;
    }

    private boolean commitWaypointDraft() {
        if (waypointDraft == null) {
            return false;
        }

        String name = waypointDraft.name.trim();
        if (name.isEmpty()) {
            name = "Waypoint " + nextWaypointNumber;
        }

        AtlasContents.WaypointData updatedWaypoint = new AtlasContents.WaypointData(
                waypointDraft.worldX,
                waypointDraft.worldZ,
                name,
                waypointDraft.iconIndex,
                waypointDraft.dimension
        );

        if (editingWaypointIndex >= 0 && editingWaypointIndex < atlasWaypoints.size()) {
            atlasWaypoints.set(editingWaypointIndex, updatedWaypoint);
            atlasIcons.set(editingWaypointIndex + 1, createWaypointIcon(
                    updatedWaypoint.worldX(),
                    updatedWaypoint.worldZ(),
                    Component.literal(updatedWaypoint.name()),
                    updatedWaypoint.iconIndex()
            ));
        } else {
            atlasWaypoints.add(updatedWaypoint);
            atlasIcons.add(createWaypointIcon(
                    updatedWaypoint.worldX(),
                    updatedWaypoint.worldZ(),
                    Component.literal(updatedWaypoint.name()),
                    updatedWaypoint.iconIndex()
            ));
            nextWaypointNumber++;
        }

        selectedWaypointIconIndex = waypointDraft.iconIndex;
        clearWaypointDraft();
        waypointsDirty = true;
        persistWaypointState();
        return true;
    }

    private boolean isWithinContextMenuBounds(double mouseX, double mouseY) {
        if (!isContextMenuOpen()) {
            return false;
        }

        int menuHeight = WAYPOINT_CONTEXT_MENU_ROW_HEIGHT * getContextMenuRowCount();
        return mouseX >= contextMenuX && mouseX < contextMenuX + WAYPOINT_CONTEXT_MENU_WIDTH
                && mouseY >= contextMenuY && mouseY < contextMenuY + menuHeight;
    }

    // ----- Rendering primitives + layered render helpers -----

    private void drawFramedPanel(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int backgroundColor, int borderColor) {
        graphics.fill(x1, y1, x2, y2, backgroundColor);
        graphics.fill(x1, y1, x2, y1 + 1, borderColor);
        graphics.fill(x1, y2 - 1, x2, y2, borderColor);
        graphics.fill(x1, y1, x1 + 1, y2, borderColor);
        graphics.fill(x2 - 1, y1, x2, y2, borderColor);
    }

    private void renderWaypointContextMenu(GuiGraphicsExtractor graphics) {
        if (!isContextMenuOpen()) {
            return;
        }

        int menuHeight = WAYPOINT_CONTEXT_MENU_ROW_HEIGHT * getContextMenuRowCount();
        int menuX2 = contextMenuX + WAYPOINT_CONTEXT_MENU_WIDTH;
        int menuY2 = contextMenuY + menuHeight;
        drawFramedPanel(graphics, contextMenuX, contextMenuY, menuX2, menuY2, 0xE0101010, 0xFF707070);
    }

    private void renderDashedTileGrid(
            GuiGraphicsExtractor graphics,
            AtlasViewport viewport,
            float mapOriginX,
            float mapOriginY,
            float scaledTileSize
    ) {
        if (scaledTileSize <= 1.0f) {
            return;
        }

        int clipX1 = (int) Math.floor(viewport.contentX());
        int clipY1 = (int) Math.floor(viewport.contentY());
        int clipX2 = (int) Math.ceil(viewport.contentX() + viewport.contentWidth());
        int clipY2 = (int) Math.ceil(viewport.contentY() + viewport.contentHeight());

        int mapOriginIntX = Mth.floor(mapOriginX);
        int mapOriginIntY = Mth.floor(mapOriginY);
        float mapOriginFracX = mapOriginX - mapOriginIntX;
        float mapOriginFracY = mapOriginY - mapOriginIntY;
        int dashSpan = GRID_DASH_LENGTH + GRID_DASH_GAP;

        // Preserve fractional panning so the grid slides smoothly instead of pixel-stepping.
        graphics.pose().pushMatrix();
        graphics.pose().translate(mapOriginFracX, mapOriginFracY);

        int firstVertical = (int) Math.floor((clipX1 - mapOriginIntX) / scaledTileSize);
        int lastVertical = (int) Math.ceil((clipX2 - mapOriginIntX) / scaledTileSize);
        int verticalDashStart = clipY1 - Math.floorMod(clipY1 - mapOriginIntY, dashSpan);
        for (int gx = firstVertical; gx <= lastVertical; gx++) {
            int lineX = Mth.floor(mapOriginIntX + gx * scaledTileSize);
            if (lineX < clipX1 || lineX >= clipX2) {
                continue;
            }

            for (int y = verticalDashStart; y < clipY2; y += dashSpan) {
                if (y + GRID_DASH_LENGTH <= clipY1) {
                    continue;
                }
                int y2 = Math.min(clipY2, y + GRID_DASH_LENGTH);
                graphics.fill(lineX, y, lineX + 1, y2, GRID_DASH_COLOR);
            }
        }

        int firstHorizontal = (int) Math.floor((clipY1 - mapOriginIntY) / scaledTileSize);
        int lastHorizontal = (int) Math.ceil((clipY2 - mapOriginIntY) / scaledTileSize);
        int horizontalDashStart = clipX1 - Math.floorMod(clipX1 - mapOriginIntX, dashSpan);
        for (int gy = firstHorizontal; gy <= lastHorizontal; gy++) {
            int lineY = Mth.floor(mapOriginIntY + gy * scaledTileSize);
            if (lineY < clipY1 || lineY >= clipY2) {
                continue;
            }

            for (int x = horizontalDashStart; x < clipX2; x += dashSpan) {
                if (x + GRID_DASH_LENGTH <= clipX1) {
                    continue;
                }
                int x2 = Math.min(clipX2, x + GRID_DASH_LENGTH);
                graphics.fill(x, lineY, x2, lineY + 1, GRID_DASH_COLOR);
            }
        }

        graphics.pose().popMatrix();
    }

    private void renderHoveredTileHighlight(GuiGraphicsExtractor graphics, float x, float y, float scaledTileSize) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scaledTileSize, scaledTileSize);

        // Subtle translucent white wash.
        graphics.fill(0, 0, 1, 1, 0x22FFFFFF);

        graphics.pose().popMatrix();
    }

    private void renderWaypointLabel(GuiGraphicsExtractor graphics, Component name, float centerX, float centerY, int iconHeight) {
        if (name == null || name.getString().isBlank()) {
            return;
        }

        float textScale = 0.5f * (float) SimpleAtlasConfigManager.getWaypointIconSize();
        int textWidth = this.font.width(name);
        float textY = centerY + iconHeight / 2.0f + 1.5f;

        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, textY);
        graphics.pose().scale(textScale, textScale);

        int localX = -textWidth / 2;
        graphics.textWithBackdrop(this.font, name, localX, 0, textWidth, 0xFFFFFFFF);

        graphics.pose().popMatrix();
    }

    private void renderMapTile(GuiGraphicsExtractor graphics, AtlasTilePayload tile, float x, float y, float scale) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null) {
            return;
        }

        MapId id = new MapId(tile.mapId());
        MapItemSavedData data = minecraft.level.getMapData(id);
        if (data == null) {
            return;
        }

        MapRenderState state = renderStates.computeIfAbsent(tile.mapId(), ignored -> new MapRenderState());

        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);

        minecraft.getMapRenderer().extractRenderState(id, data, state);
        // AtlasScreen draws player icons and waypoints itself; suppress them to avoid duplicate markers while preserving treasure and structure markers.
        removeSuppressedDecorations(data, state, tile);
        graphics.map(state);

        graphics.pose().popMatrix();
    }

    private void removeSuppressedDecorations(MapItemSavedData data, MapRenderState state, AtlasTilePayload tile) {
        List<MapDecoration> dataDecs = new ArrayList<>();
        data.getDecorations().forEach(dataDecs::add);
        if (dataDecs.size() == state.decorations.size()) {
            for (int i = state.decorations.size() - 1; i >= 0; i--) {
                MapDecoration dec = dataDecs.get(i);
                if (isSuppressedDecoration(dec, tile)) {
                    state.decorations.remove(i);
                }
            }
        }
    }

    private boolean isSuppressedDecoration(MapDecoration dec, AtlasTilePayload tile) {
        if (isPlayerDecoration(dec)) {
            return true;
        }

        // Suppress any decoration registered by simple-atlas
        if (dec.type().unwrapKey().map(k -> k.identifier().getNamespace().equals(SimpleAtlas.MOD_ID)).orElse(false)) {
            return true;
        }

        int scaleFactor = 1 << tile.scale();
        String decName = dec.name().map(Component::getString).orElse(null);

        for (AtlasContents.WaypointData waypoint : this.atlasWaypoints) {
            if (!waypoint.dimension().equals(tile.dimension())) {
                continue;
            }

            float xDelta = (float) ((waypoint.worldX() - tile.centerX()) / scaleFactor);
            float zDelta = (float) ((waypoint.worldZ() - tile.centerZ()) / scaleFactor);
            if (xDelta < -64.0F || xDelta > 64.0F || zDelta < -64.0F || zDelta > 64.0F) {
                continue;
            }

            byte expectedX = AtlasWaypointDecorations.clampMapCoordinate(xDelta);
            byte expectedY = AtlasWaypointDecorations.clampMapCoordinate(zDelta);

            if (Math.abs(dec.x() - expectedX) <= 1 && Math.abs(dec.y() - expectedY) <= 1) {
                if (decName != null && decName.equals(waypoint.name())) {
                    return true;
                }

                Holder<MapDecorationType> expectedType = AtlasWaypointDecorations.decorationTypeForWaypoint(waypoint.iconIndex());
                if (expectedType != null && dec.type().unwrapKey().equals(expectedType.unwrapKey())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isPlayerDecoration(MapDecoration dec) {
        var type = dec.type();
        return type.equals(MapDecorationTypes.PLAYER)
                || type.equals(MapDecorationTypes.PLAYER_OFF_MAP)
                || type.equals(MapDecorationTypes.PLAYER_OFF_LIMITS)
                || type.equals(MapDecorationTypes.FRAME);
    }

    private void renderAtlasBackground(
            GuiGraphicsExtractor graphics,
            AtlasViewport viewport
    ) {
        int x = (int) Math.floor(viewport.x());
        int y = (int) Math.floor(viewport.y());
        int width = (int) Math.ceil(viewport.width());
        int height = (int) Math.ceil(viewport.height());

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                ATLAS_BACKGROUND_TEXTURE,
                x,
                y,
                0.0f,
                0.0f,
                width,
                height,
                ATLAS_BACKGROUND_TEXTURE_WIDTH,
                ATLAS_BACKGROUND_TEXTURE_HEIGHT,
                ATLAS_BACKGROUND_TEXTURE_WIDTH,
                ATLAS_BACKGROUND_TEXTURE_HEIGHT
        );
    }

    // ----- Hover detection + hover label rendering -----

    private record HoveredAtlasIcon(AtlasIcon icon, AtlasIcon.Anchor anchor, Component title) {}

    private AtlasIcon.Anchor resolveHoveredAnchor(
            AtlasIcon icon,
            Minecraft minecraft,
            List<AtlasTilePayload> visibleTiles,
            float mapOriginX,
            float mapOriginY,
            float scaledTileSize,
            int mouseX,
            int mouseY
    ) {
        AtlasIcon.Anchor anchor = icon.resolveAnchor(minecraft, visibleTiles, mapOriginX, mapOriginY, scaledTileSize);
        if (anchor == null || !icon.containsPoint(anchor, mouseX, mouseY)) {
            return null;
        }
        return anchor;
    }

    private HoveredAtlasIcon findHoveredIcon(
            Minecraft minecraft,
            float mapOriginX,
            float mapOriginY,
            float scaledTileSize,
            int mouseX,
            int mouseY,
            String selectedDimension,
            List<AtlasTilePayload> visibleTiles
    ) {
        AtlasIcon.Anchor playerAnchor = selectedDimension.equals(playerDimension)
                ? resolveHoveredAnchor(playerIcon, minecraft, visibleTiles, mapOriginX, mapOriginY, scaledTileSize, mouseX, mouseY)
                : null;
        if (playerAnchor != null) {
            Component playerTitle = playerIcon.resolveHoverTitle(minecraft);
            if (playerTitle != null) {
                return new HoveredAtlasIcon(playerIcon, playerAnchor, playerTitle);
            }
            return null;
        }

        for (int i = atlasWaypoints.size() - 1; i >= 0; i--) {
            int iconListIndex = i + 1;
            if (iconListIndex >= atlasIcons.size()) {
                continue;
            }

            AtlasContents.WaypointData waypoint = atlasWaypoints.get(i);
            if (!waypoint.dimension().equals(selectedDimension)) {
                continue;
            }

            AtlasIcon icon = atlasIcons.get(iconListIndex);
            AtlasIcon.Anchor anchor = resolveHoveredAnchor(icon, minecraft, visibleTiles, mapOriginX, mapOriginY, scaledTileSize, mouseX, mouseY);
            if (anchor == null) {
                continue;
            }

            Component title = icon.resolveHoverTitle(minecraft);
            if (title != null) {
                return new HoveredAtlasIcon(icon, anchor, title);
            }
        }

        return null;
    }

    private void renderHoveredIconTitle(
            GuiGraphicsExtractor graphics,
            AtlasViewport viewport,
            HoveredAtlasIcon hoveredIcon
    ) {
        int textWidth = this.font.width(hoveredIcon.title());
        int boxPaddingX = 6;
        int boxPaddingY = 3;
        int boxWidth = textWidth + boxPaddingX * 2;
        int boxHeight = this.font.lineHeight + boxPaddingY * 2;

        int minX = (int) Math.floor(viewport.contentX()) + 2;
        int maxX = (int) Math.ceil(viewport.contentX() + viewport.contentWidth()) - boxWidth - 2;
        int centeredX = Mth.floor(hoveredIcon.anchor().screenX() - boxWidth / 2.0f);
        int boxX = maxX >= minX ? Mth.clamp(centeredX, minX, maxX) : centeredX;

        int minY = (int) Math.floor(viewport.contentY()) + 2;
        int maxY = (int) Math.ceil(viewport.contentY() + viewport.contentHeight()) - boxHeight - 2;
        int preferredY = Mth.floor(hoveredIcon.anchor().screenY() + hoveredIcon.icon().renderHeight() / 2.0f + ICON_HOVER_TITLE_PADDING);
        int boxY = maxY >= minY ? Mth.clamp(preferredY, minY, maxY) : preferredY;

        int boxX2 = boxX + boxWidth;
        int boxY2 = boxY + boxHeight;

        drawFramedPanel(graphics, boxX, boxY, boxX2, boxY2, 0xE0101010, 0xFF707070);

        int textX = boxX + boxPaddingX;
        int textY = boxY + boxPaddingY;
        graphics.textWithBackdrop(this.font, hoveredIcon.title(), textX, textY, textWidth, 0xFFFFFFFF);
    }

    private void renderWaypointDraftOverlay(GuiGraphicsExtractor graphics, AtlasViewport viewport) {
        if (waypointDraft == null || waypointIconOptions.isEmpty()) {
            return;
        }

        WaypointIconOption option = waypointIconOptions.get(waypointDraft.iconIndex);
        WaypointPickerLayout layout = getWaypointPickerLayout(viewport);
        drawFramedPanel(graphics, layout.panelX(), layout.panelY(), layout.panelX2(), layout.panelY2(), 0xB0101010, 0xFF606060);

        Component title = Component.translatable(editingWaypointIndex >= 0
                ? "gui.simple_atlas.waypoint.edit_title"
                : "gui.simple_atlas.waypoint.new_title");
        int titleWidth = this.font.width(title);
        int titleX = layout.panelX() + (layout.panelX2() - layout.panelX() - titleWidth) / 2;
        graphics.textWithBackdrop(this.font, title, titleX, layout.panelY() + 6, titleWidth, 0xFFFFFFFF);

        graphics.fill(layout.iconX() - 2, layout.iconY() - 2, layout.iconX() + WAYPOINT_PICKER_PREVIEW_SIZE + 2, layout.iconY() + WAYPOINT_PICKER_PREVIEW_SIZE + 2, 0x70000000);
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                option.texture(),
                layout.iconX(),
                layout.iconY(),
                0.0f,
                0.0f,
                WAYPOINT_PICKER_PREVIEW_SIZE,
                WAYPOINT_PICKER_PREVIEW_SIZE,
                WAYPOINT_TEXTURE_SIZE,
                WAYPOINT_TEXTURE_SIZE,
                WAYPOINT_TEXTURE_SIZE,
                WAYPOINT_TEXTURE_SIZE
        );

        graphics.fill(layout.inputX(), layout.inputY(), layout.inputX2(), layout.inputY2(), 0x90000000);
    }

    // ----- Waypoint draft panel layout -----

    private WaypointPickerLayout getWaypointPickerLayout(AtlasViewport viewport) {
        int panelWidth = Math.min(WAYPOINT_PICKER_PANEL_WIDTH, (int) Math.floor(viewport.contentWidth()) - WAYPOINT_PICKER_PADDING * 2);
        int panelX = (int) Math.floor(viewport.contentX() + (viewport.contentWidth() - panelWidth) / 2.0f);
        int panelY = (int) Math.floor(viewport.contentY()) + WAYPOINT_PICKER_PADDING;
        int panelX2 = panelX + panelWidth;
        int panelY2 = panelY + WAYPOINT_PICKER_PANEL_HEIGHT;

        // Title area: panelY + 6 to panelY + 18 (12px height)

        // Icon and arrows area starts at panelY + 20
        int iconY = panelY + 20;
        int iconX = panelX + (panelWidth - WAYPOINT_PICKER_PREVIEW_SIZE) / 2;
        int arrowY = iconY + (WAYPOINT_PICKER_PREVIEW_SIZE - WAYPOINT_PICKER_ARROW_SIZE) / 2;
        int leftArrowX = iconX - WAYPOINT_PICKER_ARROW_SIZE - 8;
        int rightArrowX = iconX + WAYPOINT_PICKER_PREVIEW_SIZE + 8;

        // Icon area ends at panelY + 40 (20px icon), so input starts at panelY + 48
        int inputWidth = panelWidth - 16;
        int inputX = panelX + (panelWidth - inputWidth) / 2;
        int inputY = panelY + 48;
        int inputX2 = inputX + inputWidth;
        int inputY2 = inputY + WAYPOINT_PICKER_INPUT_HEIGHT;

        // Input ends at panelY + 64, buttons start lower with more gap
        int buttonHeight = 14;
        int buttonWidth = 50;
        int buttonGap = 6;
        int totalButtonWidth = (buttonWidth * 2) + buttonGap;
        int buttonsStartX = panelX + (panelWidth - totalButtonWidth) / 2;
        int buttonsY = panelY + 72;
        int buttonsY2 = buttonsY + buttonHeight;

        int cancelButtonX2 = buttonsStartX + buttonWidth;
        int confirmButtonX = cancelButtonX2 + buttonGap;
        int confirmButtonX2 = confirmButtonX + buttonWidth;

        return new WaypointPickerLayout(
                panelX,
                panelY,
                panelX2,
                panelY2,
                iconX,
                iconY,
                leftArrowX,
                rightArrowX,
                arrowY,
                inputX,
                inputY,
                inputX2,
                inputY2,
                confirmButtonX,
                buttonsY,
                confirmButtonX2,
                buttonsY2,
                buttonsStartX,
                buttonsY,
                cancelButtonX2,
                buttonsY2
        );
    }

     // ----- Main atlas rendering -----

    private record BookmarkTab(int index, String dimension, Identifier iconTexture) {}

    private record BookmarkTabLayout(BookmarkTab tab, float x, float y, float width, float height) {
        float renderX(boolean selected, float retractionAmount) {
            return selected ? x : x - retractionAmount;
        }

        float visibleLeft(float clipLeft, boolean selected, float retractionAmount) {
            return Math.max(renderX(selected, retractionAmount), clipLeft);
        }

        float visibleRight(boolean selected, float retractionAmount) {
            return renderX(selected, retractionAmount) + width;
        }

        boolean contains(double mouseX, double mouseY, float clipLeft, boolean selected, float retractionAmount) {
            float left = visibleLeft(clipLeft, selected, retractionAmount);
            float right = visibleRight(selected, retractionAmount);
            return mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + height;
        }
    }

    private List<BookmarkTab> getBookmarkTabs() {
        List<BookmarkTab> tabs = new ArrayList<>(dimensionTabs.size());
        for (int i = 0; i < dimensionTabs.size(); i++) {
            String dim = dimensionTabs.get(i);
            Identifier icon = DIMENSION_ICON_MAP.getOrDefault(dim, BOOKMARK_OTHER_ICON_TEXTURE);
            tabs.add(new BookmarkTab(i, dim, icon));
        }
        return tabs;
    }

    private List<BookmarkTabLayout> getBookmarkTabLayouts(AtlasViewport viewport) {
        List<BookmarkTab> tabs = getBookmarkTabs();
        List<BookmarkTabLayout> layouts = new ArrayList<>(tabs.size());
        float scale = viewport.width() / ATLAS_BACKGROUND_TEXTURE_WIDTH;
        float scaledTabWidth = BOOKMARK_TAB_WIDTH * scale;
        float scaledTabHeight = BOOKMARK_TAB_HEIGHT * scale;
        float scaledTabGap = BOOKMARK_TAB_GAP * scale;

        float mapRight = viewport.contentX() + viewport.contentWidth();
        float tabX = mapRight - BOOKMARK_TABS_RIGHT_OVERLAP * scale;
        float tabY = viewport.contentY() + BOOKMARK_TABS_TOP_OFFSET * scale;

        for (BookmarkTab tab : tabs) {
            layouts.add(new BookmarkTabLayout(tab, tabX, tabY, scaledTabWidth, scaledTabHeight));
            tabY += scaledTabHeight + scaledTabGap;
        }

        return layouts;
    }

    private float getBookmarkRetractionAmount(AtlasViewport viewport) {
        float scale = viewport.width() / ATLAS_BACKGROUND_TEXTURE_WIDTH;
        return BOOKMARK_UNSELECTED_RETRACTION_AMOUNT * scale;
    }

    private int getBookmarkClipLeft(AtlasViewport viewport) {
        float scale = viewport.width() / ATLAS_BACKGROUND_TEXTURE_WIDTH;
        float left = Float.POSITIVE_INFINITY;
        for (BookmarkTabLayout layout : getBookmarkTabLayouts(viewport)) {
            left = Math.min(left, layout.x());
        }
        return Float.isFinite(left)
                ? (int) Math.floor(left + BOOKMARK_CLIP_LEFT_OFFSET * scale)
                : 0;
    }

    private void renderBookmarkTabs(GuiGraphicsExtractor graphics, AtlasViewport viewport, int mouseX, int mouseY) {
        float retractionAmount = getBookmarkRetractionAmount(viewport);
        float clipLeft = getBookmarkClipLeft(viewport);
        float scale = viewport.width() / ATLAS_BACKGROUND_TEXTURE_WIDTH;
        int iconSize = Math.max(1, Math.round(BOOKMARK_ICON_RENDER_SIZE * scale));
        for (BookmarkTabLayout layout : getBookmarkTabLayouts(viewport)) {
            boolean isSelected = layout.tab().index() == selectedBookmarkTab;
            boolean isHovered = !isSelected && layout.contains(mouseX, mouseY, clipLeft, false, retractionAmount);
            Identifier bgTexture = isSelected ? BOOKMARK_TAB_SELECTED_TEXTURE : BOOKMARK_TAB_TEXTURE;
            float renderX = layout.renderX(isSelected, retractionAmount);
            // Normal unselected: slightly darkened; hovered unselected or selected: full white
            int bgColor = (isSelected || isHovered) ? 0xFFFFFFFF : 0xFFCCCCCC;

            graphics.pose().pushMatrix();
            graphics.pose().translate(renderX + layout.width() / 2.0f, layout.y() + layout.height() / 2.0f);
            graphics.pose().rotate((float) (Math.PI / 2.0));

            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    bgTexture,
                    (int) Math.floor(-layout.width() / 2.0f),
                    (int) Math.floor(-layout.height() / 2.0f),
                    0.0f,
                    0.0f,
                    (int) Math.ceil(layout.width()),
                    (int) Math.ceil(layout.height()),
                    BOOKMARK_TAB_WIDTH,
                    BOOKMARK_TAB_HEIGHT,
                    BOOKMARK_TAB_WIDTH,
                    BOOKMARK_TAB_HEIGHT,
                    bgColor
            );

            graphics.pose().rotate((float) (-Math.PI / 2.0));
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    layout.tab().iconTexture(),
                    -iconSize / 2,
                    -iconSize / 2,
                    0.0f,
                    0.0f,
                    iconSize,
                    iconSize,
                    BOOKMARK_ICON_TEXTURE_SIZE,
                    BOOKMARK_ICON_TEXTURE_SIZE,
                    BOOKMARK_ICON_TEXTURE_SIZE,
                    BOOKMARK_ICON_TEXTURE_SIZE,
                    bgColor
            );

            graphics.pose().popMatrix();
        }
    }

    private boolean handleBookmarkTabClick(double mouseX, double mouseY, AtlasViewport viewport) {
        float clipLeft = getBookmarkClipLeft(viewport);
        float retractionAmount = getBookmarkRetractionAmount(viewport);
        for (BookmarkTabLayout layout : getBookmarkTabLayouts(viewport)) {
            boolean isSelected = layout.tab().index() == selectedBookmarkTab;
            if (layout.contains(mouseX, mouseY, clipLeft, isSelected, retractionAmount)) {
                if (!isSelected) {
                    selectedBookmarkTab = layout.tab().index();
                    List<Integer> availableScales = getAvailableScales();
                    if (!availableScales.isEmpty() && !availableScales.contains(activeViewScale)) {
                        activeViewScale = availableScales.getFirst();
                    }
                    rebuildScaleWidgets();
                    centerOnSelectedDimensionAtCurrentZoom();
                    closeContextMenu();
                    playBookmarkTabSound();
                }
                return true;
            }
        }

        return false;
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        MapInteractionContext context = buildMapInteractionContext();
        AtlasViewport viewport = context.viewport();
        float scaledTileSize = context.scaledTileSize();
        float mapOriginX = context.mapOriginX();
        float mapOriginY = context.mapOriginY();
        boolean mouseWithinAtlasContent = isWithinAtlasContent(viewport, mouseX, mouseY);
        Minecraft minecraft = Minecraft.getInstance();
        HoveredAtlasIcon hoveredIcon;

        renderAtlasBackground(graphics, viewport);

        graphics.enableScissor(getBookmarkClipLeft(viewport), 0, this.width, this.height);
        renderBookmarkTabs(graphics, viewport, mouseX, mouseY);
        graphics.disableScissor();

        int clipX1 = (int) Math.floor(viewport.contentX());
        int clipY1 = (int) Math.floor(viewport.contentY());
        int clipX2 = (int) Math.ceil(viewport.contentX() + viewport.contentWidth());
        int clipY2 = (int) Math.ceil(viewport.contentY() + viewport.contentHeight());
        graphics.enableScissor(clipX1, clipY1, clipX2, clipY2);

        renderDashedTileGrid(graphics, viewport, mapOriginX, mapOriginY, scaledTileSize);

        updateTileCacheIfNeeded();
        String selectedDimension = getSelectedDimension();
        List<AtlasTilePayload> visibleTiles = cachedVisibleTiles;
        DimensionTileBounds dimBounds = cachedDimensionBounds;

        for (AtlasTilePayload tile : visibleTiles) {
            float x = mapOriginX + (tile.tileX() - dimBounds.minTileX()) * scaledTileSize;
            float y = mapOriginY + (tile.tileY() - dimBounds.minTileY()) * scaledTileSize;

            renderMapTile(graphics, tile, x, y, scaledTileSize / 128.0f);

            boolean hovered =
                    mouseWithinAtlasContent &&
                            mouseX >= x &&
                            mouseX < x + scaledTileSize &&
                            mouseY >= y &&
                            mouseY < y + scaledTileSize;

            if (hovered && !isContextMenuOpen() && waypointDraft == null) {
                renderHoveredTileHighlight(graphics, x, y, scaledTileSize);
            }
        }

        for (int iconListIndex = 1; iconListIndex < atlasIcons.size(); iconListIndex++) {
            // Skip rendering the waypoint icon we're currently editing, as we'll render the draft version instead.
            if (editingWaypointIndex >= 0 && iconListIndex == editingWaypointIndex + 1) {
                continue;
            }
            // Skip waypoints not in the selected dimension
            int waypointIndexInList = iconListIndex - 1;
            AtlasContents.WaypointData wp = null;
            if (waypointIndexInList < atlasWaypoints.size()) {
                wp = atlasWaypoints.get(waypointIndexInList);
                if (!wp.dimension().equals(selectedDimension)) {
                    continue;
                }
            }
            AtlasIcon atlasIcon = atlasIcons.get(iconListIndex);
            atlasIcon.render(graphics, minecraft, visibleTiles, mapOriginX, mapOriginY, scaledTileSize);

            if (wp != null && !wp.name().isBlank()) {
                AtlasIcon.Anchor anchor = atlasIcon.resolveAnchor(minecraft, visibleTiles, mapOriginX, mapOriginY, scaledTileSize);
                if (anchor != null) {
                    renderWaypointLabel(graphics, Component.literal(wp.name()), anchor.screenX(), anchor.screenY(), atlasIcon.renderHeight());
                }
            }
        }

        renderPinnedWaypointMarkers(graphics, minecraft, mapOriginX, mapOriginY, scaledTileSize);

        if (waypointDraft != null && !waypointIconOptions.isEmpty()) {
            Component draftTitle = waypointDraft.name.isBlank()
                    ? Component.translatable("gui.simple_atlas.waypoint.default_name")
                    : Component.literal(waypointDraft.name);
            AtlasIcon draftIcon = createWaypointIcon(waypointDraft.worldX, waypointDraft.worldZ, draftTitle, waypointDraft.iconIndex);
            draftIcon.render(graphics, minecraft, visibleTiles, mapOriginX, mapOriginY, scaledTileSize);

            AtlasIcon.Anchor anchor = draftIcon.resolveAnchor(minecraft, visibleTiles, mapOriginX, mapOriginY, scaledTileSize);
            if (anchor != null) {
                renderWaypointLabel(graphics, draftTitle, anchor.screenX(), anchor.screenY(), draftIcon.renderHeight());
            }
        }

        // Draw the player marker last so it stays visible above waypoint markers.
        // Only show player marker in the player's current dimension
        if (selectedDimension.equals(playerDimension)) {
            playerIcon.render(graphics, minecraft, visibleTiles, mapOriginX, mapOriginY, scaledTileSize);
        }

        // Don't show hovered icon titles when the waypoint draft menu is open
        if (waypointDraft == null) {
            hoveredIcon = mouseWithinAtlasContent
                    ? findHoveredIcon(minecraft, mapOriginX, mapOriginY, scaledTileSize, mouseX, mouseY, selectedDimension, visibleTiles)
                    : null;
            if (hoveredIcon != null) {
                renderHoveredIconTitle(graphics, viewport, hoveredIcon);
            }
        }

        graphics.disableScissor();

        renderWaypointDraftOverlay(graphics, viewport);
        renderWaypointContextMenu(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    // ----- Context menu option handlers -----

    private void handleWaypointContextMenuOption(int option, int waypointIndex) {
        AtlasContents.WaypointData waypoint = getWaypoint(waypointIndex);
        if (waypoint == null) {
            return;
        }

        boolean canTeleport = canUseTeleportCommand();

        switch (option) {
            case 0 -> {
                if (isWaypointPinnedToLocatorBar(waypointIndex)) {
                    unpinWaypointFromLocatorBar(waypointIndex);
                } else {
                    pinWaypointToLocatorBar(waypointIndex);
                }
            }
            case 1 -> {
                if (canTeleport) {
                    teleportToLocation(waypoint.worldX(), waypoint.worldZ(), waypoint.dimension());
                } else {
                    copyCoordinatesToClipboard(waypoint.worldX(), waypoint.worldZ());
                }
            }
            case 2 -> {
                if (canTeleport) {
                    copyCoordinatesToClipboard(waypoint.worldX(), waypoint.worldZ());
                } else {
                    beginEditWaypoint(waypointIndex);
                }
            }
            case 3 -> {
                if (canTeleport) {
                    beginEditWaypoint(waypointIndex);
                } else {
                    deleteWaypoint(waypointIndex);
                }
            }
            case 4 -> {
                if (canTeleport) {
                    deleteWaypoint(waypointIndex);
                }
            }
            default -> {
            }
        }
    }

    private void handleWorldPointContextMenuOption(int option, WorldPoint worldPoint, int mapIdAtClick) {
        boolean canTeleport = canUseTeleportCommand();
        switch (option) {
            case 0 -> {
                if (canTeleport) {
                    // Teleport to the selected dimension's coordinates
                    String selectedDimension = getSelectedDimension();
                    teleportToLocation(worldPoint.x(), worldPoint.z(), selectedDimension);
                } else {
                    beginNewWaypoint(worldPoint);
                }
            }
            case 1 -> {
                if (canTeleport) {
                    beginNewWaypoint(worldPoint);
                } else {
                    copyCoordinatesToClipboard(worldPoint.x(), worldPoint.z());
                }
            }
            case 2 -> {
                if (canTeleport) {
                    copyCoordinatesToClipboard(worldPoint.x(), worldPoint.z());
                } else if (mapIdAtClick >= 0) {
                    requestAtlasMapRemoval(mapIdAtClick);
                }
            }
            case 3 -> {
                if (canTeleport && mapIdAtClick >= 0) {
                    requestAtlasMapRemoval(mapIdAtClick);
                }
            }
            default -> {
                // No-op: click was inside the menu frame but not on a valid option row.
            }
        }
    }

    private String getSelectedDimension() {
        return (selectedBookmarkTab < dimensionTabs.size())
                ? dimensionTabs.get(selectedBookmarkTab)
                : (dimensionTabs.isEmpty() ? "minecraft:overworld" : dimensionTabs.getFirst());
    }

    private List<AtlasTilePayload> getVisibleTilesForDimension(String dimension) {
        List<AtlasTilePayload> forDim = tiles.stream()
                .filter(t -> t.dimension().equals(dimension) && t.scale() == activeViewScale)
                .toList();
        if (forDim.isEmpty()) {
            forDim = tiles.stream()
                    .filter(t -> t.dimension().equals(dimension))
                    .toList();
        }
        return forDim;
    }

    // ----- Screen input + lifecycle overrides -----

    // Mouse

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        // Check bookmark tabs first
        AtlasViewport viewport = getAtlasViewport();
        if (event.button() == 0 && handleBookmarkTabClick(event.x(), event.y(), viewport)) {
            return true;
        }

        if (waypointDraft != null) {
            super.mouseClicked(event, doubleClick);
            return true;
        }

        if (isContextMenuOpen()) {
            boolean insideExistingMenu = isWithinContextMenuBounds(event.x(), event.y());
            if (super.mouseClicked(event, doubleClick)) {
                return true;
            }

            if (event.button() == 0) {
                closeContextMenu();
                return true;
            }

            if (event.button() == 1) {
                closeContextMenu();
                if (insideExistingMenu) {
                    return true;
                }
            }
        }

        if (event.button() == 0) {
            if (prevScaleButton != null && prevScaleButton.visible && prevScaleButton.mouseClicked(event, doubleClick)) {
                return true;
            }
            if (scaleToggleButton != null && scaleToggleButton.visible && scaleToggleButton.mouseClicked(event, doubleClick)) {
                return true;
            }
            if (nextScaleButton != null && nextScaleButton.visible && nextScaleButton.mouseClicked(event, doubleClick)) {
                return true;
            }
        }

        if (event.button() == 1) {
            return handleMapRightClick(event, buildMapInteractionContext());
        }

        if (event.button() == 0 && waypointDraft == null && !isContextMenuOpen()) {
            if (isWithinAtlasContent(viewport, event.x(), event.y())) {
                leftDragging = true;
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (leftDragging && event.button() == 0) {
            panX += dx;
            panY += dy;
            return true;
        }

        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            leftDragging = false;
            super.mouseReleased(event);
            return true;
        }

        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Close context menu if it's open
        if (isContextMenuOpen()) {
            closeContextMenu();
            return true;
        }

        float oldZoom = this.zoom;
        float newZoom;
        AtlasViewport viewport = getAtlasViewport();

        if (scrollY > 0) {
            newZoom = Math.min(MAX_ZOOM, oldZoom * ZOOM_STEP);
        } else if (scrollY < 0) {
            newZoom = Math.max(MIN_ZOOM, oldZoom / ZOOM_STEP);
        } else {
            return false;
        }

        if (newZoom == oldZoom) {
            return true;
        }

        float oldScaledTileSize = TILE_SIZE * oldZoom;
        String selectedDimension = getSelectedDimension();
        float oldOriginX = getMapOriginX(viewport, oldScaledTileSize, selectedDimension);
        float oldOriginY = getMapOriginY(viewport, oldScaledTileSize, selectedDimension);

        // atlas-space position under cursor before zoom
        double atlasX = (mouseX - oldOriginX - panX) / oldZoom;
        double atlasY = (mouseY - oldOriginY - panY) / oldZoom;

        this.zoom = newZoom;

        float newScaledTileSize = TILE_SIZE * newZoom;
        float newOriginX = getMapOriginX(viewport, newScaledTileSize, selectedDimension);
        float newOriginY = getMapOriginY(viewport, newScaledTileSize, selectedDimension);

        // keep same atlas-space point under cursor
        this.panX = (float) (mouseX - newOriginX - atlasX * newZoom);
        this.panY = (float) (mouseY - newOriginY - atlasY * newZoom);

        return true;
    }

    // Keyboard

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (waypointDraft != null) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                clearWaypointDraft();
                return true;
            }

            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                return commitWaypointDraft();
            }

            if (waypointNameEditBox != null) {
                waypointNameEditBox.keyPressed(event);
            }
            // Consume all key events while the draft UI is open.
            return true;
        }

        if (ModKeyBindings.RESET_ZOOM_KEY != null && ModKeyBindings.RESET_ZOOM_KEY.matches(event)) {
            resetPerspective();
            return true;
        }

        if (event.key() == GLFW.GLFW_KEY_ESCAPE && isContextMenuOpen()) {
            closeContextMenu();
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (!skipWaypointSaveOnClose && waypointsDirty) {
            persistWaypointState();
        }
        ClientPlayNetworking.send(new CloseAtlasViewPayload(this.activeViewScale));
        super.onClose();
    }

    // Text input + screen properties

    @Override
    public boolean charTyped(@NonNull CharacterEvent event) {
        if (waypointDraft != null) {
            if (waypointNameEditBox != null) {
                return waypointNameEditBox.charTyped(event);
            }
            return true;
        }

        return super.charTyped(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
