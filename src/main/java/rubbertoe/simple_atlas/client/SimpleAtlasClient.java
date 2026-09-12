package rubbertoe.simple_atlas.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import rubbertoe.simple_atlas.client.input.ModKeyBindings;
import rubbertoe.simple_atlas.client.screen.AtlasScreen;
import rubbertoe.simple_atlas.compat.ImmersiveOverlaysCompat;
import rubbertoe.simple_atlas.network.OpenAtlasScreenPayload;

public class SimpleAtlasClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModKeyBindings.initialize();
        ImmersiveOverlaysCompat.init();

        ClientLifecycleEvents.CLIENT_STARTED.register(_ -> ImmersiveOverlaysCompat.init());

        // Register network receiver for opening atlas screen from server
        ClientPlayNetworking.registerGlobalReceiver(OpenAtlasScreenPayload.TYPE, (payload, _) ->
            Minecraft.getInstance().gui.setScreen(AtlasScreen.fromPayload(payload))
        );

        // Register client tick event to handle key presses
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (ModKeyBindings.RESET_ZOOM_KEY.consumeClick()) {
                // Reset the perspective if an atlas screen is currently open
                if (client.gui.screen() instanceof AtlasScreen atlasScreen) {
                    atlasScreen.resetPerspective();
                }
            }
        });
    }
}
