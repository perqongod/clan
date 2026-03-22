package org.perq.clan;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FileManager {
    private final Plugin plugin;
    private final File clansDir;
    private final File playersDir;
    private final File invitesDir;

    public FileManager(Plugin plugin) {
        this.plugin = plugin;
        this.clansDir = new File(plugin.getDataFolder(), "clans");
        this.playersDir = new File(plugin.getDataFolder(), "players");
        this.invitesDir = new File(plugin.getDataFolder(), "invites");
        if (!clansDir.exists()) clansDir.mkdirs();
        if (!playersDir.exists()) playersDir.mkdirs();
        if (!invitesDir.exists()) invitesDir.mkdirs();
    }

    // Clan methods
    public ClanData loadClan(String tag) {
        File file = new File(clansDir, tag + ".yml");
        if (!file.exists()) return null;
        return new ClanData(file);
    }

    public void saveClan(ClanData clan) throws IOException {
        File file = new File(clansDir, clan.getTag() + ".yml");
        clan.save(file);
    }

    public void deleteClan(String tag) {
        File file = new File(clansDir, tag + ".yml");
        if (file.exists()) file.delete();
    }

    public Map<String, ClanData> loadAllClans() {
        Map<String, ClanData> clans = new HashMap<>();
        File[] files = clansDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String tag = file.getName().replace(".yml", "");
                clans.put(tag, new ClanData(file));
            }
        }
        return clans;
    }

    // Player methods
    public PlayerData loadPlayer(UUID uuid) {
        File file = new File(playersDir, uuid.toString() + ".yml");
        if (!file.exists()) return null;
        return new PlayerData(file);
    }

    public void savePlayer(UUID uuid, PlayerData player) throws IOException {
        File file = new File(playersDir, uuid.toString() + ".yml");
        player.save(file);
    }

    // Invite methods
    public InviteData loadInvite(UUID uuid) {
        File file = new File(invitesDir, uuid.toString() + ".yml");
        if (!file.exists()) return null;
        List<InviteData> all = InviteData.loadAllFromFile(file);
        return all.isEmpty() ? null : all.get(0);
    }

    public List<InviteData> loadAllInvites(UUID uuid) {
        File file = new File(invitesDir, uuid.toString() + ".yml");
        return InviteData.loadAllFromFile(file);
    }

    public void saveInvite(UUID uuid, InviteData invite) throws IOException {
        File file = new File(invitesDir, uuid.toString() + ".yml");
        invite.save(file);
    }

    public void deleteInvite(UUID uuid) {
        File file = new File(invitesDir, uuid.toString() + ".yml");
        if (file.exists()) file.delete();
    }

    public void deleteSpecificInvite(UUID uuid, String clanTag) throws IOException {
        File file = new File(invitesDir, uuid.toString() + ".yml");
        List<InviteData> all = InviteData.loadAllFromFile(file);
        all.removeIf(inv -> clanTag.equals(inv.getFromClan()));
        if (all.isEmpty()) {
            if (file.exists()) file.delete();
        } else {
            InviteData.saveAllToFile(file, all);
        }
    }
}
