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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ClanQuestListener implements Listener {
    private static final String DEFAULT_TITLE = "clan quests";
    private static final String DEFAULT_OVERVIEW_NAME = "&6clan quests";
    private static final String DEFAULT_PREVIOUS_NAME = "&eprevious";
    private static final String DEFAULT_NEXT_NAME = "&enext";
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
        Inventory inv = Bukkit.createInventory(null, INVENTORY_SIZE,
                cm.getComponent("quest-gui.title", DEFAULT_TITLE));
        pages.put(player.getUniqueId(), 0);
        populateQuestInventory(inv, clan, player.getUniqueId());
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        ConfigManager cm = plugin.getConfigManager();
        if (!event.getView().title().equals(cm.getComponent("quest-gui.title", DEFAULT_TITLE))) return;

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
            int maxPoints = cm.getMaxPoints();
            int questRedeemCost = cm.getQuestRedeemCost();
            int clanPointsReward = cm.getQuestRedeemReward();
            if (clanPointsReward <= 0) {
                player.sendMessage(cm.getMessage("quest-redeem-disabled"));
                return;
            }
            int currentPoints = clan.getPoints();
            if (currentPoints >= maxPoints) {
                player.sendMessage(cm.getMessage("quest-redeem-max")
                        .replace("%max%", String.valueOf(maxPoints)));
                return;
            }
            int redeemablePoints = clan.getRedeemableQuestPoints();
            if (redeemablePoints < questRedeemCost) {
                String message = cm.getMessage("quest-redeem-not-enough")
                        .replace("%required%", String.valueOf(questRedeemCost))
                        .replace("%available%", String.valueOf(redeemablePoints));
                player.sendMessage(message);
                return;
            }
            int awardedPoints = calculateAwardedPoints(currentPoints, clanPointsReward, maxPoints);
            int newPoints = currentPoints + awardedPoints;
            int previousRedeemed = clan.getQuestPointsRedeemed();
            String previousRank = clan.getRank();
            clan.setQuestPointsRedeemed(previousRedeemed + questRedeemCost);
            clan.setPoints(newPoints);
            clan.setRank(cm.getRankForPoints(newPoints));
            try {
                plugin.getFileManager().saveClan(clan);
            } catch (Exception e) {
                clan.setQuestPointsRedeemed(previousRedeemed);
                clan.setPoints(currentPoints);
                clan.setRank(previousRank);
                Bukkit.getLogger().log(java.util.logging.Level.WARNING, e,
                        () -> "[Clan] Failed to redeem quest points for " + clan.getTag());
                player.sendMessage(cm.getMessage("quest-redeem-failed"));
                populateQuestInventory(event.getView().getTopInventory(), clan, player.getUniqueId());
                return;
            }
            String message = cm.getMessage("quest-redeem-success")
                    .replace("%quest_points%", String.valueOf(questRedeemCost))
                    .replace("%clan_points%", String.valueOf(awardedPoints))
                    .replace("%total%", String.valueOf(newPoints));
            player.sendMessage(message);
            populateQuestInventory(event.getView().getTopInventory(), clan, player.getUniqueId());
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
        if (!event.getView().title().equals(cm.getComponent("quest-gui.title", DEFAULT_TITLE))) return;
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
        int redeemableQuestPoints = clan.getRedeemableQuestPoints();
        int completedQuests = ClanQuestProgress.getCompletedQuestCount(killCounts);
        int totalQuests = ClanQuestProgress.getTotalQuestCount();
        String questInfo = cm.getMessage("quest-info")
                .replace("%quest_cost%", String.valueOf(cm.getQuestRedeemCost()))
                .replace("%clan_reward%", String.valueOf(cm.getQuestRedeemReward()));
        String prefix = cm.getPrefix();
        if (questInfo.startsWith(prefix)) {
            questInfo = questInfo.substring(prefix.length()).trim();
        }

        List<String> overviewLore = new ArrayList<>();
        overviewLore.add(cm.translateColors("&7quest level: &f" + questLevel));
        overviewLore.add(cm.translateColors("&7completed quests: &f" + completedQuests + "/" + totalQuests));
        overviewLore.add(cm.translateColors("&7quest skill points: &f" + questPoints));
        overviewLore.add(cm.translateColors("&7redeemable quest points: &f" + redeemableQuestPoints));
        overviewLore.add(questInfo);
        inv.setItem(OVERVIEW_SLOT, namedItem(Material.NETHER_STAR,
                cm.getConfigString("quest-gui.overview.name", DEFAULT_OVERVIEW_NAME), overviewLore));

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
            String previousName = cm.getConfigString(CONFIG_NAV_PREVIOUS_NAME, DEFAULT_PREVIOUS_NAME);
            String nextName = cm.getConfigString(CONFIG_NAV_NEXT_NAME, DEFAULT_NEXT_NAME);
            if (page > 0) {
                inv.setItem(PREVIOUS_PAGE_SLOT, arrowItem(previousName));
            }
            if (page < totalPages - 1) {
                inv.setItem(NEXT_PAGE_SLOT, arrowItem(nextName));
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
        levelOneLore.add(cm.translateColors("&7unlocked"));
        entries.add(namedItem(Material.BOOK, cm.translateColors("&6quest level 1"), levelOneLore));

        for (ClanQuestProgress.QuestDefinition quest : ClanQuestProgress.getQuestDefinitions()) {
            int kills = clan.getQuestKillCount(quest.getTarget());
            String targetName = quest.getTarget().getDisplayName().toLowerCase(Locale.ENGLISH);
            List<String> lore = new ArrayList<>();
            lore.add(cm.translateColors("&7task: &fkill " + quest.getRequiredKills() + " " + targetName));
            lore.add(cm.translateColors("&7progress: &f" + kills + "/" + quest.getRequiredKills()));
            lore.add(cm.translateColors("&7reward: &f" + quest.getRewardPoints() + " quest points"));
            lore.add(cm.translateColors(kills >= quest.getRequiredKills() ? "&aunlocked" : "&clocked"));
            entries.add(namedItem(quest.getTarget().getIcon(),
                    cm.translateColors("&6quest level " + quest.getLevel() + " - " + targetName), lore));
        }

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

    private int calculateAwardedPoints(int currentPoints, int reward, int maxPoints) {
        return Math.min(maxPoints, currentPoints + reward) - currentPoints;
    }

    private ClanData getPlayerClan(UUID player) {
        PlayerData p = plugin.getFileManager().loadPlayer(player);
        if (p == null || p.getClanTag() == null) return null;
        return plugin.getFileManager().loadClan(p.getClanTag());
    }
}
