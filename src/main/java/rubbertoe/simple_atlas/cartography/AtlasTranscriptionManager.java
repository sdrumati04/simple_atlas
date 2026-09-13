package rubbertoe.simple_atlas.cartography;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;
import rubbertoe.simple_atlas.compat.MapModCompat;
import rubbertoe.simple_atlas.component.AtlasContents;
import rubbertoe.simple_atlas.component.ModComponents;
import rubbertoe.simple_atlas.item.ModItems;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AtlasTranscriptionManager {
    private static final List<TranscriptionJob> ACTIVE_JOBS = new CopyOnWriteArrayList<>();
    private static final long TICK_BUDGET_NANOS = 4_000_000L; // 4 milliseconds budget per tick

    private AtlasTranscriptionManager() {}

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(AtlasTranscriptionManager::tick);
    }

    public static final class MapScanTask {
        final ServerLevel level;
        final MapId mapId;
        final MapItemSavedData childData;
        final boolean[] coverageOnChild;
        final @Nullable ArrayList<Integer> childRem;
        int currentX = 0;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        final BlockPos.MutableBlockPos fluidPos = new BlockPos.MutableBlockPos();

        public MapScanTask(
                ServerLevel level,
                MapId mapId,
                MapItemSavedData childData,
                boolean[] coverageOnChild,
                @Nullable ArrayList<Integer> childRem
        ) {
            this.level = level;
            this.mapId = mapId;
            this.childData = childData;
            this.coverageOnChild = coverageOnChild;
            this.childRem = childRem;
        }

        public boolean step(long budgetEndNanoTime) {
            while (currentX < AtlasCartographyScaler.MAP_SIZE) {
                AtlasCartographyScaler.scanColumnForScaledMap(
                        level,
                        childData,
                        coverageOnChild,
                        childRem,
                        currentX,
                        pos,
                        fluidPos
                );
                currentX++;
                if (System.nanoTime() >= budgetEndNanoTime) {
                    return false;
                }
            }

            childData.setDirty();
            if (childRem != null) {
                MapModCompat.setRemappedColors(childData, childRem);
            }
            return true;
        }
    }

    public static final class TranscriptionJob {
        final @Nullable UUID playerId;
        final Set<Integer> mapIds;
        final List<MapScanTask> tasks;
        final int totalColumns;
        int completedColumns = 0;
        int currentTaskIndex = 0;

        public TranscriptionJob(@Nullable UUID playerId, Set<Integer> mapIds, List<MapScanTask> tasks) {
            this.playerId = playerId;
            this.mapIds = Set.copyOf(mapIds);
            this.tasks = tasks;
            this.totalColumns = tasks.size() * AtlasCartographyScaler.MAP_SIZE;
        }

        public int getProgressPercent() {
            if (totalColumns == 0) {
                return 100;
            }
            return Math.min(100, (int) Math.round((double) completedColumns / totalColumns * 100.0));
        }

        public boolean isDone() {
            return currentTaskIndex >= tasks.size();
        }
    }

    public static void startJob(
            @Nullable ServerPlayer player,
            Set<Integer> newMapIds,
            List<MapScanTask> tasks
    ) {
        if (tasks.isEmpty()) {
            return;
        }

        TranscriptionJob job = new TranscriptionJob(
                player != null ? player.getUUID() : null,
                newMapIds,
                tasks
        );
        ACTIVE_JOBS.add(job);
    }

    public static boolean isTranscribing(AtlasContents contents) {
        if (!contents.transcribing()) {
            return false;
        }
        for (TranscriptionJob job : ACTIVE_JOBS) {
            for (int id : contents.mapIds()) {
                if (job.mapIds.contains(id)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static int getProgress(AtlasContents contents) {
        for (TranscriptionJob job : ACTIVE_JOBS) {
            for (int id : contents.mapIds()) {
                if (job.mapIds.contains(id)) {
                    return job.getProgressPercent();
                }
            }
        }
        return 100;
    }

    private static void tick(MinecraftServer server) {
        if (ACTIVE_JOBS.isEmpty()) {
            return;
        }

        long budgetEndNanoTime = System.nanoTime() + TICK_BUDGET_NANOS;

        for (TranscriptionJob job : ACTIVE_JOBS) {
            while (!job.isDone() && System.nanoTime() < budgetEndNanoTime) {
                MapScanTask task = job.tasks.get(job.currentTaskIndex);
                int xBefore = task.currentX;
                boolean taskDone = task.step(budgetEndNanoTime);
                job.completedColumns += (task.currentX - xBefore);

                if (taskDone) {
                    job.currentTaskIndex++;
                    if (job.playerId != null) {
                        ServerPlayer p = server.getPlayerList().getPlayer(job.playerId);
                        if (p != null) {
                            AtlasCartographyScaler.sendMapSyncPacket(p, task.mapId, task.childData);
                        }
                    }
                }
            }

            if (job.isDone()) {
                ACTIVE_JOBS.remove(job);
                onJobCompleted(server, job);
            }

            if (System.nanoTime() >= budgetEndNanoTime) {
                break;
            }
        }
    }

    private static void onJobCompleted(MinecraftServer server, TranscriptionJob job) {
        if (job.playerId == null) {
            return;
        }

        ServerPlayer player = server.getPlayerList().getPlayer(job.playerId);
        if (player != null) {
            player.level().playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,
                    SoundSource.PLAYERS,
                    1.0f,
                    1.2f
            );
            player.sendOverlayMessage(
                    Component.translatable("message.simple_atlas.transcription_complete")
            );

            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.is(ModItems.ATLAS)) {
                    AtlasContents c = stack.getOrDefault(ModComponents.ATLAS_CONTENTS, AtlasContents.EMPTY);
                    if (c.transcribing() && c.mapIds().stream().anyMatch(job.mapIds::contains)) {
                        stack.set(ModComponents.ATLAS_CONTENTS, c.withTranscribing(false));
                    }
                }
            }
        }
    }
}
