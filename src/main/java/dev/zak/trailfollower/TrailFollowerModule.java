package dev.zak.trailfollower;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalXZ;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.rusherhack.client.api.events.client.EventUpdate;
import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.client.api.utils.ChatUtils;
import org.rusherhack.core.event.subscribe.Subscribe;
import org.rusherhack.core.setting.BooleanSetting;
import org.rusherhack.core.setting.EnumSetting;
import org.rusherhack.core.setting.NullSetting;
import org.rusherhack.core.setting.NumberSetting;
import org.rusherhack.core.setting.Setting;
import xaeroplus.XaeroPlus;
import xaeroplus.event.ChunkDataEvent;
import xaeroplus.feature.extensions.SeenChunksTrackingMapTileChunk;
import xaeroplus.module.ModuleManager;
import xaeroplus.module.impl.OldChunks;
import xaeroplus.module.impl.PaletteNewChunks;
import xaeroplus.module.impl.PortalSkipDetection;
import xaeroplus.util.ChunkUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TrailFollowerModule extends ToggleableModule {

    private final EnumSetting<FollowMode> followMode = new EnumSetting<>(
        "FollowMode",
        "Chooses how TrailFollower moves after finding a trail.",
        FollowMode.AUTO
    );

    private final BooleanSetting ignoreSeenChunks = new BooleanSetting(
        "IgnoreSeenChunks",
        "Ignores chunks XaeroPlus already considers seen.",
        true
    );

    private final BooleanSetting yawLock = new BooleanSetting(
        "YawLock",
        "Turns your yaw toward the detected trail direction in overworld mode.",
        false
    );

    private final BooleanSetting autoStartBaritoneElytra = new BooleanSetting(
        "AutoStartBaritoneElytra",
        "Runs Baritone elytra when Nether Baritone mode has a trail.",
        true
    );

    private final BooleanSetting only112 = new BooleanSetting(
        "Only112",
        "Only follows chunks XaeroPlus marks as 1.12 old chunks.",
        false
    );

    private final BooleanSetting printStatus = new BooleanSetting(
        "PrintStatus",
        "Prints trail status messages.",
        true
    );

    private final NullSetting trailSettings = new NullSetting(
        "TrailSettings",
        "Trail detection settings."
    );

    private final NullSetting movementSettings = new NullSetting(
        "MovementSettings",
        "Movement settings."
    );

    private final NullSetting baritoneSettings = new NullSetting(
        "BaritoneSettings",
        "Nether Baritone settings."
    );

    private final NullSetting portalLandingSettings = new NullSetting(
        "PortalLandingSettings",
        "Portal skip landing settings."
    );

    private final BooleanSetting portalLanding = new BooleanSetting(
        "PortalLanding",
        "Targets the center of a XaeroPlus portal skip area while following a trail.",
        false
    );

    private final BooleanSetting portalLandingOnlyWhileFollowing = new BooleanSetting(
        "PortalLandingOnlyWhileFollowing",
        "Only detects portal skip areas after a trail has been found.",
        true
    );

    private final BooleanSetting portalSeenMapFallback = new BooleanSetting(
        "PortalSeenMapFallback",
        "Also checks Xaero's seen map directly if PortalSkipDetection has not highlighted an area.",
        false
    );

    private final EnumSetting<PortalArrivalAction> portalArrivalAction = new EnumSetting<>(
        "PortalArrivalAction",
        "What to do when the portal landing target is reached.",
        PortalArrivalAction.DISABLE
    );

    private final NumberSetting<Integer> portalAreaSizeChunks = new NumberSetting<>(
        "PortalAreaSizeChunks",
        15,
        3,
        31
    );

    private final NumberSetting<Integer> portalScanRadiusChunks = new NumberSetting<>(
        "PortalScanRadiusChunks",
        64,
        16,
        160
    );

    private final NumberSetting<Integer> portalScanCooldownTicks = new NumberSetting<>(
        "PortalScanCooldownTicks",
        40,
        5,
        200
    );

    private final NumberSetting<Integer> portalClearRadiusChunks = new NumberSetting<>(
        "PortalClearRadiusChunks",
        32,
        0,
        160
    );

    private final NumberSetting<Double> portalArrivalDistance = new NumberSetting<>(
        "PortalArrivalDistance",
        48.0,
        8.0,
        256.0
    );

    private final BooleanSetting baritoneRecovery = new BooleanSetting(
    "BaritoneRecovery",
    "Uses a longer locked goal when Baritone gets too close to its current goal.",
    true
);

private final NumberSetting<Double> recoveryDistanceMultiplier = new NumberSetting<>(
    "RecoveryDistanceMultiplier",
    2.0,
    1.0,
    5.0
);

private final NumberSetting<Double> minimumGoalDistance = new NumberSetting<>(
    "MinimumGoalDistance",
    160.0,
    50.0,
    1500.0
);

private final NumberSetting<Double> recoveryReleaseDistance = new NumberSetting<>(
    "RecoveryReleaseDistance",
    275.0,
    100.0,
    2000.0
);

private final NumberSetting<Integer> recoveryLockSeconds = new NumberSetting<>(
    "RecoveryLockSeconds",
    10,
    5,
    120
);

    private final NumberSetting<Double> startDirectionWeighting = new NumberSetting<>(
        "StartDirectionBias",
        0.5,
        0.0,
        1.0
    ).incremental(0.1);

    private final BooleanSetting startNetherForwardGoal = new BooleanSetting(
        "StartNetherForwardGoal",
        "Starts Baritone forward when enabled in the Nether while using auto mode.",
        true
    );

    private final EnumSetting<TrailEndBehavior> trailEndBehavior = new EnumSetting<>(
        "TrailEndBehavior",
        "What to do when the trail times out.",
        TrailEndBehavior.DISABLE
    );

    private final NumberSetting<Integer> maxTrailLength = new NumberSetting<>(
        "MaxTrailLength",
        20,
        1,
        100
    );

    private final NumberSetting<Integer> chunksBeforeStarting = new NumberSetting<>(
        "ChunksBeforeStarting",
        10,
        1,
        50
    );

    private final NumberSetting<Integer> chunkConsiderationWindowSeconds = new NumberSetting<>(
        "ChunkWindowSeconds",
        5,
        1,
        20
    );

    private final NumberSetting<Integer> chunkFoundTimeoutSeconds = new NumberSetting<>(
        "ChunkFoundTimeoutSeconds",
        5,
        1,
        20
    );

    private final NumberSetting<Integer> trailTimeoutSeconds = new NumberSetting<>(
        "TrailTimeoutSeconds",
        30,
        10,
        120
    );

private final NumberSetting<Integer> baritoneUpdateTicks = new NumberSetting<>(
    "BaritoneUpdateTicks",
    80,
    20,
    600
);

    private final NumberSetting<Double> pathDistance = new NumberSetting<>(
    "PathDistance",
    250.0,
    100.0,
    2000.0
);

    private final NumberSetting<Double> rotateScaling = new NumberSetting<>(
        "RotateScaling",
        0.1,
        0.0,
        1.0
    );

    private final NumberSetting<Double> maxTrailDeviation = new NumberSetting<>(
        "MaxTrailDeviation",
        180.0,
        1.0,
        270.0
    );

    private boolean xaeroRegistered = false;
    private boolean followingTrail = false;
    private boolean elytraStarted = false;

    private final ArrayDeque<Vec3> possibleTrail = new ArrayDeque<>();
    private final ArrayDeque<Vec3> trail = new ArrayDeque<>();
    private final Set<TrailChunkKey> seenTrailChunks = new HashSet<>();
    private final List<PortalClearedArea> clearedPortalAreas = new ArrayList<>();

    private Vec3 lastBaritoneTarget = null;

private boolean baritoneRecoveryMode = false;
private Vec3 recoveryTarget = null;
private long lastRecoveryStartTime = 0L;
private PortalLandingTarget portalLandingTarget = null;
private boolean portalLandingArrived = false;
private StopReason lastStopReason = StopReason.NONE;
private StopReason pendingDisableReason = StopReason.NONE;

    private long lastFoundTrailTime = 0L;
    private long lastFoundPossibleTrailTime = 0L;
    private long trailStartTime = 0L;

    private double targetYaw = 0.0;
    private double startForwardYaw = 0.0;

    private int secondTimer = 0;
    private int validChunksThisSecond = 0;
    private int lastValidChunksPerSecond = 0;
    private int baritoneGoalTimer = 0;
    private int portalScanTimer = 0;

    public TrailFollowerModule() {
        super("TrailFollower", "Follows XaeroPlus inverse-new chunk trails.", ModuleCategory.CLIENT);

        applyDisplayNames();

        trailSettings.addSubSettings(
            maxTrailLength,
            chunksBeforeStarting,
            chunkConsiderationWindowSeconds,
            chunkFoundTimeoutSeconds,
            trailTimeoutSeconds,
            maxTrailDeviation,
            startDirectionWeighting,
            only112,
            ignoreSeenChunks
        );

        movementSettings.addSubSettings(
            followMode,
            yawLock,
            rotateScaling,
            trailEndBehavior
        );

        baritoneSettings.addSubSettings(
    autoStartBaritoneElytra,
    startNetherForwardGoal,
    baritoneRecovery,
    baritoneUpdateTicks,
    pathDistance,
    minimumGoalDistance,
    recoveryDistanceMultiplier,
    recoveryReleaseDistance,
    recoveryLockSeconds
);

        portalLandingSettings.addSubSettings(
            portalLanding,
            portalLandingOnlyWhileFollowing,
            portalSeenMapFallback,
            portalAreaSizeChunks,
            portalScanRadiusChunks,
            portalScanCooldownTicks,
            portalClearRadiusChunks,
            portalArrivalDistance,
            portalArrivalAction
        );

        this.registerSettings(
            trailSettings,
            movementSettings,
            baritoneSettings,
            portalLandingSettings,
            printStatus
        );
    }

    private void applyDisplayNames() {
        rename(trailSettings, "Trail");
        rename(movementSettings, "Move");
        rename(baritoneSettings, "Baritone");
        rename(portalLandingSettings, "Portal");
        rename(printStatus, "ChatStatus");

        rename(maxTrailLength, "TrailLen");
        rename(chunksBeforeStarting, "StartChunks");
        rename(chunkConsiderationWindowSeconds, "ChunkWindow");
        rename(chunkFoundTimeoutSeconds, "LostAfter");
        rename(trailTimeoutSeconds, "AbandonAfter");
        rename(maxTrailDeviation, "MaxDeviation");
        rename(startDirectionWeighting, "StartBias");
        rename(only112, "1.12Only");
        rename(ignoreSeenChunks, "IgnoreSeen");

        rename(followMode, "Mode");
        rename(yawLock, "YawLock");
        rename(rotateScaling, "Rotate");
        rename(trailEndBehavior, "TrailEnd");

        rename(autoStartBaritoneElytra, "AutoElytra");
        rename(startNetherForwardGoal, "StartForward");
        rename(baritoneRecovery, "Recovery");
        rename(baritoneUpdateTicks, "UpdateTicks");
        rename(pathDistance, "PathDist");
        rename(minimumGoalDistance, "MinGoalDist");
        rename(recoveryDistanceMultiplier, "RecoveryMult");
        rename(recoveryReleaseDistance, "ReleaseDist");
        rename(recoveryLockSeconds, "LockSecs");

        rename(portalLanding, "Enabled");
        rename(portalLandingOnlyWhileFollowing, "OnlyTrail");
        rename(portalSeenMapFallback, "SeenFallback");
        rename(portalAreaSizeChunks, "Area");
        rename(portalScanRadiusChunks, "ScanRadius");
        rename(portalScanCooldownTicks, "ScanTicks");
        rename(portalClearRadiusChunks, "ClearRadius");
        rename(portalArrivalDistance, "ArriveDist");
        rename(portalArrivalAction, "ArriveAction");
    }

    private void rename(Setting<?> setting, String displayName) {
        setting.setDisplayName(displayName);
    }

    @Override
    public void onEnable() {
        clearStopReason();
        pendingDisableReason = StopReason.NONE;
        resetTrail();
        registerXaeroListener();

        if (mc.player != null && mc.level != null) {
            targetYaw = getActualYaw(mc.player.getYRot());

            if (!ensureFlightSupplies()) {
                return;
            }

            if (getActiveFollowMode() == ActiveFollowMode.NETHER_BARITONE && startNetherForwardGoal.getValue()) {
                startForwardBaritoneGoal();
            }
        }

        log("TrailFollower enabled. mode=" + getActiveFollowMode());
    }

    @Override
    public void onDisable() {
        StopReason stopReason = pendingDisableReason;
        pendingDisableReason = StopReason.NONE;

        unregisterXaeroListener();

        if (isSoftStopReason(stopReason)) {
            clearBaritoneTracking();
        } else {
            stopBaritoneGoal();
        }

        resetTrail();

        if (isSoftStopReason(stopReason)) {
            setStopReason(stopReason);
            log(getStopLogMessage(stopReason));
            return;
        }

        clearStopReason();
        log("TrailFollower disabled.");
    }

    @Subscribe
    private void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.level == null) {
            return;
        }

        if (!ensureFlightSupplies()) {
            return;
        }

        if (portalScanTimer > 0) {
            portalScanTimer--;
        }

        secondTimer++;

        if (secondTimer >= 20) {
            secondTimer = 0;

            lastValidChunksPerSecond = validChunksThisSecond;

            if (validChunksThisSecond > 0) {
                log("validTrail/sec=" + validChunksThisSecond + " following=" + followingTrail + " yaw=" + Math.round(targetYaw));
                validChunksThisSecond = 0;
            }
        }

        if (portalLandingTarget != null) {
            updatePortalLandingTarget();
            return;
        }

        tryDetectPortalLanding(mc.player.chunkPosition(), mc.level.dimension());

        if (!followingTrail) {
            return;
        }

        long now = System.currentTimeMillis();

        if (now - lastFoundTrailTime > trailTimeoutSeconds.getValue() * 1000L) {
            log("Trail timed out.");
            handleTrailEnd();
            return;
        }

        if (now - lastFoundTrailTime > chunkFoundTimeoutSeconds.getValue() * 1000L) {
            circle();
            return;
        }

        ActiveFollowMode activeFollowMode = getActiveFollowMode();

        if (activeFollowMode == ActiveFollowMode.NETHER_BARITONE) {
            updateBaritoneGoal();
            return;
        }

        if (activeFollowMode == ActiveFollowMode.OVERWORLD_YAW && yawLock.getValue()) {
            float currentYaw = getActualYaw(mc.player.getYRot());
            float nextYaw = smoothRotation(currentYaw, (float) targetYaw, rotateScaling.getValue());
            mc.player.setYRot(nextYaw);
        }
    }

    @net.lenni0451.lambdaevents.EventHandler(priority = -1)
    public void onChunkData(ChunkDataEvent event) {
        if (mc.player == null || mc.level == null) {
            return;
        }

        if (ignoreSeenChunks.getValue() && event.seenChunk()) {
            return;
        }

        LevelChunk chunk = event.chunk();

        if (chunk == null) {
            return;
        }

        ChunkPos chunkPos = chunk.getPos();

        ResourceKey<Level> dimension = mc.level.dimension();
        TrailChunkKey trailChunkKey = new TrailChunkKey(dimension, chunkPos.toLong());

        if (seenTrailChunks.contains(trailChunkKey)) {
            return;
        }

        if (!isValidTrailChunk(chunkPos, dimension)) {
            return;
        }

        seenTrailChunks.add(trailChunkKey);
        validChunksThisSecond++;

        Vec3 chunkCenter = new Vec3(
            chunkPos.getMiddleBlockX(),
            mc.player.getY(),
            chunkPos.getMiddleBlockZ()
        );

        if (!followingTrail) {
            handlePossibleTrailChunk(chunkCenter);
            return;
        }

        handleConfirmedTrailChunk(chunkCenter);
    }

    private void handlePossibleTrailChunk(Vec3 chunkCenter) {
        clearStopReason();

        long now = System.currentTimeMillis();

        if (now - lastFoundPossibleTrailTime > chunkConsiderationWindowSeconds.getValue() * 1000L) {
            possibleTrail.clear();
        }

        possibleTrail.add(chunkCenter);
        lastFoundPossibleTrailTime = now;

        if (possibleTrail.size() < chunksBeforeStarting.getValue()) {
            return;
        }

        followingTrail = true;
        lastFoundTrailTime = now;
        trailStartTime = now;
        trail.clear();
        appendStartDirectionBias(trail);
        trail.addAll(possibleTrail);
        possibleTrail.clear();

        updateTargetYaw();
        log("Trail found. Starting follow. mode=" + getActiveFollowMode() + " points=" + trail.size() + " yaw=" + Math.round(targetYaw));
    }

    private void handleConfirmedTrailChunk(Vec3 chunkCenter) {
        clearStopReason();

        double chunkYaw = getYawTo(chunkCenter);
        double angleDifference = angleDifference(targetYaw, chunkYaw);

        if (Math.abs(angleDifference) > maxTrailDeviation.getValue()) {
            return;
        }

        lastFoundTrailTime = System.currentTimeMillis();

        while (trail.size() >= maxTrailLength.getValue()) {
            trail.pollFirst();
        }

        trail.add(chunkCenter);
        updateTargetYaw();
    }

    private void updateTargetYaw() {
        if (trail.isEmpty()) {
            return;
        }

        Vec3 average = calculateAveragePosition(trail);
        Vec3 direction = average.subtract(mc.player.position());

        if (direction.lengthSqr() < 0.001) {
            return;
        }

        Vec3 target = mc.player.position().add(direction.normalize().scale(10.0));
        targetYaw = getYawTo(target);
    }

    private void updateBaritoneGoal() {
        if (mc.player == null || trail.isEmpty()) {
            return;
        }

        if (baritoneGoalTimer > 0) {
            baritoneGoalTimer--;
            return;
        }

        baritoneGoalTimer = baritoneUpdateTicks.getValue();

        if (baritoneRecovery.getValue() && baritoneRecoveryMode) {
            if (shouldReleaseRecoveryGoal()) {
                baritoneRecoveryMode = false;
                recoveryTarget = null;
                log("baritone recovery released");
            } else {
                startBaritoneElytraIfNeeded();
                return;
            }
        }

        Vec3 target = getProjectedTrailTarget();

        if (target == null) {
            return;
        }

        double distanceToTarget = mc.player.position().distanceTo(target);

        if (baritoneRecovery.getValue() && distanceToTarget < minimumGoalDistance.getValue()) {
            Vec3 recovery = getProjectedTrailTarget(pathDistance.getValue() * recoveryDistanceMultiplier.getValue());

            if (recovery == null) {
                return;
            }

            baritoneRecoveryMode = true;
            recoveryTarget = recovery;
            lastRecoveryStartTime = System.currentTimeMillis();

            setBaritoneGoal(recovery);
            startBaritoneElytraIfNeeded();

            log("baritone recovery goal set");
            return;
    }

    if (lastBaritoneTarget != null) {
        double distance = lastBaritoneTarget.distanceTo(target);

        if (distance < 128.0) {
            startBaritoneElytraIfNeeded();
            return;
        }
    }

    setBaritoneGoal(target);
    startBaritoneElytraIfNeeded();
}

    private void tryDetectPortalLanding(ChunkPos centerChunk, ResourceKey<Level> dimension) {
        if (!portalLanding.getValue()) {
            return;
        }

        if (portalLandingOnlyWhileFollowing.getValue() && !followingTrail) {
            return;
        }

        if (portalScanTimer > 0) {
            return;
        }

        portalScanTimer = portalScanCooldownTicks.getValue();

        PortalSkipDetection portalSkipDetection = getPortalSkipDetection();

        if (portalSkipDetection == null && !portalSeenMapFallback.getValue()) {
            return;
        }

        PortalLandingTarget target = findPortalLandingTarget(portalSkipDetection, centerChunk, dimension);

        if (target == null) {
            return;
        }

        activatePortalLandingTarget(target, dimension);
    }

    private PortalSkipDetection getPortalSkipDetection() {
        try {
            return ModuleManager.getModule(PortalSkipDetection.class);
        } catch (Throwable throwable) {
            return null;
        }
    }

    private PortalLandingTarget findPortalLandingTarget(PortalSkipDetection portalSkipDetection, ChunkPos centerChunk, ResourceKey<Level> dimension) {
        int scanRadius = portalScanRadiusChunks.getValue();
        int minX = centerChunk.x - scanRadius;
        int maxX = centerChunk.x + scanRadius;
        int minZ = centerChunk.z - scanRadius;
        int maxZ = centerChunk.z + scanRadius;
        int requiredChunks = portalAreaSizeChunks.getValue() * portalAreaSizeChunks.getValue();

        Set<Long> visited = new HashSet<>();
        Map<Long, Boolean> portalCandidateCache = new HashMap<>();
        PortalLandingTarget bestTarget = null;
        double bestDistance = Double.MAX_VALUE;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                long key = packChunk(x, z);

                if (visited.contains(key)) {
                    continue;
                }

                if (!isPortalCandidateChunk(portalSkipDetection, x, z, dimension, portalCandidateCache)) {
                    continue;
                }

                PortalLandingTarget candidate = collectPortalSkipArea(
                    portalSkipDetection,
                    x,
                    z,
                    dimension,
                    visited,
                    portalCandidateCache,
                    minX,
                    maxX,
                    minZ,
                    maxZ
                );

                if (candidate.chunkCount() < requiredChunks) {
                    continue;
                }

                double distance = horizontalDistance(mc.player.position(), candidate.center());

                if (bestTarget == null || candidate.chunkCount() > bestTarget.chunkCount() || (candidate.chunkCount() == bestTarget.chunkCount() && distance < bestDistance)) {
                    bestTarget = candidate;
                    bestDistance = distance;
                }
            }
        }

        return bestTarget;
    }

    private PortalLandingTarget collectPortalSkipArea(
        PortalSkipDetection portalSkipDetection,
        int startX,
        int startZ,
        ResourceKey<Level> dimension,
        Set<Long> visited,
        Map<Long, Boolean> portalCandidateCache,
        int minLimitX,
        int maxLimitX,
        int minLimitZ,
        int maxLimitZ
    ) {
        ArrayDeque<ChunkCoord> queue = new ArrayDeque<>();
        List<ChunkCoord> chunks = new ArrayList<>();

        queue.add(new ChunkCoord(startX, startZ));
        visited.add(packChunk(startX, startZ));

        while (!queue.isEmpty()) {
            ChunkCoord chunk = queue.poll();
            chunks.add(chunk);

            addPortalNeighbor(portalSkipDetection, dimension, visited, portalCandidateCache, queue, chunk.x() + 1, chunk.z(), minLimitX, maxLimitX, minLimitZ, maxLimitZ);
            addPortalNeighbor(portalSkipDetection, dimension, visited, portalCandidateCache, queue, chunk.x() - 1, chunk.z(), minLimitX, maxLimitX, minLimitZ, maxLimitZ);
            addPortalNeighbor(portalSkipDetection, dimension, visited, portalCandidateCache, queue, chunk.x(), chunk.z() + 1, minLimitX, maxLimitX, minLimitZ, maxLimitZ);
            addPortalNeighbor(portalSkipDetection, dimension, visited, portalCandidateCache, queue, chunk.x(), chunk.z() - 1, minLimitX, maxLimitX, minLimitZ, maxLimitZ);
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (ChunkCoord chunk : chunks) {
            minX = Math.min(minX, chunk.x());
            maxX = Math.max(maxX, chunk.x());
            minZ = Math.min(minZ, chunk.z());
            maxZ = Math.max(maxZ, chunk.z());
        }

        double centerX = ((double) minX + maxX + 1.0) * 8.0;
        double centerZ = ((double) minZ + maxZ + 1.0) * 8.0;
        Vec3 center = new Vec3(centerX, mc.player.getY(), centerZ);

        return new PortalLandingTarget(center, minX, minZ, maxX, maxZ, chunks.size());
    }

    private void addPortalNeighbor(
        PortalSkipDetection portalSkipDetection,
        ResourceKey<Level> dimension,
        Set<Long> visited,
        Map<Long, Boolean> portalCandidateCache,
        ArrayDeque<ChunkCoord> queue,
        int x,
        int z,
        int minLimitX,
        int maxLimitX,
        int minLimitZ,
        int maxLimitZ
    ) {
        if (x < minLimitX || x > maxLimitX || z < minLimitZ || z > maxLimitZ) {
            return;
        }

        long key = packChunk(x, z);

        if (visited.contains(key)) {
            return;
        }

        visited.add(key);

        if (isPortalCandidateChunk(portalSkipDetection, x, z, dimension, portalCandidateCache)) {
            queue.add(new ChunkCoord(x, z));
        }
    }

    private boolean isPortalCandidateChunk(PortalSkipDetection portalSkipDetection, int x, int z, ResourceKey<Level> dimension, Map<Long, Boolean> portalCandidateCache) {
        long key = packChunk(x, z);
        Boolean cached = portalCandidateCache.get(key);

        if (cached != null) {
            return cached;
        }

        if (isClearedPortalChunk(x, z, dimension)) {
            portalCandidateCache.put(key, false);
            return false;
        }

        boolean candidate = false;

        if (portalSkipDetection != null) {
            try {
                candidate = portalSkipDetection.isPortalSkipChunk(x, z, dimension);
            } catch (Throwable ignored) {
            }
        }

        if (!candidate && portalSeenMapFallback.getValue()) {
            candidate = isSeenMapChunk(x, z);
        }

        portalCandidateCache.put(key, candidate);
        return candidate;
    }

    private boolean isClearedPortalChunk(int x, int z, ResourceKey<Level> dimension) {
        for (PortalClearedArea area : clearedPortalAreas) {
            if (!area.dimension().equals(dimension)) {
                continue;
            }

            if (x >= area.minChunkX() && x <= area.maxChunkX() && z >= area.minChunkZ() && z <= area.maxChunkZ()) {
                return true;
            }
        }

        return false;
    }

    private boolean isSeenMapChunk(int chunkX, int chunkZ) {
        try {
            Class<?> coreClass = Class.forName("xaero.map.core.XaeroWorldMapCore");
            Object session = coreClass.getField("currentSession").get(null);

            if (session == null) {
                return false;
            }

            Object mapProcessor = session.getClass().getMethod("getMapProcessor").invoke(session);

            if (mapProcessor == null) {
                return false;
            }

            int caveLayer = (Integer) mapProcessor.getClass().getMethod("getCurrentCaveLayer").invoke(mapProcessor);
            Object region = mapProcessor.getClass()
                .getMethod("getLeafMapRegion", int.class, int.class, int.class, boolean.class)
                .invoke(
                    mapProcessor,
                    caveLayer,
                    ChunkUtils.chunkCoordToMapRegionCoord(chunkX),
                    ChunkUtils.chunkCoordToMapRegionCoord(chunkZ),
                    false
                );

            if (region == null) {
                return false;
            }

            Object tileChunk = region.getClass()
                .getMethod("getChunk", int.class, int.class)
                .invoke(
                    region,
                    ChunkUtils.chunkCoordToMapTileChunkCoordLocal(chunkX),
                    ChunkUtils.chunkCoordToMapTileChunkCoordLocal(chunkZ)
                );

            if (!(tileChunk instanceof SeenChunksTrackingMapTileChunk seenChunk)) {
                return false;
            }

            boolean[][] seenTiles = seenChunk.getSeenTiles();
            return seenTiles[ChunkUtils.chunkCoordToMapTileCoordLocal(chunkX)][ChunkUtils.chunkCoordToMapTileCoordLocal(chunkZ)];
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void activatePortalLandingTarget(PortalLandingTarget target, ResourceKey<Level> dimension) {
        clearStopReason();
        clearPortalArea(target, dimension);
        portalLandingTarget = target;
        portalLandingArrived = false;
        followingTrail = false;
        possibleTrail.clear();
        baritoneRecoveryMode = false;
        recoveryTarget = null;
        baritoneGoalTimer = 0;
        targetYaw = getYawTo(target.center());

        if (getActiveFollowMode() == ActiveFollowMode.NETHER_BARITONE) {
            setBaritoneGoal(target.center());
            startBaritoneElytraIfNeeded();
        }

        log("Portal skip area detected. Targeting center " + formatBlockPos(target.center()) + " area=" + target.widthChunks() + "x" + target.depthChunks() + " clearedRadius=" + portalClearRadiusChunks.getValue());
    }

    private void clearPortalArea(PortalLandingTarget target, ResourceKey<Level> dimension) {
        int radius = portalClearRadiusChunks.getValue();
        clearedPortalAreas.add(new PortalClearedArea(
            dimension,
            target.minChunkX() - radius,
            target.minChunkZ() - radius,
            target.maxChunkX() + radius,
            target.maxChunkZ() + radius
        ));
    }

    private void updatePortalLandingTarget() {
        if (portalLandingTarget == null || portalLandingArrived || mc.player == null) {
            return;
        }

        targetYaw = getYawTo(portalLandingTarget.center());

        double distance = horizontalDistance(mc.player.position(), portalLandingTarget.center());

        if (distance <= portalArrivalDistance.getValue()) {
            log("Portal landing target reached at " + formatBlockPos(portalLandingTarget.center()) + ".");
            handlePortalArrival();
            return;
        }

        if (getActiveFollowMode() == ActiveFollowMode.NETHER_BARITONE) {
            if (baritoneGoalTimer > 0) {
                baritoneGoalTimer--;
                return;
            }

            baritoneGoalTimer = baritoneUpdateTicks.getValue();
            setBaritoneGoal(portalLandingTarget.center());
            startBaritoneElytraIfNeeded();
            return;
        }

        if (yawLock.getValue()) {
            float currentYaw = getActualYaw(mc.player.getYRot());
            float nextYaw = smoothRotation(currentYaw, (float) targetYaw, rotateScaling.getValue());
            mc.player.setYRot(nextYaw);
        }
    }

    private void handlePortalArrival() {
        portalLandingArrived = true;

        switch (portalArrivalAction.getValue()) {
            case DISABLE:
                pendingDisableReason = StopReason.PORTAL_SKIP;
                this.toggle();
                break;
            case STOP:
                clearBaritoneTracking();
                resetTrail();
                setStopReason(StopReason.PORTAL_SKIP);
                break;
            case KEEP_TARGET:
                clearBaritoneTracking();
                break;
        }
    }

    private double horizontalDistance(Vec3 first, Vec3 second) {
        return Math.hypot(first.x - second.x, first.z - second.z);
    }

    private long packChunk(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private String formatBlockPos(Vec3 position) {
        return Mth.floor(position.x) + ", " + Mth.floor(position.y) + ", " + Mth.floor(position.z);
    }

    private Vec3 getProjectedTrailTarget() {
    return getProjectedTrailTarget(pathDistance.getValue());
}

private Vec3 getProjectedTrailTarget(double distance) {
    Vec3 average = calculateAveragePosition(trail);
    Vec3 direction = average.subtract(mc.player.position());

    if (direction.lengthSqr() < 0.001) {
        return null;
    }

    return mc.player.position().add(direction.normalize().scale(distance));
}

    private boolean isValidTrailChunk(ChunkPos chunkPos, ResourceKey<Level> dimension) {
        PaletteNewChunks paletteNewChunks = ModuleManager.getModule(PaletteNewChunks.class);
        OldChunks oldChunks = ModuleManager.getModule(OldChunks.class);

        if (paletteNewChunks == null || oldChunks == null) {
            return false;
        }

        boolean isNewChunk = paletteNewChunks.isNewChunk(
            chunkPos.x,
            chunkPos.z,
            dimension
        );

        boolean isInverseNewChunk = paletteNewChunks.isInverseNewChunk(
            chunkPos.x,
            chunkPos.z,
            dimension
        );

        boolean isOldChunk = oldChunks.isOldChunk(
            chunkPos.x,
            chunkPos.z,
            dimension
        );

        boolean isHighlighted = isNewChunk || isInverseNewChunk;

        return isHighlighted && ((!isNewChunk && !only112.getValue()) || isOldChunk);
    }

    private boolean shouldReleaseRecoveryGoal() {
    if (mc.player == null || recoveryTarget == null) {
        return true;
    }

    long elapsed = System.currentTimeMillis() - lastRecoveryStartTime;

    if (elapsed < recoveryLockSeconds.getValue() * 1000L) {
        return false;
    }

    return mc.player.position().distanceTo(recoveryTarget) < recoveryReleaseDistance.getValue();
}

private void setBaritoneGoal(Vec3 target) {
    try {
        if (BaritoneAPI.getProvider() == null || BaritoneAPI.getProvider().getPrimaryBaritone() == null) {
            return;
        }

        BaritoneAPI.getProvider()
            .getPrimaryBaritone()
            .getCustomGoalProcess()
            .setGoalAndPath(new GoalXZ(Mth.floor(target.x), Mth.floor(target.z)));

        lastBaritoneTarget = target;
    } catch (Throwable throwable) {
        log("baritone goal update failed");
    }
}

    private ActiveFollowMode getActiveFollowMode() {
        if (followMode.getValue() == FollowMode.OVERWORLD_YAW) {
            return ActiveFollowMode.OVERWORLD_YAW;
        }

        if (followMode.getValue() == FollowMode.NETHER_BARITONE) {
            return ActiveFollowMode.NETHER_BARITONE;
        }

        if (mc.level != null && mc.level.dimension().equals(Level.NETHER)) {
            return ActiveFollowMode.NETHER_BARITONE;
        }

        return ActiveFollowMode.OVERWORLD_YAW;
    }

    private void executeBaritoneCommand(String command) {
        try {
            Object primaryBaritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            Object commandManager = primaryBaritone.getClass()
                .getMethod("getCommandManager")
                .invoke(primaryBaritone);

            commandManager.getClass()
                .getMethod("execute", String.class)
                .invoke(commandManager, command);
        } catch (Throwable throwable) {
            log("Failed to run Baritone command: " + command);
        }
    }

    private void startBaritoneElytraIfNeeded() {
        if (!autoStartBaritoneElytra.getValue()) {
            return;
        }

        if (elytraStarted) {
            return;
        }

        try {
            BaritoneAPI.getSettings().elytraTermsAccepted.value = true;
            executeBaritoneCommand("elytra");
            elytraStarted = true;
        } catch (Throwable throwable) {
            log("Failed to start Baritone elytra.");
        }
    }

    private void stopBaritoneGoal() {
        executeBaritoneCommand("stop");
        clearBaritoneTracking();
    }

    private void clearBaritoneTracking() {
        lastBaritoneTarget = null;
        baritoneRecoveryMode = false;
        recoveryTarget = null;
        baritoneGoalTimer = 0;
        elytraStarted = false;
    }

    private boolean ensureFlightSupplies() {
        if (getActiveFollowMode() != ActiveFollowMode.NETHER_BARITONE) {
            return true;
        }

        if (!hasUsableElytra()) {
            softDisable(StopReason.NO_ELYTRA);
            return false;
        }

        if (!hasFireworkRocket()) {
            softDisable(StopReason.NO_FIREWORKS);
            return false;
        }

        return true;
    }

    private void softDisable(StopReason stopReason) {
        pendingDisableReason = stopReason;

        if (isToggled()) {
            this.toggle();
            return;
        }

        setStopReason(stopReason);
    }

    private boolean hasFireworkRocket() {
        if (mc.player == null) {
            return false;
        }

        Inventory inventory = mc.player.getInventory();
        return containsItem(inventory.items, Items.FIREWORK_ROCKET)
            || containsItem(inventory.offhand, Items.FIREWORK_ROCKET);
    }

    private boolean containsItem(Iterable<ItemStack> stacks, net.minecraft.world.item.Item item) {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty() && stack.is(item)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasUsableElytra() {
        if (mc.player == null) {
            return false;
        }

        Inventory inventory = mc.player.getInventory();
        return isUsableElytra(mc.player.getItemBySlot(EquipmentSlot.CHEST))
            || containsUsableElytra(inventory.items)
            || containsUsableElytra(inventory.armor)
            || containsUsableElytra(inventory.offhand);
    }

    private boolean containsUsableElytra(Iterable<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (isUsableElytra(stack)) {
                return true;
            }
        }

        return false;
    }

    private boolean isUsableElytra(ItemStack stack) {
        return !stack.isEmpty()
            && stack.is(Items.ELYTRA)
            && (!stack.isDamageableItem() || stack.getDamageValue() < stack.getMaxDamage() - 1);
    }

    private void appendStartDirectionBias(ArrayDeque<Vec3> positions) {
        if (mc.player == null) {
            return;
        }

        int pointsToAdd = (int) Math.round(maxTrailLength.getValue() * startDirectionWeighting.getValue());

        if (pointsToAdd <= 0) {
            return;
        }

        Vec3 startTarget = positionInDirection(mc.player.position(), getActualYaw(mc.player.getYRot()), pathDistance.getValue());

        for (int i = 0; i < pointsToAdd; i++) {
            positions.add(startTarget);
        }
    }

    private void startForwardBaritoneGoal() {
        if (mc.player == null) {
            return;
        }

        startForwardYaw = getActualYaw(mc.player.getYRot());
        Vec3 target = positionInDirection(mc.player.position(), startForwardYaw, pathDistance.getValue());

        try {
            BaritoneAPI.getProvider()
                .getPrimaryBaritone()
                .getCustomGoalProcess()
                .setGoalAndPath(new GoalXZ(Mth.floor(target.x), Mth.floor(target.z)));

            lastBaritoneTarget = target;
        } catch (Throwable throwable) {
            log("Failed to start Nether Baritone goal.");
            return;
        }

        startBaritoneElytraIfNeeded();
        log("Started Nether Baritone goal forward.");
    }

    private Vec3 positionInDirection(Vec3 origin, double yaw, double distance) {
        double radians = Math.toRadians(yaw);
        double x = origin.x - Math.sin(radians) * distance;
        double z = origin.z + Math.cos(radians) * distance;

        return new Vec3(x, origin.y, z);
    }

    private void handleTrailEnd() {
        switch (trailEndBehavior.getValue()) {
            case DISABLE:
                this.toggle();
                break;
            case RESET:
                stopBaritoneGoal();
                resetTrail();
                break;
            case KEEP_YAW:
                stopBaritoneGoal();
                resetTrail();
                followingTrail = false;
                break;
        }
    }

    private void circle() {
        if (getActiveFollowMode() == ActiveFollowMode.NETHER_BARITONE) {
            return;
        }

        if (!yawLock.getValue()) {
            return;
        }

        mc.player.setYRot(getActualYaw(mc.player.getYRot() + 2.0F));
    }

    private Vec3 calculateAveragePosition(ArrayDeque<Vec3> positions) {
        double sumX = 0.0;
        double sumY = 0.0;
        double sumZ = 0.0;

        for (Vec3 position : positions) {
            sumX += position.x;
            sumY += position.y;
            sumZ += position.z;
        }

        double count = positions.size();

        return new Vec3(
            sumX / count,
            sumY / count,
            sumZ / count
        );
    }

    private double getYawTo(Vec3 target) {
        Vec3 playerPos = mc.player.position();

        double deltaX = target.x - playerPos.x;
        double deltaZ = target.z - playerPos.z;

        return getActualYaw((float) Math.toDegrees(Math.atan2(-deltaX, deltaZ)));
    }

    private double angleDifference(double first, double second) {
        double difference = (second - first) % 360.0;

        if (difference < -180.0) {
            difference += 360.0;
        }

        if (difference > 180.0) {
            difference -= 360.0;
        }

        return difference;
    }

    private float smoothRotation(float current, float target, double scaling) {
        double difference = angleDifference(current, target);
        return getActualYaw((float) (current + difference * scaling));
    }

    private float getActualYaw(float yaw) {
        return (yaw % 360.0F + 360.0F) % 360.0F;
    }

    private void resetTrail() {
    followingTrail = false;
    elytraStarted = false;
    baritoneRecoveryMode = false;
    recoveryTarget = null;
    portalLandingTarget = null;
    portalLandingArrived = false;
    possibleTrail.clear();
    trail.clear();
    seenTrailChunks.clear();
    validChunksThisSecond = 0;
    lastValidChunksPerSecond = 0;
    baritoneGoalTimer = 0;
    portalScanTimer = 0;
    lastBaritoneTarget = null;
    lastFoundTrailTime = 0L;
    lastFoundPossibleTrailTime = 0L;
    trailStartTime = 0L;
}

    private void registerXaeroListener() {
        if (xaeroRegistered) {
            return;
        }

        XaeroPlus.EVENT_BUS.register(this);
        xaeroRegistered = true;
    }

    private void unregisterXaeroListener() {
        if (!xaeroRegistered) {
            return;
        }

        XaeroPlus.EVENT_BUS.unregister(this);
        xaeroRegistered = false;
    }

    private void log(String message) {
        if (printStatus.getValue()) {
            ChatUtils.print(message);
        }
    }

    private void setStopReason(StopReason stopReason) {
        lastStopReason = stopReason;
    }

    private void clearStopReason() {
        lastStopReason = StopReason.NONE;
    }

    private boolean isSoftStopReason(StopReason stopReason) {
        return stopReason != StopReason.NONE;
    }

    private String getStopLogMessage(StopReason stopReason) {
        return switch (stopReason) {
            case PORTAL_SKIP -> "TrailFollower stopped after potential portal skip. Baritone landing left active.";
            case NO_ELYTRA -> "TrailFollower soft disabled: no usable elytra found. Baritone left active.";
            case NO_FIREWORKS -> "TrailFollower soft disabled: no fireworks found. Baritone left active.";
            case NONE -> "TrailFollower disabled.";
        };
    }

    public TrailFollowerHudStatus getHudStatus() {
        double playerYaw = 0.0;
        double baritoneTargetDistance = -1.0;
        double portalTargetDistance = -1.0;
        int portalAreaWidth = 0;
        int portalAreaDepth = 0;
        int portalAreaChunks = 0;

        if (mc.player != null) {
            playerYaw = getActualYaw(mc.player.getYRot());

            if (lastBaritoneTarget != null) {
                baritoneTargetDistance = mc.player.position().distanceTo(lastBaritoneTarget);
            }

            if (portalLandingTarget != null) {
                portalTargetDistance = horizontalDistance(mc.player.position(), portalLandingTarget.center());
            }
        }

        if (portalLandingTarget != null) {
            portalAreaWidth = portalLandingTarget.widthChunks();
            portalAreaDepth = portalLandingTarget.depthChunks();
            portalAreaChunks = portalLandingTarget.chunkCount();
        }

        return new TrailFollowerHudStatus(
            isToggled(),
            getHudState(),
            getHudMessage(),
            getModeLabel(),
            trail.size(),
            possibleTrail.size(),
            lastValidChunksPerSecond,
            getElapsedSeconds(lastFoundTrailTime),
            getElapsedSeconds(lastFoundPossibleTrailTime),
            getElapsedSeconds(trailStartTime),
            getAbandonCountdownSeconds(),
            isCircling(),
            targetYaw,
            playerYaw,
            baritoneRecoveryMode,
            baritoneTargetDistance,
            portalLandingTarget != null,
            portalLandingArrived,
            portalTargetDistance,
            portalAreaWidth,
            portalAreaDepth,
            portalAreaChunks
        );
    }

    private String getHudMessage() {
        if (lastStopReason != StopReason.NONE) {
            return switch (lastStopReason) {
                case PORTAL_SKIP -> "Stopped at potential portal skip. Place portal, cross, wait, return.";
                case NO_ELYTRA -> "Soft disabled: no usable elytra found. Baritone left active.";
                case NO_FIREWORKS -> "Soft disabled: no fireworks found. Baritone left active.";
                case NONE -> "TrailFollower is off.";
            };
        }

        if (!isToggled()) {
            return "TrailFollower is off.";
        }

        if (mc.player == null || mc.level == null) {
            return "Waiting for world.";
        }

        if (portalLandingTarget != null) {
            if (portalLandingArrived) {
                return "Arrived. Place portal, cross, wait, return.";
            }

            return "Portal skip found. Landing center.";
        }

        if (baritoneRecoveryMode) {
            return "Baritone recovery target locked.";
        }

        if (followingTrail) {
            int staleSeconds = getElapsedSeconds(lastFoundTrailTime);

            if (staleSeconds > chunkFoundTimeoutSeconds.getValue()) {
                int abandonSeconds = getAbandonCountdownSeconds();
                String action = getActiveFollowMode() == ActiveFollowMode.OVERWORLD_YAW && yawLock.getValue() ? "Circling" : "Holding target";
                return "Trail lost. " + action + ". Abandon in " + formatSeconds(abandonSeconds) + ".";
            }

            int followSeconds = getElapsedSeconds(trailStartTime);

            if (followSeconds >= 0 && followSeconds < 5) {
                return "Trail found. Locking direction.";
            }

            return "Following trail. Last hit " + formatSeconds(staleSeconds) + " ago.";
        }

        if (!possibleTrail.isEmpty()) {
            return "Trail candidate " + possibleTrail.size() + "/" + chunksBeforeStarting.getValue() + ".";
        }

        if (portalLanding.getValue()) {
            return "Scanning for trail and portal skips.";
        }

        return "Scanning for trail.";
    }

    private String getHudState() {
        if (lastStopReason != StopReason.NONE) {
            return switch (lastStopReason) {
                case PORTAL_SKIP -> "Portal Stop";
                case NO_ELYTRA -> "No Elytra";
                case NO_FIREWORKS -> "No Fireworks";
                case NONE -> "Off";
            };
        }

        if (!isToggled()) {
            return "Off";
        }

        if (mc.player == null || mc.level == null) {
            return "Waiting";
        }

        if (portalLandingTarget != null) {
            return portalLandingArrived ? "Arrived" : "Portal";
        }

        if (baritoneRecoveryMode) {
            return "Recovery";
        }

        if (followingTrail) {
            long age = System.currentTimeMillis() - lastFoundTrailTime;
            if (age > chunkFoundTimeoutSeconds.getValue() * 1000L) {
                return isCircling() ? "Circling" : "Lost";
            }

            return "Following";
        }

        if (!possibleTrail.isEmpty()) {
            return "Building";
        }

        return "Scanning";
    }

    private boolean isCircling() {
        return followingTrail
            && getActiveFollowMode() == ActiveFollowMode.OVERWORLD_YAW
            && yawLock.getValue()
            && getElapsedSeconds(lastFoundTrailTime) > chunkFoundTimeoutSeconds.getValue();
    }

    private String getModeLabel() {
        if (followMode.getValue() == FollowMode.AUTO && mc.level == null) {
            return "Auto";
        }

        return switch (getActiveFollowMode()) {
            case OVERWORLD_YAW -> "Overworld Yaw";
            case NETHER_BARITONE -> "Nether Baritone";
        };
    }

    private int getElapsedSeconds(long timestamp) {
        if (timestamp <= 0L) {
            return -1;
        }

        return (int) ((System.currentTimeMillis() - timestamp) / 1000L);
    }

    private int getAbandonCountdownSeconds() {
        int staleSeconds = getElapsedSeconds(lastFoundTrailTime);

        if (staleSeconds < 0) {
            return -1;
        }

        return Math.max(0, trailTimeoutSeconds.getValue() - staleSeconds);
    }

    private String formatSeconds(int seconds) {
        if (seconds < 0) {
            return "--";
        }

        return seconds + "s";
    }

    public record TrailFollowerHudStatus(
        boolean enabled,
        String state,
        String message,
        String mode,
        int trailPoints,
        int candidatePoints,
        int chunksPerSecond,
        int trailAgeSeconds,
        int candidateAgeSeconds,
        int followSeconds,
        int abandonCountdownSeconds,
        boolean circling,
        double targetYaw,
        double playerYaw,
        boolean baritoneRecovery,
        double baritoneTargetDistance,
        boolean portalLanding,
        boolean portalLandingArrived,
        double portalTargetDistance,
        int portalAreaWidth,
        int portalAreaDepth,
        int portalAreaChunks
    ) {}

    private record TrailChunkKey(ResourceKey<Level> dimension, long chunkPos) {}

    private record ChunkCoord(int x, int z) {}

    private record PortalLandingTarget(Vec3 center, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ, int chunkCount) {
        private int widthChunks() {
            return maxChunkX - minChunkX + 1;
        }

        private int depthChunks() {
            return maxChunkZ - minChunkZ + 1;
        }
    }

    private record PortalClearedArea(ResourceKey<Level> dimension, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {}

    public enum FollowMode {
        AUTO,
        OVERWORLD_YAW,
        NETHER_BARITONE
    }

    private enum ActiveFollowMode {
        OVERWORLD_YAW,
        NETHER_BARITONE
    }

    private enum StopReason {
        NONE,
        PORTAL_SKIP,
        NO_ELYTRA,
        NO_FIREWORKS
    }

    public enum TrailEndBehavior {
        DISABLE,
        RESET,
        KEEP_YAW
    }

    public enum PortalArrivalAction {
        DISABLE,
        STOP,
        KEEP_TARGET
    }
}
