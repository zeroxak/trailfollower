package dev.zak.trailfollower;

import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.plugin.Plugin;

public class TrailFollowerPlugin extends Plugin {

    private TrailFollowerModule trailFollowerModule;

    @Override
    public void onLoad() {
        this.getLogger().info("TrailFollower plugin loaded.");
        this.trailFollowerModule = new TrailFollowerModule();
        RusherHackAPI.getModuleManager().registerFeature(this.trailFollowerModule);
        RusherHackAPI.getHudManager().registerFeature(new TrailFollowerHudElement(this.trailFollowerModule));
    }

    @Override
    public void onUnload() {
        this.getLogger().info("TrailFollower plugin unloaded.");
    }
}
