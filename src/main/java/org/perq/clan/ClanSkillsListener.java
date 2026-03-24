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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClanSkillsListener implements Listener {
    private static final String TITLE = "Clan Progress";
    private static final int OVERVIEW_SLOT = 13;
    private static final int CHEST_SLOT = 10;
    private static final int SPAWN_SLOT = 12;
    private static final int BANK_SLOT = 14;
    private static final int MEMBERS_SLOT = 16;

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

        if (rawSlot != OVERVIEW_SLOT) return;

        Player player = (Player) event.getWhoClicked();
        ClanData clan = getPlayerClan(player.getUniqueId());
        if (clan == null) {
            player.closeInventory();
            return;
        }
        ConfigManager cm = plugin.getConfigManager();
        player.sendMessage(cm.getMessage("skills-auto-progress"));
    }

    private void populateInventory(Inventory inv, ClanData clan) {
        inv.clear();
        ConfigManager cm = plugin.getConfigManager();

        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        int points = clan.getPoints();
        int nextUnlock = ClanSkillProgress.getNextUnlockPoints(points);
        int bonusSlots = ClanSkillProgress.getBonusMemberSlots(points);

        List<String> overviewLore = new ArrayList<>();
        overviewLore.add(cm.translateColors("&7Clan Points: &f" + points));
        overviewLore.add(cm.translateColors("&7Next unlock at: &f" + nextUnlock));
        overviewLore.add(cm.translateColors("&7Next reward: &f" + ClanSkillProgress.getRewardLabel(points)));
        overviewLore.add(cm.translateColors("&eProgress is automatic"));
        inv.setItem(OVERVIEW_SLOT, namedItem(Material.NETHER_STAR, cm.translateColors("&6Clan Progress"), overviewLore));

        List<String> chestLore = new ArrayList<>();
        chestLore.add(cm.translateColors("&7Unlock points: &f" + ClanSkillProgress.getChestUnlockPoints()));
        chestLore.add(cm.translateColors(ClanSkillProgress.hasChest(points) ? "&aUnlocked" : "&cLocked"));
        inv.setItem(CHEST_SLOT, namedItem(Material.CHEST, cm.translateColors("&6Clan Chest"), chestLore));

        List<String> spawnLore = new ArrayList<>();
        spawnLore.add(cm.translateColors("&7Unlock points: &f" + ClanSkillProgress.getSpawnUnlockPoints()));
        spawnLore.add(cm.translateColors(ClanSkillProgress.hasSpawn(points) ? "&aUnlocked" : "&cLocked"));
        inv.setItem(SPAWN_SLOT, namedItem(Material.ENDER_EYE, cm.translateColors("&6Clan Spawn"), spawnLore));

        List<String> bankLore = new ArrayList<>();
        bankLore.add(cm.translateColors("&7Unlock points: &f" + ClanSkillProgress.getBankUnlockPoints()));
        bankLore.add(cm.translateColors(ClanSkillProgress.hasBank(points) ? "&aUnlocked" : "&cLocked"));
        inv.setItem(BANK_SLOT, namedItem(Material.GOLD_INGOT, cm.translateColors("&6Clan Bank"), bankLore));

        List<String> memberLore = new ArrayList<>();
        memberLore.add(cm.translateColors("&7Bonus slots: &f+" + bonusSlots));
        memberLore.add(cm.translateColors("&7Gain +1 member slot each " + ClanSkillProgress.getBankUnlockPoints() + "+ points"));
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
