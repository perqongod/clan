package org.perq.clan;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
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
    private static final int DEFAULT_ROWS = 3;
    private static final int DEFAULT_OVERVIEW_SLOT = 22;
    private static final int DEFAULT_PREVIOUS_PAGE_SLOT = 0;
    private static final int DEFAULT_NEXT_PAGE_SLOT = 8;
    private static final int DEFAULT_QUEST_ROW_START = 9;
    private static final int DEFAULT_QUEST_ROW_SIZE = 9;

    private final Clan plugin;
    private final Map<UUID, Integer> pages = new HashMap<>();

    public ClanQuestListener(Clan plugin) {
        this.plugin = plugin;
    }

    public void openGui(Player player, ClanData clan) {
        ConfigManager cm = plugin.getConfigManager();
        QuestGuiLayout layout = loadQuestGuiLayout(cm);
        Inventory inv = Bukkit.createInventory(null, layout.size, layout.title);
        pages.put(player.getUniqueId(), 0);
        populateQuestInventory(inv, clan, player.getUniqueId(), layout);
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        ConfigManager cm = plugin.getConfigManager();
        QuestGuiLayout layout = loadQuestGuiLayout(cm);
        if (!event.getView().title().equals(layout.title)) return;

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) return;

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ClanData clan = getPlayerClan(player.getUniqueId());
        if (clan == null) {
            player.closeInventory();
            return;
        }
        if (rawSlot == layout.overviewSlot) {
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
                populateQuestInventory(event.getView().getTopInventory(), clan, player.getUniqueId(), layout);
                return;
            }
            String message = cm.getMessage("quest-redeem-success")
                    .replace("%quest_points%", String.valueOf(questRedeemCost))
                    .replace("%clan_points%", String.valueOf(awardedPoints))
                    .replace("%total%", String.valueOf(newPoints));
            player.sendMessage(message);
            populateQuestInventory(event.getView().getTopInventory(), clan, player.getUniqueId(), layout);
            return;
        }
        if (rawSlot == layout.previousSlot || rawSlot == layout.nextSlot) {
            int totalPages = getTotalPages(clan, layout);
            int currentPage = pages.getOrDefault(player.getUniqueId(), 0);
            if (rawSlot == layout.previousSlot && currentPage > 0) {
                currentPage--;
            } else if (rawSlot == layout.nextSlot && currentPage < totalPages - 1) {
                currentPage++;
            } else {
                return;
            }
            pages.put(player.getUniqueId(), currentPage);
            populateQuestInventory(event.getView().getTopInventory(), clan, player.getUniqueId(), layout);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        ConfigManager cm = plugin.getConfigManager();
        QuestGuiLayout layout = loadQuestGuiLayout(cm);
        if (!event.getView().title().equals(layout.title)) return;
        pages.remove(event.getPlayer().getUniqueId());
    }

    private void populateQuestInventory(Inventory inv, ClanData clan, UUID viewer, QuestGuiLayout layout) {
        inv.clear();
        ConfigManager cm = plugin.getConfigManager();

        for (Integer slot : layout.fillerSlots) {
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, layout.filler);
            }
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

        Map<String, String> overviewPlaceholders = new HashMap<>();
        overviewPlaceholders.put("%quest_level%", String.valueOf(questLevel));
        overviewPlaceholders.put("%completed%", String.valueOf(completedQuests));
        overviewPlaceholders.put("%total%", String.valueOf(totalQuests));
        overviewPlaceholders.put("%quest_points%", String.valueOf(questPoints));
        overviewPlaceholders.put("%redeemable%", String.valueOf(redeemableQuestPoints));
        overviewPlaceholders.put("%quest_cost%", String.valueOf(cm.getQuestRedeemCost()));
        overviewPlaceholders.put("%clan_reward%", String.valueOf(cm.getQuestRedeemReward()));
        overviewPlaceholders.put("%quest_info%", questInfo);

        List<String> defaultOverviewLore = new ArrayList<>();
        defaultOverviewLore.add("&7quest level: &f%quest_level%");
        defaultOverviewLore.add("&7completed quests: &f%completed%/%total%");
        defaultOverviewLore.add("&7quest skill points: &f%quest_points%");
        defaultOverviewLore.add("&7redeemable quest points: &f%redeemable%");
        defaultOverviewLore.add("%quest_info%");

        ItemStack overviewItem = GuiConfigHelper.buildConfiguredItem(cm,
                plugin.getConfig().getConfigurationSection("quest-gui.overview"),
                Material.NETHER_STAR,
                DEFAULT_OVERVIEW_NAME,
                defaultOverviewLore,
                overviewPlaceholders);
        if (layout.overviewSlot >= 0 && layout.overviewSlot < inv.getSize()) {
            inv.setItem(layout.overviewSlot, overviewItem);
        }

        List<ItemStack> questEntries = buildQuestEntries(clan, cm, plugin.getConfig());
        int totalPages = Math.max(1, (questEntries.size() + layout.questSlots.size() - 1) / layout.questSlots.size());
        int page = Math.min(pages.getOrDefault(viewer, 0), totalPages - 1);
        pages.put(viewer, page);

        int startIndex = page * layout.questSlots.size();
        for (int i = 0; i < layout.questSlots.size(); i++) {
            int index = startIndex + i;
            if (index >= questEntries.size()) break;
            inv.setItem(layout.questSlots.get(i), questEntries.get(index));
        }

        if (totalPages > 1) {
            if (page > 0) {
                if (layout.previousSlot >= 0 && layout.previousSlot < inv.getSize()) {
                    inv.setItem(layout.previousSlot, layout.previousItem);
                }
            }
            if (page < totalPages - 1) {
                if (layout.nextSlot >= 0 && layout.nextSlot < inv.getSize()) {
                    inv.setItem(layout.nextSlot, layout.nextItem);
                }
            }
        }
    }

    private int getTotalPages(ClanData clan, QuestGuiLayout layout) {
        int questCount = buildQuestEntries(clan, plugin.getConfigManager(), plugin.getConfig()).size();
        return Math.max(1, (questCount + layout.questSlots.size() - 1) / layout.questSlots.size());
    }

    private List<ItemStack> buildQuestEntries(ClanData clan, ConfigManager cm, FileConfiguration config) {
        List<ItemStack> entries = new ArrayList<>();

        ConfigurationSection levelOneSection = config.getConfigurationSection("quest-gui.level-one");
        Map<String, String> levelOnePlaceholders = new HashMap<>();
        levelOnePlaceholders.put("%level%", "1");
        entries.add(GuiConfigHelper.buildConfiguredItem(cm,
                levelOneSection,
                Material.BOOK,
                "&6quest level 1",
                List.of("&7unlocked"),
                levelOnePlaceholders));

        for (ClanQuestProgress.QuestDefinition quest : ClanQuestProgress.getQuestDefinitions()) {
            int kills = clan.getQuestKillCount(quest.getTarget());
            String targetName = quest.getTarget().getDisplayName().toLowerCase(Locale.ENGLISH);
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%level%", String.valueOf(quest.getLevel()));
            placeholders.put("%target%", targetName);
            placeholders.put("%required%", String.valueOf(quest.getRequiredKills()));
            placeholders.put("%kills%", String.valueOf(kills));
            placeholders.put("%reward%", String.valueOf(quest.getRewardPoints()));
            placeholders.put("%progress%", kills + "/" + quest.getRequiredKills());
            placeholders.put("%status%", kills >= quest.getRequiredKills() ? "&aunlocked" : "&clocked");

            ConfigurationSection questSection = config.getConfigurationSection("quest-gui.quests");
            List<String> defaultLore = new ArrayList<>();
            defaultLore.add("&7task: &fkill %required% %target%");
            defaultLore.add("&7progress: &f%progress%");
            defaultLore.add("&7reward: &f%reward% quest points");
            defaultLore.add("%status%");

            entries.add(GuiConfigHelper.buildConfiguredItem(cm,
                    questSection,
                    quest.getTarget().getIcon(),
                    "&6quest level %level% - %target%",
                    defaultLore,
                    placeholders));
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

    private QuestGuiLayout loadQuestGuiLayout(ConfigManager cm) {
        FileConfiguration config = plugin.getConfig();
        int rows = GuiConfigHelper.clampRows(config.getInt("quest-gui.rows", DEFAULT_ROWS));
        int size = rows * 9;
        Component title = cm.getComponent("quest-gui.title", DEFAULT_TITLE);
        ItemStack filler = GuiConfigHelper.buildConfiguredItem(cm,
                config.getConfigurationSection("quest-gui.filler"),
                Material.GRAY_STAINED_GLASS_PANE,
                " ",
                List.of(),
                Map.of());
        List<Integer> fillerSlots = GuiConfigHelper.resolveOptionalSlots(config, "quest-gui.filler.slots", size);
        if (fillerSlots.isEmpty()) {
            fillerSlots = GuiConfigHelper.buildDefaultSlots(size);
        }
        int overviewSlot = GuiConfigHelper.resolveSlot(config, "quest-gui.overview.slot", DEFAULT_OVERVIEW_SLOT, size);
        int previousSlot = GuiConfigHelper.resolveSlot(config, "quest-gui.navigation.previous.slot", DEFAULT_PREVIOUS_PAGE_SLOT, size);
        int nextSlot = GuiConfigHelper.resolveSlot(config, "quest-gui.navigation.next.slot", DEFAULT_NEXT_PAGE_SLOT, size);
        List<Integer> questSlots = resolveQuestSlots(config, size);

        ItemStack previousItem = GuiConfigHelper.buildConfiguredItem(cm,
                config.getConfigurationSection("quest-gui.navigation.previous"),
                Material.ARROW,
                DEFAULT_PREVIOUS_NAME,
                List.of(),
                Map.of());
        ItemStack nextItem = GuiConfigHelper.buildConfiguredItem(cm,
                config.getConfigurationSection("quest-gui.navigation.next"),
                Material.ARROW,
                DEFAULT_NEXT_NAME,
                List.of(),
                Map.of());
        return new QuestGuiLayout(size, title, filler, fillerSlots, overviewSlot, previousSlot,
                nextSlot, questSlots, previousItem, nextItem);
    }

    private List<Integer> resolveQuestSlots(FileConfiguration config, int size) {
        int rowStart = config.getInt("quest-gui.quests.row-start", DEFAULT_QUEST_ROW_START);
        int rowSize = config.getInt("quest-gui.quests.row-size", DEFAULT_QUEST_ROW_SIZE);
        List<Integer> defaults = new ArrayList<>();
        for (int i = 0; i < rowSize; i++) {
            int slot = rowStart + i;
            if (slot >= 0 && slot < size) {
                defaults.add(slot);
            }
        }
        return GuiConfigHelper.resolveSlots(config, "quest-gui.quests.slots", defaults, size);
    }

    private static class QuestGuiLayout {
        private final int size;
        private final Component title;
        private final ItemStack filler;
        private final List<Integer> fillerSlots;
        private final int overviewSlot;
        private final int previousSlot;
        private final int nextSlot;
        private final List<Integer> questSlots;
        private final ItemStack previousItem;
        private final ItemStack nextItem;

        private QuestGuiLayout(int size,
                               Component title,
                               ItemStack filler,
                               List<Integer> fillerSlots,
                               int overviewSlot,
                               int previousSlot,
                               int nextSlot,
                               List<Integer> questSlots,
                               ItemStack previousItem,
                               ItemStack nextItem) {
            this.size = size;
            this.title = title;
            this.filler = filler;
            this.fillerSlots = fillerSlots;
            this.overviewSlot = overviewSlot;
            this.previousSlot = previousSlot;
            this.nextSlot = nextSlot;
            this.questSlots = questSlots;
            this.previousItem = previousItem;
            this.nextItem = nextItem;
        }
    }
}
