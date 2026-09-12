package rubbertoe.simple_atlas.mixin;

import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MapItemSavedData.HoldingPlayer.class)
public interface HoldingPlayerAccessor {
    @Accessor("dirtyData")
    boolean simple_atlas$getDirtyData();

    @Accessor("dirtyData")
    void simple_atlas$setDirtyData(boolean dirtyData);

    @Accessor("dirtyDecorations")
    boolean simple_atlas$getDirtyDecorations();

    @Accessor("dirtyDecorations")
    void simple_atlas$setDirtyDecorations(boolean dirtyDecorations);
}
