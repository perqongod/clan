package org.perq.clan;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClanLog {
    public enum Type {
        JOIN, LEAVE, KICK, WAR_WON, WAR_LOST
    }

    private final Type type;
    private final String message;
    private final long timestamp;

    public ClanLog(Type type, String message) {
        this.type = type;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }

    public ClanLog(Type type, String message, long timestamp) {
        this.type = type;
        this.message = message;
        this.timestamp = timestamp;
    }

    public Type getType() { return type; }
    public String getMessage() { return message; }
    public long getTimestamp() { return timestamp; }

    /** Returns formatted string: [HH:MM DD.MM.YYYY] <message> */
    public String format() {
        String time = new SimpleDateFormat("HH:mm dd.MM.yyyy").format(new Date(timestamp));
        return "[" + time + "] " + message;
    }

    public static List<ClanLog> loadFromFile(File file) {
        List<ClanLog> logs = new ArrayList<>();
        if (!file.exists()) return logs;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<Map<?, ?>> list = config.getMapList("logs");
        for (Map<?, ?> entry : list) {
            String typeStr = (String) entry.get("type");
            String msg = (String) entry.get("message");
            Object ts = entry.get("timestamp");
            long ts2 = ts instanceof Number ? ((Number) ts).longValue() : 0L;
            try {
                logs.add(new ClanLog(Type.valueOf(typeStr), msg, ts2));
            } catch (IllegalArgumentException ignored) {
                // skip unknown log types
            }
        }
        return logs;
    }

    public static void saveToFile(File file, List<ClanLog> logs) throws IOException {
        YamlConfiguration config = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (ClanLog log : logs) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("type", log.type.name());
            entry.put("message", log.message);
            entry.put("timestamp", log.timestamp);
            list.add(entry);
        }
        config.set("logs", list);
        config.save(file);
    }
}
