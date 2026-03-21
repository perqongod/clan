package org.perq.clan;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EventListener implements Listener {
    private final Clan plugin;
    private Map<UUID, Long> joinTimes = new HashMap<>();

    public EventListener(Clan plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer != null) {
            ClanData clan = getPlayerClan(killer.getUniqueId());
            if (clan != null) {
                clan.setPoints(clan.getPoints() + plugin.getConfigManager().getKillPoints());
                clan.setRank(getRank(clan.getPoints()));
                try {
                    plugin.getFileManager().saveClan(clan);
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        joinTimes.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID player = event.getPlayer().getUniqueId();
        Long joinTime = joinTimes.remove(player);
        if (joinTime != null) {
            double hours = (System.currentTimeMillis() - joinTime) / 3600000.0;
            ClanData clan = getPlayerClan(player);
            if (clan != null) {
                clan.setOnlineTime(clan.getOnlineTime() + hours);
                try {
                    plugin.getFileManager().saveClan(clan);
                } catch (IOException e) {}
            }
            PlayerData pData = plugin.getFileManager().loadPlayer(player);
            if (pData != null) {
                pData.setOnlineTime(pData.getOnlineTime() + hours);
                try {
                    plugin.getFileManager().savePlayer(player, pData);
                } catch (IOException e) {}
            }
        }
    }

    private ClanData getPlayerClan(UUID player) {
        PlayerData p = plugin.getFileManager().loadPlayer(player);
        if (p == null || p.getClanTag() == null) return null;
        return plugin.getFileManager().loadClan(p.getClanTag());
    }

    private String getRank(int points) {
        Map<String, Integer> ranks = plugin.getConfigManager().getRanks();
        String currentRank = "Bronze";
        for (Map.Entry<String, Integer> entry : ranks.entrySet()) {
            if (points >= entry.getValue()) {
                currentRank = entry.getKey();
            } else {
                break;
            }
        }
        return currentRank;
    }
}
