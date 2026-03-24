package org.perq.clan;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ClanSkillsListener implements Listener {
    private static final String TITLE = "Clan Progress";
    private static final int INVENTORY_SIZE = 27;
    private static final int OVERVIEW_SLOT = 22;
    private static final int MEMBERS_SLOT = 24;
    private static final int PREVIOUS_PAGE_SLOT = 0;
    private static final int NEXT_PAGE_SLOT = 8;
    private static final int SKILL_ROW_START = 9;
    private static final int SKILL_ROW_SIZE = 9;

    private final Clan plugin;
    private final Map<UUID, Integer> pages = new HashMap<>();

    public ClanSkillsListener(Clan plugin) {
        this.plugin = plugin;
    }

    public void openGui(Player player, ClanData clan) {
        Inventory inv = Bukkit.createInventory(null, INVENTORY_SIZE, TITLE);
        pages.put(player.getUniqueId(), 0);
        populateInventory(inv, clan, player.getUniqueId());
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!event.getView().title().equals(Component.text(TITLE))) return;

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) return;

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ClanData clan = getPlayerClan(player.getUniqueId());
        if (clan == null) {
            player.closeInventory();
            return;
        }
        if (rawSlot == OVERVIEW_SLOT) {
            ConfigManager cm = plugin.getConfigManager();
            player.sendMessage(cm.getMessage("skills-auto-progress"));
            return;
        }
        if (rawSlot == PREVIOUS_PAGE_SLOT || rawSlot == NEXT_PAGE_SLOT) {
            ConfigManager cm = plugin.getConfigManager();
            int totalPages = getTotalPages(clan, cm);
            int currentPage = pages.getOrDefault(player.getUniqueId(), 0);
            if (rawSlot == PREVIOUS_PAGE_SLOT && currentPage > 0) {
                currentPage--;
            } else if (rawSlot == NEXT_PAGE_SLOT && currentPage < totalPages - 1) {
                currentPage++;
            } else {
                return;
            }
            pages.put(player.getUniqueId(), currentPage);
            populateInventory(event.getView().getTopInventory(), clan, player.getUniqueId());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getView().title().equals(Component.text(TITLE))) return;
        pages.remove(event.getPlayer().getUniqueId());
    }

    private void populateInventory(Inventory inv, ClanData clan, UUID viewer) {
        inv.clear();
        ConfigManager cm = plugin.getConfigManager();

        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        int points = clan.getSkillPoints();
        int nextUnlock = ClanSkillProgress.getNextUnlockPoints(points);
        int bonusSlots = ClanSkillProgress.getBonusMemberSlots(points);

        List<String> overviewLore = new ArrayList<>();
        overviewLore.add(cm.translateColors("&7Skill Points (for unlocks): &f" + points));
        overviewLore.add(cm.translateColors("&7Ranking Points (for leaderboard): &f" + clan.getPoints()));
        overviewLore.add(cm.translateColors("&7Next unlock at: &f" + nextUnlock));
        overviewLore.add(cm.translateColors("&7Next reward: &f" + ClanSkillProgress.getRewardLabel(points)));
        overviewLore.add(cm.translateColors("&eProgress is automatic"));
        inv.setItem(OVERVIEW_SLOT, namedItem(Material.NETHER_STAR, cm.translateColors("&6Clan Progress"), overviewLore));

        List<ItemStack> skillEntries = buildSkillEntries(points, cm);
        int totalPages = Math.max(1, (skillEntries.size() + SKILL_ROW_SIZE - 1) / SKILL_ROW_SIZE);
        int page = Math.min(pages.getOrDefault(viewer, 0), totalPages - 1);
        pages.put(viewer, page);

        int startIndex = page * SKILL_ROW_SIZE;
        for (int i = 0; i < SKILL_ROW_SIZE; i++) {
            int index = startIndex + i;
            if (index >= skillEntries.size()) break;
            inv.setItem(SKILL_ROW_START + i, skillEntries.get(index));
        }

        if (totalPages > 1) {
            if (page > 0) {
                inv.setItem(PREVIOUS_PAGE_SLOT, arrowItem(cm.translateColors("&ePrevious")));
            }
            if (page < totalPages - 1) {
                inv.setItem(NEXT_PAGE_SLOT, arrowItem(cm.translateColors("&eNext")));
            }
        }

        List<String> memberLore = new ArrayList<>();
        memberLore.add(cm.translateColors("&7Bonus slots: &f+" + bonusSlots));
        memberLore.add(cm.translateColors("&7Gain +1 member slot every " + ClanSkillProgress.getBonusSlotStep()
                + " points after " + ClanSkillProgress.getSpawnUnlockPoints()));
        inv.setItem(MEMBERS_SLOT, namedItem(Material.PAPER, cm.translateColors("&6Member Slots"), memberLore));
    }

    private int getTotalPages(ClanData clan, ConfigManager cm) {
        int skillCount = buildSkillEntries(clan.getSkillPoints(), cm).size();
        return Math.max(1, (skillCount + SKILL_ROW_SIZE - 1) / SKILL_ROW_SIZE);
    }

    private List<ItemStack> buildSkillEntries(int points, ConfigManager cm) {
        List<ItemStack> entries = new ArrayList<>();

        List<String> chestLore = new ArrayList<>();
        chestLore.add(cm.translateColors("&7Unlock points: &f" + ClanSkillProgress.getChestUnlockPoints()));
        chestLore.add(cm.translateColors(ClanSkillProgress.hasChest(points) ? "&aUnlocked" : "&cLocked"));
        entries.add(namedItem(Material.CHEST, cm.translateColors("&6Clan Chest"), chestLore));

        List<String> spawnLore = new ArrayList<>();
        spawnLore.add(cm.translateColors("&7Unlock points: &f" + ClanSkillProgress.getSpawnUnlockPoints()));
        spawnLore.add(cm.translateColors(ClanSkillProgress.hasSpawn(points) ? "&aUnlocked" : "&cLocked"));
        entries.add(namedItem(Material.ENDER_EYE, cm.translateColors("&6Clan Spawn"), spawnLore));

        List<String> renameLore = new ArrayList<>();
        renameLore.add(cm.translateColors("&7Unlock points: &f" + ClanSkillProgress.getRenameUnlockPoints()));
        renameLore.add(cm.translateColors(ClanSkillProgress.hasRename(points) ? "&aUnlocked" : "&cLocked"));
        entries.add(namedItem(Material.NAME_TAG, cm.translateColors("&6Clan Rename"), renameLore));

        return entries;
    }

    private ItemStack arrowItem(String name) {
        return namedItem(Material.ARROW, name);
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
