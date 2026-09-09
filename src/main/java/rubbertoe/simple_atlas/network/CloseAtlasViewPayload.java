package rubbertoe.simple_atlas.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import rubbertoe.simple_atlas.SimpleAtlas;

public record CloseAtlasViewPayload(int selectedScale) implements CustomPacketPayload {
    public static final Type<CloseAtlasViewPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(SimpleAtlas.MOD_ID, "close_atlas_view"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CloseAtlasViewPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    CloseAtlasViewPayload::selectedScale,
                    CloseAtlasViewPayload::new
            );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}