package dev.zak.trailfollower;

import net.minecraft.network.chat.Component;
import org.rusherhack.client.api.feature.hud.ShortListHudElement;
import org.rusherhack.core.setting.BooleanSetting;

import java.util.ArrayList;
import java.util.List;

public class TrailFollowerHudElement extends ShortListHudElement {

    private final TrailFollowerModule module;
    private final BooleanSetting showMessages = new BooleanSetting("Messages", true);
    private final BooleanSetting showTrail = new BooleanSetting("Trail", true);
    private final BooleanSetting showMovement = new BooleanSetting("Movement", true);
    private final BooleanSetting showPortal = new BooleanSetting("Portal", true);
    private final BooleanSetting showBaritone = new BooleanSetting("Baritone", true);

    public TrailFollowerHudElement(TrailFollowerModule module) {
        super("TrailFollowerInfo");
        this.module = module;
        this.setDescription("Shows TrailFollower status messages and optional trail, movement, portal, and Baritone groups.");
        this.axis.setValue(TextAxis.Y);
        this.registerSettings(showMessages, showTrail, showMovement, showPortal, showBaritone);
        this.setToggled(true);
    }

    @Override
    public Component[] getComponents() {
        TrailFollowerModule.TrailFollowerHudStatus status = module.getHudStatus();
        List<Component> lines = new ArrayList<>();

        lines.add(Component.literal("TF: " + status.state()));

        if (showMessages.getValue()) {
            lines.add(Component.literal(status.message()));
        }

        if (!status.enabled()) {
            return lines.toArray(Component[]::new);
        }

        if (showPortal.getValue() && status.portalLanding()) {
            lines.add(Component.literal("Portal: " + status.portalAreaWidth() + "x" + status.portalAreaDepth() + " " + Math.round(status.portalTargetDistance()) + "b"));
        }

        if (showTrail.getValue()) {
            lines.add(Component.literal("Trail: " + status.trailPoints() + " | Candidate: " + status.candidatePoints()));
            lines.add(Component.literal("Chunks: " + status.chunksPerSecond() + "/s | Last: " + formatAge(status.trailAgeSeconds()) + " | Abandon: " + formatAge(status.abandonCountdownSeconds())));
        }

        if (showMovement.getValue()) {
            lines.add(Component.literal("Mode: " + status.mode()));
            lines.add(Component.literal("Yaw: " + Math.round(status.playerYaw()) + " -> " + Math.round(status.targetYaw())));
        }

        if (showBaritone.getValue() && status.baritoneTargetDistance() >= 0.0) {
            String goalType = status.baritoneRecovery() ? "Recovery" : "Goal";
            lines.add(Component.literal("Baritone: " + goalType + " " + Math.round(status.baritoneTargetDistance()) + "b"));
        }

        return lines.toArray(Component[]::new);
    }

    private String formatAge(int seconds) {
        if (seconds < 0) {
            return "--";
        }

        return seconds + "s";
    }
}
