package org.perq.clan;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Clan extends JavaPlugin {

    private ConfigManager configManager;
    private FileManager fileManager;
    private WarManager warManager;
    private Map<UUID, Boolean> invitationToggles = new HashMap<>(); // true = disabled
    private Set<UUID> clanChatMode = new HashSet<>();

    @Override
    public void onEnable() {
        // Plugin startup logic
        configManager = new ConfigManager(this);
        fileManager = new FileManager(this);
        warManager = new WarManager();

        getCommand("clan").setExecutor(new ClanCommand(this));
        getCommand("clan").setTabCompleter(new ClanCommand(this));

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

    public WarManager getWarManager() {
        return warManager;
    }

    public boolean toggleInvitation(UUID player) {
        boolean current = invitationToggles.getOrDefault(player, false);
        invitationToggles.put(player, !current);
        return !current; // true if now disabled
    }

    public Map<UUID, Boolean> getInvitationToggles() {
        return invitationToggles;
    }

    public Set<UUID> getClanChatMode() {
        return clanChatMode;
    }

    /** Toggles clan chat mode. Returns true if now enabled. */
    public boolean toggleClanChat(UUID player) {
        if (clanChatMode.contains(player)) {
            clanChatMode.remove(player);
            return false;
        } else {
            clanChatMode.add(player);
            return true;
        }
    }
}
