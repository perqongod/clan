package org.perq.clan;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

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
    public void onClanChestClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        @SuppressWarnings("deprecation")
        String title = event.getView().getTitle();
        if (!title.startsWith("Clan Chest: ")) return;

        String clanTag = title.substring("Clan Chest: ".length());
        ClanData clan = plugin.getFileManager().loadClan(clanTag);
        if (clan == null) return;
        if (!ClanSkillProgress.hasChest(clan.getPoints())) {
            event.setCancelled(true);
            player.closeInventory();
            player.sendMessage(plugin.getConfigManager().getMessage("skills-locked-chest"));
            return;
        }

        UUID playerUUID = player.getUniqueId();

        // Leaders always have full access
        if (clan.getLeader().equals(playerUUID)) return;

        // Determine if this interaction affects the clan chest contents
        int topSize = event.getView().getTopInventory().getSize();
        boolean clickedInChest = event.getRawSlot() >= 0 && event.getRawSlot() < topSize;
        boolean shiftClickFromInventory = event.isShiftClick() && !clickedInChest;

        if (clickedInChest || shiftClickFromInventory) {
            ClanChestPermission permission = clan.getChestPermission(playerUUID);
            if (permission != ClanChestPermission.EXECUTE) {
                event.setCancelled(true);
                player.sendMessage(plugin.getConfigManager().getMessage("leader-disallow"));
            }
        }
    }

    @EventHandler
    public void onClanBankClick(InventoryClickEvent event) {
        @SuppressWarnings("deprecation")
        String title = event.getView().getTitle();
        if (!title.startsWith("Clan Bank: ")) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onClanChestClose(InventoryCloseEvent event) {
        @SuppressWarnings("deprecation")
        String title = event.getView().getTitle();
        if (!title.startsWith("Clan Chest: ")) return;
        String clanTag = title.substring("Clan Chest: ".length());
        ClanData clan = plugin.getFileManager().loadClan(clanTag);
        if (clan == null) return;
        clan.setChestContents(event.getView().getTopInventory().getContents());
        try {
            plugin.getFileManager().saveClan(clan);
        } catch (IOException ignored) {
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof Player)) return;
        Player attacker = (Player) event.getDamager();
        Player victim = (Player) event.getEntity();

        WarManager warManager = plugin.getWarManager();
        WarManager.ActiveWar war = warManager.getActiveWar(getPlayerClanTag(attacker.getUniqueId()));
        if (war == null || (war.getState() != WarManager.WarState.COUNTDOWN && war.getState() != WarManager.WarState.ACTIVE)) return;
        if (!war.isJoinedPlayer(attacker.getUniqueId()) || !war.isJoinedPlayer(victim.getUniqueId())) return;

        // Check if both are in the same team (friendly fire)
        boolean sameTeam = (war.isInTeamA(attacker.getUniqueId()) && war.isInTeamA(victim.getUniqueId()))
                || (war.isInTeamB(attacker.getUniqueId()) && war.isInTeamB(victim.getUniqueId()));

        if (sameTeam) {
            event.setCancelled(true);
            attacker.sendMessage(plugin.getConfigManager().getMessage("war-friendly-fire"));
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // War death handling
        WarManager warManager = plugin.getWarManager();
        WarManager.ActiveWar war = warManager.getActiveWar(getPlayerClanTag(victim.getUniqueId()));
        if (war != null && war.getState() == WarManager.WarState.ACTIVE) {
            warManager.handlePlayerDeath(victim, killer);
            if (killer != null && war.isActivePlayer(killer.getUniqueId())
                    && war.isOpposingTeams(killer.getUniqueId(), victim.getUniqueId())) {
                String killerTeamTag = war.isInTeamA(killer.getUniqueId()) ? war.getClanTagA() : war.getClanTagB();
                ClanData killerClan = plugin.getFileManager().loadClan(killerTeamTag);
                if (killerClan != null) {
                    killerClan.addLog(killer.getName() + " defeated " + victim.getName() + " in a clan war.");
                    applyKillPoints(killerClan);
                    try { plugin.getFileManager().saveClan(killerClan); } catch (IOException ignored) {}
                }
                ClanData victimClan = getPlayerClan(victim.getUniqueId());
                if (victimClan != null && killerClan != null
                        && !victimClan.getTag().equalsIgnoreCase(killerClan.getTag())) {
                    applyDeathPenalty(victimClan);
                    try { plugin.getFileManager().saveClan(victimClan); } catch (IOException ignored) {}
                }
            }
            victim.setGameMode(GameMode.SPECTATOR);
            return; // Don't award regular kill points during war
        }

        // Regular kill points (not in war)
        if (killer != null) {
            ClanData killerClan = getPlayerClan(killer.getUniqueId());
            ClanData victimClan = getPlayerClan(victim.getUniqueId());
            if (killerClan != null) {
                applyKillPoints(killerClan);
                try {
                    plugin.getFileManager().saveClan(killerClan);
                } catch (IOException e) {
                    // ignore
                }
            }
            if (victimClan != null && killerClan != null
                    && !victimClan.getTag().equalsIgnoreCase(killerClan.getTag())) {
                applyDeathPenalty(victimClan);
                try {
                    plugin.getFileManager().saveClan(victimClan);
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
        plugin.getWarManager().handleDisconnect(player);
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

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        WarManager.ActiveWar war = plugin.getWarManager().getActiveWarByPlayer(playerUUID);
        if (war == null || !war.isDead(playerUUID)) return;
        if (war.getDeathLocation(playerUUID) != null) {
            event.setRespawnLocation(war.getDeathLocation(playerUUID));
        }
        Bukkit.getScheduler().runTask(plugin, () -> event.getPlayer().setGameMode(GameMode.SPECTATOR));
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        WarManager.ActiveWar war = plugin.getWarManager().getActiveWarByPlayer(event.getPlayer().getUniqueId());
        if (war == null) return;
        if (war.getState() == WarManager.WarState.ACTIVE || war.getState() == WarManager.WarState.COUNTDOWN) {
            String base = event.getMessage().split(" ")[0].toLowerCase();
            if (plugin.getConfigManager().getWarCommandAllowlist().contains(base)) {
                return;
            }
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getConfigManager().getMessage("war-commands-blocked"));
        }
    }

    private String getPlayerClanTag(UUID player) {
        PlayerData p = plugin.getFileManager().loadPlayer(player);
        return (p == null) ? null : p.getClanTag();
    }

    private ClanData getPlayerClan(UUID player) {
        PlayerData p = plugin.getFileManager().loadPlayer(player);
        if (p == null || p.getClanTag() == null) return null;
        return plugin.getFileManager().loadClan(p.getClanTag());
    }

    private void applyKillPoints(ClanData clan) {
        int killPoints = plugin.getConfigManager().getKillPoints();
        if (killPoints <= 0) return;
        applyPoints(clan, killPoints);
    }

    private void applyDeathPenalty(ClanData clan) {
        int killPoints = plugin.getConfigManager().getKillPoints();
        if (killPoints <= 0) return;
        applyPoints(clan, -killPoints);
    }

    private void applyPoints(ClanData clan, int delta) {
        ConfigManager cm = plugin.getConfigManager();
        int minPoints = cm.getMinPoints();
        int maxPoints = cm.getMaxPoints();
        int newPoints = Math.max(minPoints, Math.min(maxPoints, clan.getPoints() + delta));
        clan.setPoints(newPoints);
        clan.setRank(cm.getRankForPoints(newPoints));
    }

}
