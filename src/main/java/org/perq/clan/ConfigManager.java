package org.perq.clan;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigManager {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private final Clan plugin;
    private FileConfiguration config;

    public ConfigManager(Clan plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        config = plugin.getConfig();
    }

    public String getPrefix() {
        return translateColors(config.getString("prefix", "&b[Xyntrix]&r "));
    }

    public String getClanSystemPrefix() {
        return translateColors(config.getString("clansystem-prefix", "&4[ClanSystem]&r "));
    }

    private static String translateHexColorCodes(String message) {
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer(message.length() + 4 * 8);
        while (matcher.find()) {
            String group = matcher.group(1);
            matcher.appendReplacement(buffer, "§x§" + group.charAt(0) + "§" + group.charAt(1) + "§" + group.charAt(2) + "§" + group.charAt(3) + "§" + group.charAt(4) + "§" + group.charAt(5));
        }
        return matcher.appendTail(buffer).toString();
    }

    public String translateColors(String message) {
        return ChatColor.translateAlternateColorCodes('&', translateHexColorCodes(message));
    }

    public String getMessage(String key) {
        String msg = config.getString("messages." + key);
        if (msg == null) return getPrefix() + "§cMissing message: " + key;
        msg = msg.replace("%prefix%", getPrefix());
        msg = msg.replace("%csprefix%", getClanSystemPrefix());
        return translateColors(msg);
    }

    public int getKillPoints() {
        return config.getInt("kill-points", 1);
    }

    public int getOnlineSaveInterval() {
        return config.getInt("online-save-interval", 60);
    }

    public Map<String, Integer> getRanks() {
        Map<String, Integer> ranks = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("ranks");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ranks.put(key, section.getInt(key));
            }
        }
        return ranks;
    }

    public String getClanChatFormat() {
        return config.getString("messages.clan-chat-format", "&e[ᴄʟᴀɴꜱʏꜱᴛᴇᴍ] &f%player%&7: &f%message%");
    }

    /** @deprecated Use getTagMinLength() / getTagMaxLength() */
    @Deprecated
    public int getTagLength() {
        return config.getInt("clan-tag-settings.max-length", config.getInt("clan-tag-settings.length", 4));
    }

    public int getTagMinLength() {
        return config.getInt("clan-tag-settings.min-length", 3);
    }

    public int getTagMaxLength() {
        return config.getInt("clan-tag-settings.max-length", config.getInt("clan-tag-settings.length", 8));
    }

    public boolean isOnlyLettersAllowed() {
        return config.getBoolean("clan-tag-settings.only-letters", false);
    }

    public java.util.List<String> getTagBlacklist() {
        return config.getStringList("clan-tag-settings.blacklist");
    }

    public int getMaxMembers() {
        return config.getInt("max-members", 10);
    }

    public int getWarCost() {
        return config.getInt("war.cost", 200);
    }

    public int getWarCooldownMinutes() {
        return config.getInt("war.cooldown-minutes", 30);
    }

    public int getWarArenaCountdownSeconds() {
        return config.getInt("war.arena-countdown-seconds", 600);
    }

    public String getMySQLHost() {
        return config.getString("mysql.host");
    }

    public int getMySQLPort() {
        return config.getInt("mysql.port");
    }

    public String getMySQLDatabase() {
        return config.getString("mysql.database");
    }

    public String getMySQLUsername() {
        return config.getString("mysql.username");
    }

    public String getMySQLPassword() {
        return config.getString("mysql.password");
    }

    public boolean getEnableDatabase() {
        return config.getBoolean("enable-database");
    }
}
