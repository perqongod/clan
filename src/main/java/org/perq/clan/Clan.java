package org.perq.clan;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Clan extends JavaPlugin {

    private ConfigManager configManager;
    private FileManager fileManager;
    private Map<UUID, Boolean> invitationToggles = new HashMap<>(); // true = disabled

    @Override
    public void onEnable() {
        // Plugin startup logic
        configManager = new ConfigManager(this);
        fileManager = new FileManager(this);

        getCommand("clan").setExecutor(new ClanCommand(this));
        getCommand("clan").setTabCompleter(new ClanCommand(this));
        getCommand("c").setExecutor(new ClanChatCommand(this));

        getServer().getPluginManager().registerEvents(new EventListener(this), this);
        
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

    public boolean toggleInvitation(UUID player) {
        boolean current = invitationToggles.getOrDefault(player, false);
        invitationToggles.put(player, !current);
        return !current; // true if now disabled
    }

    public Map<UUID, Boolean> getInvitationToggles() {
        return invitationToggles;
    }
}
