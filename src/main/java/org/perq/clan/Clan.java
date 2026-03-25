package org.perq.clan;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

public final class Clan extends JavaPlugin {

    private ConfigManager configManager;
    private FileManager fileManager;
    private ClanSettingsListener clanSettingsListener;
    private ClanSkillsListener clanSkillsListener;
    private ClanStatsListener clanStatsListener;
    private ClanQuestListener clanQuestListener;

    @Override
    public void onEnable() {
        // Plugin startup logic
        configManager = new ConfigManager(this);
        fileManager = new FileManager(this);
        clanSettingsListener = new ClanSettingsListener(this);
        clanSkillsListener = new ClanSkillsListener(this);
        clanStatsListener = new ClanStatsListener(this);
        clanQuestListener = new ClanQuestListener(this);

        ClanCommand clanCommand = new ClanCommand(this);
        getCommand("clan").setExecutor(clanCommand);
        getCommand("clan").setTabCompleter(clanCommand);
        getServer().getPluginManager().registerEvents(new EventListener(this), this);
        getServer().getPluginManager().registerEvents(clanSettingsListener, this);
        getServer().getPluginManager().registerEvents(clanSkillsListener, this);
        getServer().getPluginManager().registerEvents(clanStatsListener, this);
        getServer().getPluginManager().registerEvents(clanQuestListener, this);

        // Register PlaceholderAPI expansion if available
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ClanPlaceholder(this).register();
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public FileManager getFileManager() {
        return fileManager;
    }

    public ClanSettingsListener getClanSettingsListener() {
        return clanSettingsListener;
    }

    public ClanSkillsListener getClanSkillsListener() {
        return clanSkillsListener;
    }

    public ClanStatsListener getClanStatsListener() {
        return clanStatsListener;
    }

    public ClanQuestListener getClanQuestListener() {
        return clanQuestListener;
    }

    public boolean toggleInvitation(Player player) {
        PlayerData data = fileManager.loadPlayer(player.getUniqueId());
        if (data == null) {
            data = new PlayerData(player.getName());
        }
        boolean nowEnabled = !data.isInvitesEnabled();
        data.setInvitesEnabled(nowEnabled);
        try {
            fileManager.savePlayer(player.getUniqueId(), data);
        } catch (IOException e) {
            player.sendMessage(configManager.getPrefix() + "Error saving invitation settings.");
            getLogger().log(Level.WARNING, "Failed to save invitation settings for " + player.getName(), e);
        }
        return !nowEnabled;
    }

    public boolean isInvitesEnabled(UUID player) {
        PlayerData data = fileManager.loadPlayer(player);
        return data == null || data.isInvitesEnabled();
    }
}
