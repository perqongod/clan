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

public class ClanQuestListener implements Listener {
    private static final String TITLE = "Clan Quests";
    private static final int INVENTORY_SIZE = 27;
    private static final int OVERVIEW_SLOT = 22;
    private static final int PREVIOUS_PAGE_SLOT = 0;
    private static final int NEXT_PAGE_SLOT = 8;
    private static final int QUEST_ROW_START = 9;
    private static final int QUEST_ROW_SIZE = 9;

    private final Clan plugin;
    private final Map<UUID, Integer> pages = new HashMap<>();

    public ClanQuestListener(Clan plugin) {
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
            player.sendMessage(plugin.getConfigManager().getPrefix()
                    + "Quest points affect clan skills only.");
            return;
        }
        if (rawSlot == PREVIOUS_PAGE_SLOT || rawSlot == NEXT_PAGE_SLOT) {
            int totalPages = getTotalPages(clan);
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

        int zombieKills = clan.getQuestZombieKills();
        int questLevel = ClanQuestProgress.getQuestLevel(zombieKills);
        int questPoints = clan.getQuestSkillPoints();

        List<String> overviewLore = new ArrayList<>();
        overviewLore.add(cm.translateColors("&7Quest level: &f" + questLevel));
        overviewLore.add(cm.translateColors("&7Zombie kills: &f" + zombieKills));
        overviewLore.add(cm.translateColors("&7Quest skill points: &f" + questPoints));
        overviewLore.add(cm.translateColors("&eQuest points do not affect ranking"));
        inv.setItem(OVERVIEW_SLOT, namedItem(Material.NETHER_STAR, cm.translateColors("&6Clan Quests"), overviewLore));

        List<ItemStack> questEntries = buildQuestEntries(zombieKills, cm);
        int totalPages = Math.max(1, (questEntries.size() + QUEST_ROW_SIZE - 1) / QUEST_ROW_SIZE);
        int page = Math.min(pages.getOrDefault(viewer, 0), totalPages - 1);
        pages.put(viewer, page);

        int startIndex = page * QUEST_ROW_SIZE;
        for (int i = 0; i < QUEST_ROW_SIZE; i++) {
            int index = startIndex + i;
            if (index >= questEntries.size()) break;
            inv.setItem(QUEST_ROW_START + i, questEntries.get(index));
        }

        if (totalPages > 1) {
            if (page > 0) {
                inv.setItem(PREVIOUS_PAGE_SLOT, arrowItem(cm.translateColors("&ePrevious")));
            }
            if (page < totalPages - 1) {
                inv.setItem(NEXT_PAGE_SLOT, arrowItem(cm.translateColors("&eNext")));
            }
        }
    }

    private int getTotalPages(ClanData clan) {
        int questCount = buildQuestEntries(clan.getQuestZombieKills(), plugin.getConfigManager()).size();
        return Math.max(1, (questCount + QUEST_ROW_SIZE - 1) / QUEST_ROW_SIZE);
    }

    private List<ItemStack> buildQuestEntries(int zombieKills, ConfigManager cm) {
        List<ItemStack> entries = new ArrayList<>();

        List<String> levelOneLore = new ArrayList<>();
        levelOneLore.add(cm.translateColors("&7Unlocked"));
        entries.add(namedItem(Material.BOOK, cm.translateColors("&6Quest Level 1"), levelOneLore));

        int requiredKills = ClanQuestProgress.getLevel2ZombieKills();
        List<String> levelTwoLore = new ArrayList<>();
        levelTwoLore.add(cm.translateColors("&7Task: &fKill " + requiredKills + " Zombies"));
        levelTwoLore.add(cm.translateColors("&7Progress: &f" + zombieKills + "/" + requiredKills));
        levelTwoLore.add(cm.translateColors(zombieKills >= requiredKills ? "&aUnlocked" : "&cLocked"));
        entries.add(namedItem(Material.ZOMBIE_HEAD, cm.translateColors("&6Quest Level 2"), levelTwoLore));

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
