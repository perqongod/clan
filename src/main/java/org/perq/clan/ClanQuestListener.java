package org.perq.clan;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
    private static final String DEFAULT_TITLE = "Clan Quests";
    private static final String DEFAULT_OVERVIEW_NAME = "&6Clan Quests";
    private static final String DEFAULT_PREVIOUS_NAME = "&ePrevious";
    private static final String DEFAULT_NEXT_NAME = "&eNext";
    private static final String CONFIG_NAV_PREVIOUS_NAME = "quest-gui.navigation.previous.name";
    private static final String CONFIG_NAV_NEXT_NAME = "quest-gui.navigation.next.name";
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
        ConfigManager cm = plugin.getConfigManager();
        Inventory inv = Bukkit.createInventory(null, INVENTORY_SIZE, getQuestTitle(cm));
        pages.put(player.getUniqueId(), 0);
        populateQuestInventory(inv, clan, player.getUniqueId());
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        ConfigManager cm = plugin.getConfigManager();
        if (!event.getView().title().equals(getQuestTitle(cm))) return;

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
            player.sendMessage(plugin.getConfigManager().getMessage("quest-info"));
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
            populateQuestInventory(event.getView().getTopInventory(), clan, player.getUniqueId());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        ConfigManager cm = plugin.getConfigManager();
        if (!event.getView().title().equals(getQuestTitle(cm))) return;
        pages.remove(event.getPlayer().getUniqueId());
    }

    private void populateQuestInventory(Inventory inv, ClanData clan, UUID viewer) {
        inv.clear();
        ConfigManager cm = plugin.getConfigManager();

        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        Map<ClanQuestProgress.QuestTarget, Integer> killCounts = clan.getQuestKillCounts();
        int questLevel = ClanQuestProgress.getQuestLevel(killCounts);
        int questPoints = clan.getQuestSkillPoints();
        int completedQuests = ClanQuestProgress.getCompletedQuestCount(killCounts);
        int totalQuests = ClanQuestProgress.getTotalQuestCount();
        String questInfo = cm.getMessage("quest-info");
        String prefix = cm.getPrefix();
        if (questInfo.startsWith(prefix)) {
            questInfo = questInfo.substring(prefix.length()).trim();
        }

        List<String> overviewLore = new ArrayList<>();
        overviewLore.add(cm.translateColors("&7Quest level: &f" + questLevel));
        overviewLore.add(cm.translateColors("&7Completed quests: &f" + completedQuests + "/" + totalQuests));
        overviewLore.add(cm.translateColors("&7Quest skill points: &f" + questPoints));
        overviewLore.add(questInfo);
        inv.setItem(OVERVIEW_SLOT, namedItem(Material.NETHER_STAR, getQuestOverviewName(cm), overviewLore));

        List<ItemStack> questEntries = buildQuestEntries(clan, cm);
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
                inv.setItem(PREVIOUS_PAGE_SLOT, arrowItem(getQuestNavigationName(cm,
                        CONFIG_NAV_PREVIOUS_NAME, DEFAULT_PREVIOUS_NAME)));
            }
            if (page < totalPages - 1) {
                inv.setItem(NEXT_PAGE_SLOT, arrowItem(getQuestNavigationName(cm,
                        CONFIG_NAV_NEXT_NAME, DEFAULT_NEXT_NAME)));
            }
        }
    }

    private int getTotalPages(ClanData clan) {
        int questCount = buildQuestEntries(clan, plugin.getConfigManager()).size();
        return Math.max(1, (questCount + QUEST_ROW_SIZE - 1) / QUEST_ROW_SIZE);
    }

    private List<ItemStack> buildQuestEntries(ClanData clan, ConfigManager cm) {
        List<ItemStack> entries = new ArrayList<>();

        List<String> levelOneLore = new ArrayList<>();
        levelOneLore.add(cm.translateColors("&7Unlocked"));
        entries.add(namedItem(Material.BOOK, cm.translateColors("&6Quest Level 1"), levelOneLore));

        for (ClanQuestProgress.QuestDefinition quest : ClanQuestProgress.getQuestDefinitions()) {
            int kills = clan.getQuestKillCount(quest.getTarget());
            List<String> lore = new ArrayList<>();
            lore.add(cm.translateColors("&7Task: &fKill " + quest.getRequiredKills() + " " + quest.getTarget().getDisplayName()));
            lore.add(cm.translateColors("&7Progress: &f" + kills + "/" + quest.getRequiredKills()));
            lore.add(cm.translateColors("&7Reward: &f" + quest.getRewardPoints() + " quest points"));
            lore.add(cm.translateColors(kills >= quest.getRequiredKills() ? "&aUnlocked" : "&cLocked"));
            entries.add(namedItem(quest.getTarget().getIcon(),
                    cm.translateColors("&6Quest Level " + quest.getLevel() + " - " + quest.getTarget().getDisplayName()), lore));
        }

        return entries;
    }

    private ItemStack arrowItem(String name) {
        return namedItem(Material.ARROW, name);
    }

    private Component getQuestTitle(ConfigManager cm) {
        String title = plugin.getConfig().getString("quest-gui.title", DEFAULT_TITLE);
        return LegacyComponentSerializer.legacySection().deserialize(cm.translateColors(title));
    }

    private String getQuestOverviewName(ConfigManager cm) {
        String name = plugin.getConfig().getString("quest-gui.overview.name", DEFAULT_OVERVIEW_NAME);
        return cm.translateColors(name);
    }

    private String getQuestNavigationName(ConfigManager cm, String path, String defaultName) {
        String name = plugin.getConfig().getString(path, defaultName);
        return cm.translateColors(name);
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
