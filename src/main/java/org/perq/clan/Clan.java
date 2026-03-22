package org.perq.clan;

import org.bukkit.plugin.java.JavaPlugin;

public final class Clan extends JavaPlugin {

    private ConfigManager configManager;
    private FileManager fileManager;

    @Override
    public void onEnable() {
        // Plugin startup logic
        configManager = new ConfigManager(this);
        fileManager = new FileManager(this);

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
}
