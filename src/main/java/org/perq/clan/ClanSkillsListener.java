package org.perq.clan;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClanSkillsListener implements Listener {
    private static final String TITLE = "Clan Battle Pass";
    private static final int UPGRADE_SLOT = 13;
    private static final int CHEST_SLOT = 11;
    private static final int SPAWN_SLOT = 15;
    private static final int MEMBERS_SLOT = 22;

    private final Clan plugin;

    public ClanSkillsListener(Clan plugin) {
        this.plugin = plugin;
    }

    public void openGui(Player player, ClanData clan) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        populateInventory(inv, clan);
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!event.getView().title().equals(Component.text(TITLE))) return;

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) return;

        event.setCancelled(true);

        if (rawSlot != UPGRADE_SLOT) return;

        Player player = (Player) event.getWhoClicked();
        ClanData clan = getPlayerClan(player.getUniqueId());
        if (clan == null) {
            player.closeInventory();
            return;
        }
        ConfigManager cm = plugin.getConfigManager();
        if (!clan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(cm.getMessage("not-clan-leader"));
            return;
        }

        int level = clan.getSkillLevel();
        int cost = ClanSkillProgress.getNextLevelCost(level);
        if (clan.getPoints() < cost) {
            player.sendMessage(cm.getMessage("skills-not-enough-points")
                    .replace("%cost%", String.valueOf(cost))
                    .replace("%points%", String.valueOf(clan.getPoints())));
            return;
        }

        clan.setPoints(clan.getPoints() - cost);
        clan.setSkillLevel(level + 1);
        clan.setRank(cm.getRankForPoints(clan.getPoints()));
        clan.addLog(player.getName() + " upgraded the clan battle pass to level " + clan.getSkillLevel() + ".");
        try {
            plugin.getFileManager().saveClan(clan);
            player.sendMessage(cm.getMessage("skills-upgraded")
                    .replace("%level%", String.valueOf(clan.getSkillLevel()))
                    .replace("%cost%", String.valueOf(cost)));
            populateInventory(event.getView().getTopInventory(), clan);
        } catch (IOException e) {
            player.sendMessage(cm.getPrefix() + "Error saving.");
        }
    }

    private void populateInventory(Inventory inv, ClanData clan) {
        inv.clear();
        ConfigManager cm = plugin.getConfigManager();

        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        int level = clan.getSkillLevel();
        int cost = ClanSkillProgress.getNextLevelCost(level);
        int bonusSlots = ClanSkillProgress.getBonusMemberSlots(level);

        List<String> upgradeLore = new ArrayList<>();
        upgradeLore.add(cm.translateColors("&7Level: &f" + level));
        upgradeLore.add(cm.translateColors("&7Clan Points: &f" + clan.getPoints()));
        upgradeLore.add(cm.translateColors("&7Next level cost: &f" + cost));
        upgradeLore.add(cm.translateColors("&7Next reward: &f" + ClanSkillProgress.getRewardLabel(level + 1)));
        upgradeLore.add(cm.translateColors("&eClick to upgrade"));
        inv.setItem(UPGRADE_SLOT, namedItem(Material.NETHER_STAR, cm.translateColors("&6Upgrade Battle Pass"), upgradeLore));

        List<String> chestLore = new ArrayList<>();
        chestLore.add(cm.translateColors("&7Unlock level: &f" + ClanSkillProgress.getChestUnlockLevel()));
        chestLore.add(cm.translateColors(ClanSkillProgress.hasChest(level) ? "&aUnlocked" : "&cLocked"));
        inv.setItem(CHEST_SLOT, namedItem(Material.CHEST, cm.translateColors("&6Clan Chest"), chestLore));

        List<String> spawnLore = new ArrayList<>();
        spawnLore.add(cm.translateColors("&7Unlock level: &f" + ClanSkillProgress.getSpawnUnlockLevel()));
        spawnLore.add(cm.translateColors(ClanSkillProgress.hasSpawn(level) ? "&aUnlocked" : "&cLocked"));
        inv.setItem(SPAWN_SLOT, namedItem(Material.ENDER_EYE, cm.translateColors("&6Clan Spawn"), spawnLore));

        List<String> memberLore = new ArrayList<>();
        memberLore.add(cm.translateColors("&7Bonus slots: &f+" + bonusSlots));
        memberLore.add(cm.translateColors("&7Gain +1 member slot each level after " + ClanSkillProgress.getSpawnUnlockLevel()));
        inv.setItem(MEMBERS_SLOT, namedItem(Material.PAPER, cm.translateColors("&6Member Slots"), memberLore));
    }

    private ItemStack namedItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack namedItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ClanData getPlayerClan(UUID player) {
        PlayerData p = plugin.getFileManager().loadPlayer(player);
        if (p == null || p.getClanTag() == null) return null;
        return plugin.getFileManager().loadClan(p.getClanTag());
    }
}
