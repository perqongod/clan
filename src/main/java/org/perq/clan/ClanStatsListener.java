package org.perq.clan;

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

public class ClanStatsListener implements Listener {
    private static final String TITLE_PREFIX = "Clan stats of ";
    private static final String EMPTY_DISPLAY_NAME = " ";
    private static final String PLAYTIME_FORMAT = "%.1f";
    private static final int INVENTORY_SIZE = 27;
    private static final int POINTS_SLOT = 10;
    private static final int RANK_SLOT = 11;
    private static final int CREATED_SLOT = 12;
    private static final int MEMBERS_SLOT = 13;
    private static final int PLAYTIME_SLOT = 14;
    private static final int LEADER_SLOT = 15;
    private static final int TAG_SLOT = 16;

    private final Clan plugin;

    public ClanStatsListener(Clan plugin) {
        this.plugin = plugin;
    }

    public void openGui(Player player, ClanData clan) {
        Inventory inv = Bukkit.createInventory(null, INVENTORY_SIZE, TITLE_PREFIX + clan.getTag());
        populateInventory(inv, clan);
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        @SuppressWarnings("deprecation")
        String title = event.getView().getTitle();
        if (!title.startsWith(TITLE_PREFIX)) return;

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) return;

        event.setCancelled(true);
    }

    private void populateInventory(Inventory inv, ClanData clan) {
        inv.clear();
        ConfigManager cm = plugin.getConfigManager();

        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, EMPTY_DISPLAY_NAME);
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        String leaderName = Bukkit.getOfflinePlayer(clan.getLeader()).getName();
        if (leaderName == null) leaderName = "Unknown";
        int effectiveMaxMembers = cm.getMaxMembers() + ClanSkillProgress.getBonusMemberSlots(clan.getSkillPoints());

        List<String> pointsLore = new ArrayList<>();
        pointsLore.add(cm.translateColors("&f" + clan.getPoints()));
        inv.setItem(POINTS_SLOT, namedItem(Material.GREEN_WOOL, cm.translateColors("&aClan Points"), pointsLore));

        List<String> rankLore = new ArrayList<>();
        rankLore.add(cm.translateColors("&f" + clan.getRank()));
        inv.setItem(RANK_SLOT, namedItem(Material.PINK_WOOL, cm.translateColors("&dClan Rank"), rankLore));

        List<String> createdLore = new ArrayList<>();
        createdLore.add(cm.translateColors("&f" + clan.getCreated()));
        inv.setItem(CREATED_SLOT, namedItem(Material.RED_WOOL, cm.translateColors("&cCreated"), createdLore));

        List<String> membersLore = new ArrayList<>();
        membersLore.add(cm.translateColors("&f" + clan.getMembers().size() + "/" + effectiveMaxMembers));
        inv.setItem(MEMBERS_SLOT, namedItem(Material.GRAY_WOOL, cm.translateColors("&7Members"), membersLore));

        List<String> playtimeLore = new ArrayList<>();
        playtimeLore.add(cm.translateColors("&f" + String.format(PLAYTIME_FORMAT, clan.getOnlineTime()) + "h"));
        inv.setItem(PLAYTIME_SLOT, namedItem(Material.YELLOW_WOOL, cm.translateColors("&eTotal Playtime"), playtimeLore));

        List<String> leaderLore = new ArrayList<>();
        leaderLore.add(cm.translateColors("&f" + leaderName));
        inv.setItem(LEADER_SLOT, namedItem(Material.WHITE_WOOL, cm.translateColors("&fLeader"), leaderLore));

        List<String> tagLore = new ArrayList<>();
        tagLore.add(cm.translateColors("&f" + clan.getTag()));
        inv.setItem(TAG_SLOT, namedItem(Material.BLACK_WOOL, cm.translateColors("&8Clan Tag"), tagLore));
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
}
