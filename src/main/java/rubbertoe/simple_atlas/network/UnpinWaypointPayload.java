package rubbertoe.simple_atlas.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import rubbertoe.simple_atlas.SimpleAtlas;

public record UnpinWaypointPayload(
        double worldX,
        double worldZ,
        String dimension
) implements CustomPacketPayload {
    public static final Type<UnpinWaypointPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(SimpleAtlas.MOD_ID, "unpin_waypoint"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UnpinWaypointPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.DOUBLE,
                    UnpinWaypointPayload::worldX,
                    ByteBufCodecs.DOUBLE,
                    UnpinWaypointPayload::worldZ,
                    ByteBufCodecs.STRING_UTF8,
                    UnpinWaypointPayload::dimension,
                    UnpinWaypointPayload::new
            );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

