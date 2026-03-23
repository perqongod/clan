package org.perq.clan;

import java.util.HashMap;
import java.util.Map;

public class WarManager {
    /** clanTag → pending war request sent TO that clan */
    private final Map<String, WarData> pendingWars = new HashMap<>();
    /** clanTag → active war this clan is participating in */
    private final Map<String, WarData> activeWars = new HashMap<>();
    /** clanTag → timestamp of last war end (for cooldown) */
    private final Map<String, Long> warCooldowns = new HashMap<>();

    public boolean hasPendingWar(String clanTag) {
        return pendingWars.containsKey(clanTag);
    }

    public WarData getPendingWar(String clanTag) {
        return pendingWars.get(clanTag);
    }

    public void addPendingWar(String targetClanTag, WarData war) {
        pendingWars.put(targetClanTag, war);
    }

    public void removePendingWar(String targetClanTag) {
        pendingWars.remove(targetClanTag);
    }

    public boolean hasActiveWar(String clanTag) {
        return activeWars.containsKey(clanTag);
    }

    public WarData getActiveWar(String clanTag) {
        return activeWars.get(clanTag);
    }

    public void addActiveWar(String clanTag, WarData war) {
        activeWars.put(clanTag, war);
    }

    public void removeActiveWar(String clanTag) {
        activeWars.remove(clanTag);
    }

    public boolean isOnCooldown(String clanTag, long cooldownMillis) {
        Long last = warCooldowns.get(clanTag);
        if (last == null) return false;
        return System.currentTimeMillis() - last < cooldownMillis;
    }

    public void setCooldown(String clanTag) {
        warCooldowns.put(clanTag, System.currentTimeMillis());
    }
}
