package org.perq.clan;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigManager {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%[^%]+%");
    private final Clan plugin;
    private FileConfiguration config;

    public ConfigManager(Clan plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        config = plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        config = plugin.getConfig();
    }

    public String getPrefix() {
        return translateColors(config.getString("prefix", "&6&lxyntrix &7| "));
    }

    public String getClanSystemPrefix() {
        return translateColors(config.getString("clansystem-prefix", "&4[ᴄʟᴀɴꜱʏꜱᴛᴇᴍ] &7| "));
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
        return formatMessage(message, true);
    }

    public String formatPlain(String message) {
        return formatMessage(message, false);
    }

    private String formatMessage(String message, boolean preservePlaceholders) {
        if (message == null) return null;
        String translated = ChatColor.translateAlternateColorCodes('&', translateHexColorCodes(message));
        String stripped = ChatColor.stripColor(translated);
        return preservePlaceholders ? toSmallCapsPreservingPlaceholders(stripped) : toSmallCaps(stripped);
    }

    private static String toSmallCapsPreservingPlaceholders(String input) {
        if (input == null) return null;
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(input);
        StringBuilder builder = new StringBuilder(input.length());
        int lastIndex = 0;
        while (matcher.find()) {
            builder.append(toSmallCaps(input.substring(lastIndex, matcher.start())));
            builder.append(input, matcher.start(), matcher.end());
            lastIndex = matcher.end();
        }
        builder.append(toSmallCaps(input.substring(lastIndex)));
        return builder.toString();
    }

    private static String toSmallCaps(String input) {
        if (input == null) return null;
        StringBuilder builder = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            builder.append(toSmallCapsChar(input.charAt(i)));
        }
        return builder.toString();
    }

    private static String toSmallCapsChar(char value) {
        switch (Character.toLowerCase(value)) {
            case 'a':
                return "ᴀ";
            case 'b':
                return "ʙ";
            case 'c':
                return "ᴄ";
            case 'd':
                return "ᴅ";
            case 'e':
                return "ᴇ";
            case 'f':
                return "ꜰ";
            case 'g':
                return "ɢ";
            case 'h':
                return "ʜ";
            case 'i':
                return "ɪ";
            case 'j':
                return "ᴊ";
            case 'k':
                return "ᴋ";
            case 'l':
                return "ʟ";
            case 'm':
                return "ᴍ";
            case 'n':
                return "ɴ";
            case 'o':
                return "ᴏ";
            case 'p':
                return "ᴘ";
            case 'q':
                return "ǫ";
            case 'r':
                return "ʀ";
            case 's':
                return "ꜱ";
            case 't':
                return "ᴛ";
            case 'u':
                return "ᴜ";
            case 'v':
                return "ᴠ";
            case 'w':
                return "ᴡ";
            case 'x':
                return "x";
            case 'y':
                return "ʏ";
            case 'z':
                return "ᴢ";
            default:
                return String.valueOf(value);
        }
    }

    public Component getComponent(String path, String defaultValue) {
        String value = config.getString(path, defaultValue);
        return LegacyComponentSerializer.legacySection().deserialize(translateColors(value));
    }

    public String getConfigString(String path, String defaultValue) {
        String value = config.getString(path, defaultValue);
        return translateColors(value);
    }

    public String normalizeTag(String tag) {
        if (tag == null) return null;
        return tag.replace(ChatColor.COLOR_CHAR, '&');
    }

    public String getMessage(String key) {
        String msg = config.getString("messages." + key);
        if (msg == null) return formatPlain(getPrefix() + "Missing message: " + key);
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

    public String getRankForPoints(int points) {
        Map<String, Integer> ranks = getRanks();
        if (ranks.isEmpty()) return "Bronze";
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(ranks.entrySet());
        sorted.sort(Comparator.comparingInt(Map.Entry::getValue));
        String currentRank = sorted.get(0).getKey();
        for (Map.Entry<String, Integer> entry : sorted) {
            if (points >= entry.getValue()) {
                currentRank = entry.getKey();
            } else {
                break;
            }
        }
        return currentRank;
    }

    public String getClanChatFormat() {
        return translateColors(config.getString("messages.clan-chat-format", "&e[ᴄʟᴀɴꜱʏꜱᴛᴇᴍ] &f%player%&7: &f%message%"));
    }

    public String getHelpBookTitle() {
        return translateColors(config.getString("help-book.title", "Clan Help"));
    }

    public String getHelpBookAuthor() {
        return translateColors(config.getString("help-book.author", "Clan System"));
    }

    public List<String> getHelpBookPages() {
        List<String> pages = config.getStringList("help-book.pages");
        if (pages == null || pages.isEmpty()) {
            pages = new ArrayList<>();
            pages.add("&6&lClan Hilfe\n"
                    + "&7Erste Schritte:\n"
                    + "&f/clan create <tag>\n"
                    + "&f/clan invite <spieler>\n"
                    + "&f/clan join <tag>\n"
                    + "&f/clan leave\n"
                    + "&f/clan info\n"
                    + "&f/clan request <tag>\n"
                    + "&f/clan requests");
            pages.add("&6&lVerwaltung\n"
                    + "&f/clan kick <spieler>\n"
                    + "&f/clan promote <spieler>\n"
                    + "&f/clan demote [spieler]\n"
                    + "&f/clan leader <spieler>\n"
                    + "&f/clan rename <tag>\n"
                    + "&f/clan chest (set)\n"
                    + "&f/clan spawn\n"
                    + "&f/clan setspawn\n"
                    + "&f/clan delspawn\n"
                    + "&f/clan settings\n"
                    + "&f/clan skills\n"
                    + "&f/clan quest\n"
                    + "&f/clan logs");
        }
        return new ArrayList<>(pages);
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

    public int getMinPoints() {
        return config.getInt("min-points", 0);
    }

    public int getMaxPoints() {
        return config.getInt("max-points", 10000);
    }
}
