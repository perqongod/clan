package org.perq.clan;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InviteData {
    private String fromClan;
    private long timestamp;
    private String requester;

    public InviteData(String fromClan) {
        this.fromClan = fromClan;
        this.timestamp = System.currentTimeMillis();
        this.requester = null;
    }

    public InviteData(String fromClan, long timestamp) {
        this.fromClan = fromClan;
        this.timestamp = timestamp;
        this.requester = null;
    }

    public InviteData(String fromClan, String requester) {
        this.fromClan = fromClan;
        this.timestamp = System.currentTimeMillis();
        this.requester = requester;
    }

    public InviteData(String fromClan, long timestamp, String requester) {
        this.fromClan = fromClan;
        this.timestamp = timestamp;
        this.requester = requester;
    }

    /** Legacy constructor – loads the first invite from file (backward compat). */
    public InviteData(File file) {
        List<InviteData> all = loadAllFromFile(file);
        if (!all.isEmpty()) {
            this.fromClan = all.get(0).fromClan;
            this.timestamp = all.get(0).timestamp;
            this.requester = all.get(0).requester;
        } else {
            this.fromClan = null;
            this.timestamp = 0L;
            this.requester = null;
        }
    }

    /** Legacy save – appends this invite to the file's list if not already present. */
    public void save(File file) throws IOException {
        List<InviteData> existing = loadAllFromFile(file);
        boolean found = false;
        for (InviteData inv : existing) {
            if (inv.getFromClan() != null && inv.getFromClan().equals(this.fromClan)) {
                if (inv.requester == null && this.requester == null) {
                    found = true;
                    break;
                }
                if (inv.requester != null && inv.requester.equals(this.requester)) {
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            existing.add(this);
        }
        saveAllToFile(file, existing);
    }

    public boolean isRequest() {
        return requester != null && !requester.isEmpty();
    }

    public String getRequester() {
        return requester;
    }

    // ── Static list helpers ──────────────────────────────────────────────────

    public static List<InviteData> loadAllFromFile(File file) {
        List<InviteData> invites = new ArrayList<>();
        if (!file.exists()) return invites;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<Map<?, ?>> list = config.getMapList("invites");
        if (!list.isEmpty()) {
            for (Map<?, ?> entry : list) {
                String clan = (String) entry.get("from");
                String requester = (String) entry.get("requester");
                Object ts = entry.get("timestamp");
                long timestamp = ts instanceof Number ? ((Number) ts).longValue() : 0L;
                invites.add(new InviteData(clan, timestamp, requester));
            }
        } else if (config.contains("from")) {
            // backward compat with old single-entry format
            invites.add(new InviteData(config.getString("from"), config.getLong("timestamp")));
        }
        return invites;
    }

    public static void saveAllToFile(File file, List<InviteData> invites) throws IOException {
        YamlConfiguration config = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (InviteData inv : invites) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("from", inv.fromClan);
            entry.put("timestamp", inv.timestamp);
            if (inv.requester != null) {
                entry.put("requester", inv.requester);
            }
            list.add(entry);
        }
        config.set("invites", list);
        config.save(file);
    }

    // ── Getters ──────────────────────────────────────────────────────────────
    public String getFromClan() { return fromClan; }
    public long getTimestamp() { return timestamp; }
}
