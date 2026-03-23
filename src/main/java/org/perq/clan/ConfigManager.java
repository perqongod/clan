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
        return translateColors(config.getString("prefix"));
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
        msg = msg.replace("%prefix%", getPrefix());
        return translateColors(msg);
    }

    public int getKillPoints() {
        return config.getInt("kill-points");
    }

    public int getOnlineSaveInterval() {
        return config.getInt("online-save-interval");
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
        return config.getString("messages.clan-chat-format");
    }

    public int getTagLength() {
        return config.getInt("clan-tag-settings.length", 4);
    }

    public boolean isOnlyLettersAllowed() {
        return config.getBoolean("clan-tag-settings.only-letters", true);
    }

    public java.util.List<String> getTagBlacklist() {
        return config.getStringList("clan-tag-settings.blacklist");
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

    public String getClanSystemPrefix() {
        return translateColors(config.getString("clansystem-prefix", "&4[ClanSystem]&r "));
    }

    public int getWarCostPoints() {
        return config.getInt("war.cost-points", 200);
    }

    public int getWarCooldownMinutes() {
        return config.getInt("war.cooldown-minutes", 30);
    }

    public int getWarDurationMinutes() {
        return config.getInt("war.duration-minutes", 10);
    }

    public int getWarWinnerPoints() {
        return config.getInt("war.winner-points", 100);
    }

    public int getWarLoserPoints() {
        return config.getInt("war.loser-points", 100);
    }

    public int getInviteCooldownSeconds() {
        return config.getInt("cooldowns.invite-seconds", 10);
    }

    public int getRequestCooldownSeconds() {
        return config.getInt("cooldowns.request-seconds", 5);
    }

    public int getMaxMembers() {
        return config.getInt("clan.max-members", 10);
    }
}
