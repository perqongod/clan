package org.perq.clan;

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
import java.util.Map;
import java.util.UUID;

public class ClanStatsListener implements Listener {
    private static final String DEFAULT_TITLE_FORMAT = "Clan stats of %tag%";
    private static final String EMPTY_DISPLAY_NAME = " ";
    private static final String PLAYTIME_FORMAT = "%.1f";
    private static final int DEFAULT_ROWS = 3;
    private static final int POINTS_SLOT = 10;
    private static final int RANK_SLOT = 11;
    private static final int CREATED_SLOT = 12;
    private static final int MEMBERS_SLOT = 13;
    private static final int PLAYTIME_SLOT = 14;
    private static final int LEADER_SLOT = 15;
    private static final int TAG_SLOT = 16;

    private final Clan plugin;
    private final Map<UUID, String> openTitles = new HashMap<>();

    public ClanStatsListener(Clan plugin) {
        this.plugin = plugin;
    }

    public void openGui(Player player, ClanData clan) {
        ConfigManager cm = plugin.getConfigManager();
        StatsGuiLayout layout = loadStatsGuiLayout(cm);
        String title = formatStatsTitle(cm, clan);
        Inventory inv = Bukkit.createInventory(null, layout.size, title);
        openTitles.put(player.getUniqueId(), title);
        populateInventory(inv, clan, layout);
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        @SuppressWarnings("deprecation")
        String title = event.getView().getTitle();
        String openTitle = openTitles.get(player.getUniqueId());
        if (openTitle == null || !openTitle.equals(title)) return;

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) return;

        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        @SuppressWarnings("deprecation")
        String title = event.getView().getTitle();
        UUID playerId = event.getPlayer().getUniqueId();
        String openTitle = openTitles.get(playerId);
        if (openTitle != null && openTitle.equals(title)) {
            openTitles.remove(playerId);
        }
    }

    private void populateInventory(Inventory inv, ClanData clan, StatsGuiLayout layout) {
        inv.clear();
        ConfigManager cm = plugin.getConfigManager();

        ItemStack filler = layout.filler;
        for (Integer slot : layout.fillerSlots) {
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, filler);
            }
        }

        String leaderName = Bukkit.getOfflinePlayer(clan.getLeader()).getName();
        if (leaderName == null) leaderName = "Unknown";
        int effectiveMaxMembers = cm.getMaxMembers() + ClanSkillProgress.getBonusMemberSlots(clan.getSkillPoints());
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%tag%", clan.getTag());
        placeholders.put("%points%", String.valueOf(clan.getPoints()));
        placeholders.put("%rank%", clan.getRank());
        placeholders.put("%created%", clan.getCreated());
        placeholders.put("%members%", String.valueOf(clan.getMembers().size()));
        placeholders.put("%max_members%", String.valueOf(effectiveMaxMembers));
        placeholders.put("%playtime%", String.format(PLAYTIME_FORMAT, clan.getOnlineTime()));
        placeholders.put("%leader%", leaderName);

        FileConfiguration config = plugin.getConfig();
        setConfiguredItem(inv, cm, config, "stats-gui.items.points", POINTS_SLOT,
                Material.GREEN_WOOL, "&aClan Points",
                List.of("&f%points%"), placeholders);
        setConfiguredItem(inv, cm, config, "stats-gui.items.rank", RANK_SLOT,
                Material.PINK_WOOL, "&dClan Rank",
                List.of("&f%rank%"), placeholders);
        setConfiguredItem(inv, cm, config, "stats-gui.items.created", CREATED_SLOT,
                Material.RED_WOOL, "&cCreated",
                List.of("&f%created%"), placeholders);
        setConfiguredItem(inv, cm, config, "stats-gui.items.members", MEMBERS_SLOT,
                Material.GRAY_WOOL, "&7Members",
                List.of("&f%members%/%max_members%"), placeholders);
        setConfiguredItem(inv, cm, config, "stats-gui.items.playtime", PLAYTIME_SLOT,
                Material.YELLOW_WOOL, "&eTotal Playtime",
                List.of("&f%playtime%h"), placeholders);
        setConfiguredItem(inv, cm, config, "stats-gui.items.leader", LEADER_SLOT,
                Material.WHITE_WOOL, "&fLeader",
                List.of("&f%leader%"), placeholders);
        setConfiguredItem(inv, cm, config, "stats-gui.items.tag", TAG_SLOT,
                Material.BLACK_WOOL, "&8Clan Tag",
                List.of("&f%tag%"), placeholders);
    }

    private ItemStack namedItem(Material material, String name) {
        return namedItem(material, name, null);
    }

    private ItemStack namedItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private StatsGuiLayout loadStatsGuiLayout(ConfigManager cm) {
        FileConfiguration config = plugin.getConfig();
        int rows = GuiConfigHelper.clampRows(config.getInt("stats-gui.rows", DEFAULT_ROWS));
        int size = rows * 9;
        ItemStack filler = GuiConfigHelper.buildConfiguredItem(cm,
                config.getConfigurationSection("stats-gui.filler"),
                Material.GRAY_STAINED_GLASS_PANE,
                EMPTY_DISPLAY_NAME,
                List.of(),
                Map.of());
        List<Integer> fillerSlots = GuiConfigHelper.resolveOptionalSlots(config, "stats-gui.filler.slots", size);
        if (fillerSlots.isEmpty()) {
            fillerSlots = GuiConfigHelper.buildDefaultSlots(size);
        }
        return new StatsGuiLayout(size, filler, fillerSlots);
    }

    private void setConfiguredItem(Inventory inv,
                                   ConfigManager cm,
                                   FileConfiguration config,
                                   String path,
                                   int defaultSlot,
                                   Material defaultMaterial,
                                   String defaultName,
                                   List<String> defaultLore,
                                   Map<String, String> placeholders) {
        int slot = GuiConfigHelper.resolveSlot(config, path + ".slot", defaultSlot, inv.getSize());
        if (slot < 0 || slot >= inv.getSize()) return;
        ConfigurationSection section = config.getConfigurationSection(path);
        ItemStack item = GuiConfigHelper.buildConfiguredItem(cm, section,
                defaultMaterial, defaultName, defaultLore, placeholders);
        inv.setItem(slot, item);
    }

    private String formatStatsTitle(ConfigManager cm, ClanData clan) {
        String template = plugin.getConfig().getString("stats-gui.title", DEFAULT_TITLE_FORMAT);
        String withTag = GuiConfigHelper.applyPlaceholders(template, Map.of("%tag%", clan.getTag()));
        return cm.translateColors(withTag);
    }

    private static class StatsGuiLayout {
        private final int size;
        private final ItemStack filler;
        private final List<Integer> fillerSlots;

        private StatsGuiLayout(int size, ItemStack filler, List<Integer> fillerSlots) {
            this.size = size;
            this.filler = filler;
            this.fillerSlots = fillerSlots;
        }
    }
}
