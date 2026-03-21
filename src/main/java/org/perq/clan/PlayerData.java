package org.perq.clan;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class PlayerData {
    private String name;
    private String clanTag;
    private String role;
    private boolean invitesEnabled;
    private double onlineTime;

    public PlayerData(String name) {
        this.name = name;
        this.clanTag = null;
        this.role = "MEMBER";
        this.invitesEnabled = true;
        this.onlineTime = 0.0;
    }

    public PlayerData(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        this.name = config.getString("name");
        this.clanTag = config.getString("clan");
        this.role = config.getString("role");
        this.invitesEnabled = config.getBoolean("invitesEnabled");
        this.onlineTime = config.getDouble("online-time");
    }

    public void save(File file) throws IOException {
        YamlConfiguration config = new YamlConfiguration();
        config.set("name", name);
        config.set("clan", clanTag);
        config.set("role", role);
        config.set("invitesEnabled", invitesEnabled);
        config.set("online-time", onlineTime);
        config.save(file);
    }

    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getClanTag() { return clanTag; }
    public void setClanTag(String clanTag) { this.clanTag = clanTag; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isInvitesEnabled() { return invitesEnabled; }
    public void setInvitesEnabled(boolean invitesEnabled) { this.invitesEnabled = invitesEnabled; }

    public double getOnlineTime() { return onlineTime; }
    public void setOnlineTime(double onlineTime) { this.onlineTime = onlineTime; }
}
