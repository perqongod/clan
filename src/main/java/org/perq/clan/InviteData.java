package org.perq.clan;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class InviteData {
    private String fromClan;
    private long timestamp;

    public InviteData(String fromClan) {
        this.fromClan = fromClan;
        this.timestamp = System.currentTimeMillis();
    }

    public InviteData(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        this.fromClan = config.getString("from");
        this.timestamp = config.getLong("timestamp");
    }

    public void save(File file) throws IOException {
        YamlConfiguration config = new YamlConfiguration();
        config.set("from", fromClan);
        config.set("timestamp", timestamp);
        config.save(file);
    }

    // Getters
    public String getFromClan() { return fromClan; }
    public long getTimestamp() { return timestamp; }
}
