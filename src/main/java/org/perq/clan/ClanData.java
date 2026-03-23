package org.perq.clan;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClanData {
    private String tag;
    private UUID leader;
    private List<UUID> moderators;
    private List<UUID> members;
    private int points;
    private String rank;
    private String created;
    private double onlineTime;
    private Location spawn;
    private List<String> pendingRequesters;

    public ClanData(String tag, UUID leader) {
        this.tag = tag;
        this.leader = leader;
        this.moderators = new ArrayList<>();
        this.members = new ArrayList<>();
        this.members.add(leader);
        this.points = 0;
        this.rank = "Bronze";
        this.created = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        this.onlineTime = 0.0;
        this.spawn = null;
        this.pendingRequesters = new ArrayList<>();
    }

    public ClanData(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        this.tag = config.getString("tag");
        this.leader = UUID.fromString(config.getString("leader"));
        this.moderators = new ArrayList<>();
        for (String mod : config.getStringList("moderators")) {
            moderators.add(UUID.fromString(mod));
        }
        this.members = new ArrayList<>();
        for (String mem : config.getStringList("members")) {
            members.add(UUID.fromString(mem));
        }
        this.points = config.getInt("points");
        this.rank = config.getString("rank");
        this.created = config.getString("created");
        this.onlineTime = config.getDouble("online-time");
        this.pendingRequesters = config.getStringList("pending-requesters");
        if (this.pendingRequesters == null) this.pendingRequesters = new ArrayList<>();
        if (config.contains("spawn")) {
            String world = config.getString("spawn.world");
            double x = config.getDouble("spawn.x");
            double y = config.getDouble("spawn.y");
            double z = config.getDouble("spawn.z");
            this.spawn = new Location(Bukkit.getWorld(world), x, y, z);
        }
    }

    public void save(File file) throws IOException {
        YamlConfiguration config = new YamlConfiguration();
        config.set("tag", tag);
        config.set("leader", leader.toString());
        List<String> mods = new ArrayList<>();
        for (UUID mod : moderators) {
            mods.add(mod.toString());
        }
        config.set("moderators", mods);
        List<String> mems = new ArrayList<>();
        for (UUID mem : members) {
            mems.add(mem.toString());
        }
        config.set("members", mems);
        config.set("points", points);
        config.set("rank", rank);
        config.set("created", created);
        config.set("online-time", onlineTime);
        config.set("pending-requesters", pendingRequesters);
        if (spawn != null) {
            config.set("spawn.world", spawn.getWorld().getName());
            config.set("spawn.x", spawn.getX());
            config.set("spawn.y", spawn.getY());
            config.set("spawn.z", spawn.getZ());
        }
        config.save(file);
    }

    // Getters and setters
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    public UUID getLeader() { return leader; }
    public void setLeader(UUID leader) { this.leader = leader; }

    public List<UUID> getModerators() { return moderators; }
    public void setModerators(List<UUID> moderators) { this.moderators = moderators; }

    public List<UUID> getMembers() { return members; }
    public void setMembers(List<UUID> members) { this.members = members; }

    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }

    public String getRank() { return rank; }
    public void setRank(String rank) { this.rank = rank; }

    public String getCreated() { return created; }
    public void setCreated(String created) { this.created = created; }

    public double getOnlineTime() { return onlineTime; }
    public void setOnlineTime(double onlineTime) { this.onlineTime = onlineTime; }

    public Location getSpawn() { return spawn; }
    public void setSpawn(Location spawn) { this.spawn = spawn; }

    public List<String> getPendingRequesters() { return pendingRequesters; }
    public void setPendingRequesters(List<String> pendingRequesters) { this.pendingRequesters = pendingRequesters; }
}
