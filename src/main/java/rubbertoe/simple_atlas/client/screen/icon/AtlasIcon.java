package rubbertoe.simple_atlas.client.screen.icon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.Nullable;
import rubbertoe.simple_atlas.network.AtlasTilePayload;

import java.util.List;

public abstract class AtlasIcon {
    private final Identifier texture;
    private final int textureWidth;
    private final int textureHeight;
    private final int renderWidth;
    private final int renderHeight;

    protected AtlasIcon(
            Identifier texture,
            int textureWidth,
            int textureHeight,
            int renderWidth,
            int renderHeight
    ) {
        this.texture = texture;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.renderWidth = renderWidth;
        this.renderHeight = renderHeight;
    }

    protected abstract WorldPoint getWorldPoint(Minecraft minecraft);

    protected float getRotationRadians(Minecraft minecraft) {
        return 0.0f;
    }

    protected boolean isVisible(Minecraft minecraft) {
        return true;
    }

    protected @Nullable Component getHoverTitle(Minecraft minecraft) {
        return null;
    }

    public final @Nullable Component resolveHoverTitle(Minecraft minecraft) {
        return isVisible(minecraft) ? getHoverTitle(minecraft) : null;
    }

    public final int renderHeight() {
        return renderHeight;
    }

    public final boolean containsPoint(Anchor anchor, double mouseX, double mouseY) {
        float halfWidth = renderWidth / 2.0f;
        float halfHeight = renderHeight / 2.0f;
        return mouseX >= anchor.screenX() - halfWidth
                && mouseX < anchor.screenX() + halfWidth
                && mouseY >= anchor.screenY() - halfHeight
                && mouseY < anchor.screenY() + halfHeight;
    }

    protected void renderAtAnchor(GuiGraphicsExtractor graphics, Minecraft minecraft, Anchor anchor) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(anchor.screenX(), anchor.screenY());
        graphics.pose().rotate(getRotationRadians(minecraft));

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                -renderWidth / 2,
                -renderHeight / 2,
                0.0f,
                0.0f,
                renderWidth,
                renderHeight,
                textureWidth,
                textureHeight,
                textureWidth,
                textureHeight
        );

        graphics.pose().popMatrix();
    }

    public final Anchor resolveAnchor(
            Minecraft minecraft,
            List<AtlasTilePayload> tiles,
            float mapOriginX,
            float mapOriginY,
            float scaledTileSize
    ) {
        if (!isVisible(minecraft) || minecraft.level == null || tiles.isEmpty()) {
            return null;
        }

        WorldPoint worldPoint = getWorldPoint(minecraft);
        if (worldPoint == null) {
            return null;
        }

        int scaleFactor = 1 << tiles.getFirst().scale();
        double bestDistSq = Double.MAX_VALUE;
        AtlasTilePayload bestTile = null;
        float bestLocalX = 0.0f;
        float bestLocalY = 0.0f;

        AtlasTilePayload fallbackTile = null;
        double fallbackDistSq = Double.MAX_VALUE;
        float fallbackLocalX = 0.0f;
        float fallbackLocalY = 0.0f;

        int minTileX = Integer.MAX_VALUE;
        int minTileY = Integer.MAX_VALUE;

        for (AtlasTilePayload tile : tiles) {
            minTileX = Math.min(minTileX, tile.tileX());
            minTileY = Math.min(minTileY, tile.tileY());

            double tileWorldMinX = tile.centerX() - 64.0 * scaleFactor;
            double tileWorldMinZ = tile.centerZ() - 64.0 * scaleFactor;

            double localPixelX = (worldPoint.x() - tileWorldMinX) / scaleFactor;
            double localPixelY = (worldPoint.z() - tileWorldMinZ) / scaleFactor;

            boolean inside =
                    localPixelX >= 0.0 && localPixelX < 128.0 &&
                            localPixelY >= 0.0 && localPixelY < 128.0;

            double dx = worldPoint.x() - tile.centerX();
            double dz = worldPoint.z() - tile.centerZ();
            double distSq = dx * dx + dz * dz;

            if (inside && distSq < bestDistSq) {
                bestDistSq = distSq;
                bestTile = tile;
                bestLocalX = (float) localPixelX;
                bestLocalY = (float) localPixelY;
            }

            if (distSq < fallbackDistSq) {
                fallbackDistSq = distSq;
                fallbackTile = tile;
                fallbackLocalX = (float) localPixelX;
                fallbackLocalY = (float) localPixelY;
            }
        }

        AtlasTilePayload chosen;
        float localX;
        float localY;

        if (bestTile != null) {
            chosen = bestTile;
            localX = bestLocalX;
            localY = bestLocalY;
        } else if (fallbackTile != null) {
            chosen = fallbackTile;
            localX = fallbackLocalX;
            localY = fallbackLocalY;
        } else {
            return null;
        }

        float localTileX = chosen.tileX() - minTileX;
        float localTileY = chosen.tileY() - minTileY;

        float tileScreenX = mapOriginX + localTileX * scaledTileSize;
        float tileScreenY = mapOriginY + localTileY * scaledTileSize;

        float screenX = tileScreenX + localX * (scaledTileSize / 128.0f);
        float screenY = tileScreenY + localY * (scaledTileSize / 128.0f);

        return new Anchor(screenX, screenY);
    }

    public final void render(
            GuiGraphicsExtractor graphics,
            Minecraft minecraft,
            List<AtlasTilePayload> tiles,
            float mapOriginX,
            float mapOriginY,
            float scaledTileSize
    ) {
        Anchor anchor = resolveAnchor(minecraft, tiles, mapOriginX, mapOriginY, scaledTileSize);
        if (anchor == null) {
            return;
        }

        renderAtAnchor(graphics, minecraft, anchor);
    }

    public record Anchor(float screenX, float screenY) {}

    protected record WorldPoint(double x, double z) {}
}

