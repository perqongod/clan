package org.perq.clan;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private int skillLevel;
    /** Log entries in format "[HH:MM DD.MM.YYYY] message" */
    private List<String> logs;
    /** UUIDs of players who requested to join this clan */
    private List<UUID> pendingRequests;
    /** Per-member permission for the clan chest GUI/command */
    private Map<UUID, ClanChestPermission> chestPermissions;

    private static final DateTimeFormatter LOG_FMT = DateTimeFormatter.ofPattern("HH:mm dd.MM.yyyy");

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
        this.skillLevel = 0;
        this.logs = new ArrayList<>();
        this.pendingRequests = new ArrayList<>();
        this.chestPermissions = new HashMap<>();
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
        this.skillLevel = config.getInt("skill-level", 0);
        if (config.contains("spawn")) {
            String world = config.getString("spawn.world");
            double x = config.getDouble("spawn.x");
            double y = config.getDouble("spawn.y");
            double z = config.getDouble("spawn.z");
            this.spawn = new Location(Bukkit.getWorld(world), x, y, z);
        }
        this.logs = new ArrayList<>(config.getStringList("logs"));
        this.pendingRequests = new ArrayList<>();
        for (String req : config.getStringList("pending-requests")) {
            try { pendingRequests.add(UUID.fromString(req)); } catch (IllegalArgumentException ignored) {}
        }
        this.chestPermissions = new HashMap<>();
        if (config.isConfigurationSection("chest-permissions")) {
            for (String key : config.getConfigurationSection("chest-permissions").getKeys(false)) {
                try {
                    UUID memberId = UUID.fromString(key);
                    String value = config.getString("chest-permissions." + key);
                    chestPermissions.put(memberId, ClanChestPermission.fromString(value));
                } catch (IllegalArgumentException ignored) {
                    // skip invalid UUIDs
                }
            }
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
        config.set("skill-level", skillLevel);
        if (spawn != null) {
            config.set("spawn.world", spawn.getWorld().getName());
            config.set("spawn.x", spawn.getX());
            config.set("spawn.y", spawn.getY());
            config.set("spawn.z", spawn.getZ());
        }
        config.set("logs", logs);
        List<String> reqStrings = new ArrayList<>();
        for (UUID req : pendingRequests) {
            reqStrings.add(req.toString());
        }
        config.set("pending-requests", reqStrings);
        Map<String, String> perms = new HashMap<>();
        for (UUID mem : members) {
            ClanChestPermission permission = getChestPermission(mem);
            if (permission != ClanChestPermission.VIEW) {
                perms.put(mem.toString(), permission.name());
            }
        }
        config.set("chest-permissions", perms);
        config.save(file);
    }

    /** Adds a log entry with the current timestamp. */
    public void addLog(String message) {
        String entry = "[" + LocalDateTime.now().format(LOG_FMT) + "] " + message;
        logs.add(entry);
        // Keep only the last 200 entries to avoid file bloat
        if (logs.size() > 200) {
            logs = new ArrayList<>(logs.subList(logs.size() - 200, logs.size()));
        }
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

    public int getSkillLevel() { return skillLevel; }
    public void setSkillLevel(int skillLevel) { this.skillLevel = skillLevel; }

    public List<String> getLogs() { return logs; }
    public void setLogs(List<String> logs) { this.logs = logs; }

    public List<UUID> getPendingRequests() { return pendingRequests; }
    public void setPendingRequests(List<UUID> pendingRequests) { this.pendingRequests = pendingRequests; }

    public ClanChestPermission getChestPermission(UUID member) {
        return chestPermissions.getOrDefault(member, ClanChestPermission.VIEW);
    }

    public void setChestPermission(UUID member, ClanChestPermission permission) {
        if (permission == null || permission == ClanChestPermission.VIEW) {
            chestPermissions.remove(member);
        } else {
            chestPermissions.put(member, permission);
        }
    }
}
