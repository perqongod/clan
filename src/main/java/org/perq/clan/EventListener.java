package org.perq.clan;

import org.bukkit.Bukkit;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class EventListener implements Listener {
    private final Clan plugin;
    private Map<UUID, Long> joinTimes = new HashMap<>();
    private static final int QUEST_SAVE_INTERVAL = 10;

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
        if (!ClanSkillProgress.hasChest(clan.getSkillPoints())) {
            event.setCancelled(true);
            player.closeInventory();
            player.sendMessage(plugin.getConfigManager().getMessage("skills-locked-chest")
                    .replace("%required%", String.valueOf(ClanSkillProgress.getChestUnlockPoints())));
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
        } catch (IOException e) {
            Bukkit.getLogger().log(Level.WARNING,
                    "[Clan] Failed to save clan chest contents for " + clanTag, e);
        }
    }

    @EventHandler
    public void onFriendlyFire(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();
        Player attacker = null;
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();
            if (projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
            }
        }
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;
        ClanData attackerClan = getPlayerClan(attacker.getUniqueId());
        ClanData victimClan = getPlayerClan(victim.getUniqueId());
        if (attackerClan == null || victimClan == null) return;
        if (!attackerClan.getTag().equalsIgnoreCase(victimClan.getTag())) return;
        ClanFriendlyFirePermission permission = attackerClan.getFriendlyFirePermission(attacker.getUniqueId());
        if (permission == ClanFriendlyFirePermission.DENY) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuestZombieKill(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Zombie)) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        ClanData clan = getPlayerClan(killer.getUniqueId());
        if (clan == null) return;
        int newCount = clan.getQuestZombieKillCount() + 1;
        clan.setQuestZombieKillCount(newCount);
        if (newCount % QUEST_SAVE_INTERVAL != 0) return;
        try {
            plugin.getFileManager().saveClan(clan);
        } catch (IOException e) {
            Bukkit.getLogger().log(Level.WARNING,
                    "[Clan] Failed to save quest progress for " + clan.getTag(), e);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Regular kill points
        if (killer != null) {
            ClanData killerClan = getPlayerClan(killer.getUniqueId());
            ClanData victimClan = getPlayerClan(victim.getUniqueId());
            boolean sameClan = killerClan != null && victimClan != null
                    && victimClan.getTag().equalsIgnoreCase(killerClan.getTag());
            if (killerClan != null && !sameClan) {
                applyKillPoints(killerClan);
                try {
                    plugin.getFileManager().saveClan(killerClan);
                } catch (IOException e) {
                    // ignore
                }
            }
            if (victimClan != null && killerClan != null && !sameClan) {
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
