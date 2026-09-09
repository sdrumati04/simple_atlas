package rubbertoe.simple_atlas.network;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import rubbertoe.simple_atlas.SimpleAtlas;
import rubbertoe.simple_atlas.component.AtlasContents;

import java.util.ArrayList;
import java.util.List;

public record OpenAtlasScreenPayload(
        List<AtlasTilePayload> tiles,
        List<Integer> atlasMapIds,
        List<AtlasContents.WaypointData> waypoints,
        int selectedWaypointIconIndex,
        int nextWaypointNumber,
        String playerDimension,
        int selectedScale
) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(SimpleAtlas.MOD_ID, "open_atlas_screen");
    public static final Type<OpenAtlasScreenPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, AtlasTilePayload> TILE_CODEC =
            StreamCodec.of(
                    (buf, val) -> {
                        buf.writeInt(val.mapId());
                        buf.writeInt(val.centerX());
                        buf.writeInt(val.centerZ());
                        buf.writeInt(val.tileX());
                        buf.writeInt(val.tileY());
                        ByteBufCodecs.stringUtf8(256).encode(buf, val.dimension());
                        buf.writeInt(val.scale());
                    },
                    buf -> new AtlasTilePayload(
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt(),
                            ByteBufCodecs.stringUtf8(256).decode(buf),
                            buf.readInt()
                    )
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, AtlasContents.WaypointData> WAYPOINT_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.DOUBLE,
                    AtlasContents.WaypointData::worldX,
                    ByteBufCodecs.DOUBLE,
                    AtlasContents.WaypointData::worldZ,
                    ByteBufCodecs.stringUtf8(32),
                    AtlasContents.WaypointData::name,
                    ByteBufCodecs.INT,
                    AtlasContents.WaypointData::iconIndex,
                    ByteBufCodecs.stringUtf8(256),
                    AtlasContents.WaypointData::dimension,
                    AtlasContents.WaypointData::new
            );

    private static final StreamCodec<RegistryFriendlyByteBuf, List<AtlasTilePayload>> TILES_CODEC =
            ByteBufCodecs.collection(ArrayList::new, TILE_CODEC);
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Integer>> MAP_IDS_CODEC =
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.INT);
    private static final StreamCodec<RegistryFriendlyByteBuf, List<AtlasContents.WaypointData>> WAYPOINTS_CODEC =
            ByteBufCodecs.collection(ArrayList::new, WAYPOINT_CODEC);

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenAtlasScreenPayload> CODEC = StreamCodec.of(
            (buf, val) -> {
                TILES_CODEC.encode(buf, new ArrayList<>(val.tiles()));
                MAP_IDS_CODEC.encode(buf, new ArrayList<>(val.atlasMapIds()));
                WAYPOINTS_CODEC.encode(buf, new ArrayList<>(val.waypoints()));
                buf.writeInt(val.selectedWaypointIconIndex());
                buf.writeInt(val.nextWaypointNumber());
                ByteBufCodecs.stringUtf8(256).encode(buf, val.playerDimension());
                buf.writeInt(val.selectedScale());
            },
            buf -> new OpenAtlasScreenPayload(
                    TILES_CODEC.decode(buf),
                    MAP_IDS_CODEC.decode(buf),
                    WAYPOINTS_CODEC.decode(buf),
                    buf.readInt(),
                    buf.readInt(),
                    ByteBufCodecs.stringUtf8(256).decode(buf),
                    buf.readInt()
            )
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

