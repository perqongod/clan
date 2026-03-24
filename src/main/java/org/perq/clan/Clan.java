package org.perq.clan;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Clan extends JavaPlugin {

    private ConfigManager configManager;
    private FileManager fileManager;
    private WarManager warManager;
    private WarTeamSelectionListener warTeamSelectionListener;
    private ClanSettingsListener clanSettingsListener;
    private ClanSkillsListener clanSkillsListener;
    private Map<UUID, Boolean> invitationToggles = new HashMap<>(); // true = disabled

    @Override
    public void onEnable() {
        // Plugin startup logic
        configManager = new ConfigManager(this);
        fileManager = new FileManager(this);
        warManager = new WarManager(this);
        warTeamSelectionListener = new WarTeamSelectionListener(this);
        clanSettingsListener = new ClanSettingsListener(this);
        clanSkillsListener = new ClanSkillsListener(this);

        ClanCommand clanCommand = new ClanCommand(this);
        getCommand("clan").setExecutor(clanCommand);
        getCommand("clan").setTabCompleter(clanCommand);
        if (getCommand("war") != null) {
            getCommand("war").setExecutor(clanCommand);
            getCommand("war").setTabCompleter(clanCommand);
        }

        getServer().getPluginManager().registerEvents(new EventListener(this), this);
        getServer().getPluginManager().registerEvents(warTeamSelectionListener, this);
        getServer().getPluginManager().registerEvents(clanSettingsListener, this);
        getServer().getPluginManager().registerEvents(clanSkillsListener, this);

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

    public WarManager getWarManager() {
        return warManager;
    }

    public WarTeamSelectionListener getWarTeamSelectionListener() {
        return warTeamSelectionListener;
    }

    public ClanSettingsListener getClanSettingsListener() {
        return clanSettingsListener;
    }

    public ClanSkillsListener getClanSkillsListener() {
        return clanSkillsListener;
    }

    public boolean toggleInvitation(UUID player) {
        boolean current = invitationToggles.getOrDefault(player, false);
        invitationToggles.put(player, !current);
        return !current; // true if now disabled
    }

    public Map<UUID, Boolean> getInvitationToggles() {
        return invitationToggles;
    }
}
