package org.perq.clan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.logging.Level;

public class ClanData {
    private String tag;
    private UUID leader;
    private List<UUID> moderators;
    private List<UUID> members;
    private int points;
    private Map<ClanQuestProgress.QuestTarget, Integer> questKillCounts;
    private int questPointsRedeemed;
    private String rank;
    private String created;
    private double onlineTime;
    private Location spawn;
    private Location chestLocation;
    private List<ItemStack> chestItems;
    private int skillLevel;
    private long lastRenameAt;
    /** Log entries in format "[HH:MM DD.MM.YYYY] message" */
    private List<String> logs;
    /** UUIDs of players who requested to join this clan */
    private List<UUID> pendingRequests;
    /** Per-member permission for the clan chest GUI/command */
    private Map<UUID, ClanChestPermission> chestPermissions;
    /** Per-member permission for friendly fire toggles */
    private Map<UUID, ClanFriendlyFirePermission> friendlyFirePermissions;
    /** Per-member permission for clan skills visibility/usage */
    private Map<UUID, ClanAccessPermission> skillsPermissions;
    /** Per-member permission for clan spawn visibility/usage */
    private Map<UUID, ClanAccessPermission> spawnPermissions;

    private static final DateTimeFormatter LOG_FMT = DateTimeFormatter.ofPattern("HH:mm dd.MM.yyyy");
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final int MAX_CHEST_SIZE = 54;

    public ClanData(String tag, UUID leader) {
        this.tag = tag;
        this.leader = leader;
        this.moderators = new ArrayList<>();
        this.members = new ArrayList<>();
        this.members.add(leader);
        this.points = 0;
        this.questKillCounts = ClanQuestProgress.createEmptyKillCounts();
        this.questPointsRedeemed = 0;
        this.rank = "Bronze";
        this.created = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        this.onlineTime = 0.0;
        this.spawn = null;
        this.chestLocation = null;
        this.chestItems = createEmptyChestItems();
        this.skillLevel = 0;
        this.logs = new ArrayList<>();
        this.pendingRequests = new ArrayList<>();
        this.chestPermissions = new HashMap<>();
        this.friendlyFirePermissions = new HashMap<>();
        this.skillsPermissions = new HashMap<>();
        this.spawnPermissions = new HashMap<>();
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
        this.questKillCounts = loadQuestKills(config);
        this.questPointsRedeemed = Math.max(0, config.getInt("quest-points-redeemed", 0));
        this.rank = config.getString("rank");
        this.created = config.getString("created");
        this.onlineTime = config.getDouble("online-time");
        this.skillLevel = config.getInt("skill-level", 0);
        this.lastRenameAt = config.getLong("last-rename-at", 0L);
        if (config.contains("spawn")) {
            String world = config.getString("spawn.world");
            double x = config.getDouble("spawn.x");
            double y = config.getDouble("spawn.y");
            double z = config.getDouble("spawn.z");
            float yaw = (float) config.getDouble("spawn.yaw", 0.0);
            float pitch = (float) config.getDouble("spawn.pitch", 0.0);
            this.spawn = new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
        }
        if (config.contains("chest")) {
            String world = config.getString("chest.world");
            double x = config.getDouble("chest.x");
            double y = config.getDouble("chest.y");
            double z = config.getDouble("chest.z");
            float yaw = (float) config.getDouble("chest.yaw", 0.0);
            float pitch = (float) config.getDouble("chest.pitch", 0.0);
            this.chestLocation = new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
        }
        this.chestItems = deserializeChestItems(config.getString("chest-items-json"), this.tag);
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
                    ClanChestPermission permission = ClanChestPermission.fromString(value);
                    if (permission == ClanChestPermission.DENY) {
                        permission = ClanChestPermission.VIEW;
                    }
                    if (permission != ClanChestPermission.VIEW) {
                        chestPermissions.put(memberId, permission);
                    }
                } catch (IllegalArgumentException ignored) {
                    // skip invalid UUIDs
                }
            }
        }
        this.friendlyFirePermissions = new HashMap<>();
        if (config.isConfigurationSection("friendly-fire-permissions")) {
            for (String key : config.getConfigurationSection("friendly-fire-permissions").getKeys(false)) {
                try {
                    UUID memberId = UUID.fromString(key);
                    String value = config.getString("friendly-fire-permissions." + key);
                    friendlyFirePermissions.put(memberId, ClanFriendlyFirePermission.fromString(value));
                } catch (IllegalArgumentException ignored) {
                    // skip invalid UUIDs
                }
            }
        }
        this.skillsPermissions = new HashMap<>();
        if (config.isConfigurationSection("skills-permissions")) {
            for (String key : config.getConfigurationSection("skills-permissions").getKeys(false)) {
                try {
                    UUID memberId = UUID.fromString(key);
                    String value = config.getString("skills-permissions." + key);
                    skillsPermissions.put(memberId, ClanAccessPermission.fromString(value));
                } catch (IllegalArgumentException ignored) {
                    // skip invalid UUIDs
                }
            }
        }
        this.spawnPermissions = new HashMap<>();
        if (config.isConfigurationSection("spawn-permissions")) {
            for (String key : config.getConfigurationSection("spawn-permissions").getKeys(false)) {
                try {
                    UUID memberId = UUID.fromString(key);
                    String value = config.getString("spawn-permissions." + key);
                    ClanAccessPermission permission = ClanAccessPermission.fromString(value);
                    if (permission == ClanAccessPermission.VIEW) {
                        permission = ClanAccessPermission.DENY;
                    }
                    if (permission != ClanAccessPermission.defaultPermission()) {
                        spawnPermissions.put(memberId, permission);
                    }
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
        Map<String, Integer> questKills = new HashMap<>();
        for (Map.Entry<ClanQuestProgress.QuestTarget, Integer> entry : questKillCounts.entrySet()) {
            questKills.put(entry.getKey().getKey(), entry.getValue());
        }
        config.set("quest-kills", questKills);
        config.set("quest-points-redeemed", questPointsRedeemed);
        config.set("rank", rank);
        config.set("created", created);
        config.set("online-time", onlineTime);
        config.set("skill-level", skillLevel);
        config.set("last-rename-at", lastRenameAt);
        if (spawn != null) {
            config.set("spawn.world", spawn.getWorld().getName());
            config.set("spawn.x", spawn.getX());
            config.set("spawn.y", spawn.getY());
            config.set("spawn.z", spawn.getZ());
            config.set("spawn.yaw", spawn.getYaw());
            config.set("spawn.pitch", spawn.getPitch());
        }
        if (chestLocation != null) {
            config.set("chest.world", chestLocation.getWorld().getName());
            config.set("chest.x", chestLocation.getX());
            config.set("chest.y", chestLocation.getY());
            config.set("chest.z", chestLocation.getZ());
            config.set("chest.yaw", chestLocation.getYaw());
            config.set("chest.pitch", chestLocation.getPitch());
        }
        config.set("chest-items-json", serializeChestItems(chestItems));
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
        Map<String, String> friendlyPerms = new HashMap<>();
        for (UUID mem : members) {
            ClanFriendlyFirePermission permission = getFriendlyFirePermission(mem);
            if (permission != ClanFriendlyFirePermission.ALLOW) {
                friendlyPerms.put(mem.toString(), permission.name());
            }
        }
        config.set("friendly-fire-permissions", friendlyPerms);
        Map<String, String> skillPerms = new HashMap<>();
        for (UUID mem : members) {
            ClanAccessPermission permission = getSkillsPermission(mem);
            if (permission != ClanAccessPermission.defaultPermission()) {
                skillPerms.put(mem.toString(), permission.name());
            }
        }
        config.set("skills-permissions", skillPerms);
        Map<String, String> spawnPerms = new HashMap<>();
        for (UUID mem : members) {
            ClanAccessPermission permission = getSpawnPermission(mem);
            if (permission != ClanAccessPermission.defaultPermission()) {
                spawnPerms.put(mem.toString(), permission.name());
            }
        }
        config.set("spawn-permissions", spawnPerms);
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

    public int getQuestKillCount(ClanQuestProgress.QuestTarget target) {
        return questKillCounts.getOrDefault(target, 0);
    }

    public int addQuestKill(ClanQuestProgress.QuestTarget target) {
        int updated = getQuestKillCount(target) + 1;
        questKillCounts.put(target, updated);
        return updated;
    }

    public Map<ClanQuestProgress.QuestTarget, Integer> getQuestKillCounts() {
        return new EnumMap<>(questKillCounts);
    }

    public String getRank() { return rank; }
    public void setRank(String rank) { this.rank = rank; }

    /**
     * Returns remaining quest skill points after redemptions.
     */
    public int getQuestSkillPoints() {
        int earnedPoints = ClanQuestProgress.getQuestSkillPoints(questKillCounts);
        return Math.max(0, earnedPoints - questPointsRedeemed);
    }

    public int getQuestPointsRedeemed() {
        return questPointsRedeemed;
    }

    public void setQuestPointsRedeemed(int questPointsRedeemed) {
        int earnedPoints = ClanQuestProgress.getQuestSkillPoints(questKillCounts);
        int clamped = Math.min(questPointsRedeemed, earnedPoints);
        this.questPointsRedeemed = Math.max(0, clamped);
    }

    /**
     * Returns remaining quest points available for redemption after redemptions (same as getQuestSkillPoints()).
     */
    public int getRedeemableQuestPoints() {
        return getQuestSkillPoints();
    }

    public int getSkillPoints() {
        return points + getQuestSkillPoints();
    }

    public String getCreated() { return created; }
    public void setCreated(String created) { this.created = created; }

    public double getOnlineTime() { return onlineTime; }
    public void setOnlineTime(double onlineTime) { this.onlineTime = onlineTime; }

    public Location getSpawn() { return spawn; }
    public void setSpawn(Location spawn) { this.spawn = spawn; }

    public Location getChestLocation() { return chestLocation; }
    public void setChestLocation(Location chestLocation) { this.chestLocation = chestLocation; }

    public List<ItemStack> getChestItems() {
        return new ArrayList<>(chestItems);
    }

    public ItemStack[] getChestContents() {
        return chestItems.toArray(new ItemStack[0]);
    }

    public void setChestContents(ItemStack[] contents) {
        List<ItemStack> normalized = new ArrayList<>();
        if (contents != null) {
            Collections.addAll(normalized, contents);
        }
        normalized = normalizeChestItems(normalized);
        if (contents != null && contents.length < normalized.size() && chestItems != null
                && chestItems.size() == normalized.size()) {
            for (int i = contents.length; i < normalized.size(); i++) {
                normalized.set(i, chestItems.get(i));
            }
        }
        chestItems = normalized;
    }

    public int getSkillLevel() { return skillLevel; }
    public void setSkillLevel(int skillLevel) { this.skillLevel = skillLevel; }

    public long getLastRenameAt() { return lastRenameAt; }
    public void setLastRenameAt(long lastRenameAt) { this.lastRenameAt = lastRenameAt; }


    public List<String> getLogs() { return logs; }
    public void setLogs(List<String> logs) { this.logs = logs; }

    public List<UUID> getPendingRequests() { return pendingRequests; }
    public void setPendingRequests(List<UUID> pendingRequests) { this.pendingRequests = pendingRequests; }

    private Map<ClanQuestProgress.QuestTarget, Integer> loadQuestKills(YamlConfiguration config) {
        Map<ClanQuestProgress.QuestTarget, Integer> questKills = ClanQuestProgress.createEmptyKillCounts();
        ConfigurationSection questSection = config.getConfigurationSection("quest-kills");
        if (questSection != null) {
            for (String key : questSection.getKeys(false)) {
                ClanQuestProgress.QuestTarget target = ClanQuestProgress.getQuestTargetByKey(key);
                if (target != null) {
                    questKills.put(target, questSection.getInt(key));
                }
            }
        }
        int legacyZombies = config.getInt("quest-zombie-kills", 0);
        if (legacyZombies > 0) {
            int current = questKills.getOrDefault(ClanQuestProgress.QuestTarget.ZOMBIE, 0);
            if (legacyZombies > current) {
                questKills.put(ClanQuestProgress.QuestTarget.ZOMBIE, legacyZombies);
            }
        }
        return questKills;
    }

    public ClanChestPermission getChestPermission(UUID member) {
        return chestPermissions.getOrDefault(member, ClanChestPermission.VIEW);
    }

    public void setChestPermission(UUID member, ClanChestPermission permission) {
        ClanChestPermission normalized = permission == ClanChestPermission.DENY
                ? ClanChestPermission.VIEW
                : permission;
        if (normalized == null || normalized == ClanChestPermission.VIEW) {
            chestPermissions.remove(member);
        } else {
            chestPermissions.put(member, normalized);
        }
    }

    public ClanFriendlyFirePermission getFriendlyFirePermission(UUID member) {
        return friendlyFirePermissions.getOrDefault(member, ClanFriendlyFirePermission.ALLOW);
    }

    public void setFriendlyFirePermission(UUID member, ClanFriendlyFirePermission permission) {
        if (permission == null || permission == ClanFriendlyFirePermission.ALLOW) {
            friendlyFirePermissions.remove(member);
        } else {
            friendlyFirePermissions.put(member, permission);
        }
    }

    public ClanAccessPermission getSkillsPermission(UUID member) {
        return skillsPermissions.getOrDefault(member, ClanAccessPermission.defaultPermission());
    }

    public void setSkillsPermission(UUID member, ClanAccessPermission permission) {
        if (permission == null || permission == ClanAccessPermission.defaultPermission()) {
            skillsPermissions.remove(member);
        } else {
            skillsPermissions.put(member, permission);
        }
    }

    public ClanAccessPermission getSpawnPermission(UUID member) {
        return spawnPermissions.getOrDefault(member, ClanAccessPermission.defaultPermission());
    }

    public void setSpawnPermission(UUID member, ClanAccessPermission permission) {
        ClanAccessPermission normalized = permission == ClanAccessPermission.VIEW
                ? ClanAccessPermission.DENY
                : permission;
        if (normalized == null || normalized == ClanAccessPermission.defaultPermission()) {
            spawnPermissions.remove(member);
        } else {
            spawnPermissions.put(member, normalized);
        }
    }

    private static List<ItemStack> createEmptyChestItems() {
        return new ArrayList<>(Collections.nCopies(MAX_CHEST_SIZE, null));
    }

    private static List<ItemStack> normalizeChestItems(List<ItemStack> items) {
        List<ItemStack> normalized = new ArrayList<>(items == null ? Collections.emptyList() : items);
        if (normalized.size() > MAX_CHEST_SIZE) {
            normalized = new ArrayList<>(normalized.subList(0, MAX_CHEST_SIZE));
        }
        while (normalized.size() < MAX_CHEST_SIZE) {
            normalized.add(null);
        }
        return normalized;
    }

    private static String serializeChestItems(List<ItemStack> items) {
        List<ItemStack> normalized = normalizeChestItems(items);
        List<String> encoded = normalized.stream()
                .map(ClanData::encodeItemStack)
                .collect(Collectors.toList());
        return GSON.toJson(encoded);
    }

    private static List<ItemStack> deserializeChestItems(String json, String clanTag) {
        if (json == null || json.isEmpty()) return createEmptyChestItems();
        try {
            List<String> encoded = GSON.fromJson(json, new TypeToken<List<String>>() {}.getType());
            if (encoded == null) return createEmptyChestItems();
            List<ItemStack> items = new ArrayList<>();
            for (String entry : encoded) {
                items.add(decodeItemStack(entry, clanTag));
            }
            return normalizeChestItems(items);
        } catch (RuntimeException e) {
            return createEmptyChestItems();
        }
    }

    private static String encodeItemStack(ItemStack item) {
        if (item == null) return null;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             BukkitObjectOutputStream data = new BukkitObjectOutputStream(output)) {
            data.writeObject(item);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException e) {
            return null;
        }
    }

    private static ItemStack decodeItemStack(String data, String clanTag) {
        if (data == null || data.isEmpty()) return null;
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            Bukkit.getLogger().log(Level.WARNING,
                    "[Clan] Invalid clan chest item data encountered for clan: " + clanTag, e);
            return null;
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(raw);
             BukkitObjectInputStream dataStream = new BukkitObjectInputStream(input)) {
            Object obj = dataStream.readObject();
            if (obj instanceof ItemStack) {
                return (ItemStack) obj;
            }
        } catch (IOException | ClassNotFoundException e) {
            Bukkit.getLogger().log(Level.WARNING,
                    "[Clan] Failed to deserialize clan chest item for clan: " + clanTag, e);
        }
        return null;
    }
}
