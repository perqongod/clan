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
import org.bukkit.inventory.meta.SkullMeta;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ClanSettingsListener implements Listener {

    private static final String MAIN_TITLE = "Clan Settings";
    private static final String CHEST_TITLE = "Clan Chest Settings";
    private static final int MAIN_INVENTORY_SIZE = 27;
    private static final int CHEST_INVENTORY_SIZE = 54;
    private static final int CHEST_SLOT = 4;
    private static final int MAIN_CHEST_SLOT = 10;
    private static final int MAIN_HEAD_SLOT = 11;

    private final Clan plugin;
    private final Map<UUID, SettingsSession> sessions = new HashMap<>();

    public ClanSettingsListener(Clan plugin) {
        this.plugin = plugin;
    }

    public void openGui(Player leader, ClanData clan) {
        Inventory inv = Bukkit.createInventory(null, MAIN_INVENTORY_SIZE, MAIN_TITLE);
        SettingsSession session = new SettingsSession(clan.getTag(), new ArrayList<>(clan.getMembers()));
        sessions.put(leader.getUniqueId(), session);
        populateMainMenu(inv);
        leader.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        SettingsSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        Component title = event.getView().title();
        boolean mainMenu = title.equals(Component.text(MAIN_TITLE));
        if (!mainMenu && !title.equals(Component.text(CHEST_TITLE))) return;

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) return;

        event.setCancelled(true);

        ClanData clan = plugin.getFileManager().loadClan(session.clanTag);
        if (clan == null) {
            sessions.remove(player.getUniqueId());
            player.closeInventory();
            return;
        }

        if (mainMenu) {
            if (rawSlot == MAIN_CHEST_SLOT) {
                session.members = new ArrayList<>(clan.getMembers());
                if (session.selectedMember != null && !session.members.contains(session.selectedMember)) {
                    session.selectedMember = null;
                }
                openChestSettings(player, clan, session);
            }
            return;
        }

        if (rawSlot == CHEST_SLOT) {
            if (session.selectedMember != null) {
                togglePermission(player, clan, session.selectedMember);
                refreshChestSettings(event.getView().getTopInventory(), clan, session);
            }
            return;
        }

        if (rawSlot < 9) return;

        int memberIndex = rawSlot - 9;
        if (memberIndex >= session.members.size()) return;

        UUID member = session.members.get(memberIndex);
        if (togglePermission(player, clan, member)) {
            session.selectedMember = member;
        }
        refreshChestSettings(event.getView().getTopInventory(), clan, session);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Component title = event.getView().title();
        if (!title.equals(Component.text(MAIN_TITLE)) && !title.equals(Component.text(CHEST_TITLE))) return;
        SettingsSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null) return;
        if (session.switching) {
            session.switching = false;
            return;
        }
        sessions.remove(event.getPlayer().getUniqueId());
    }

    private void refreshChestSettings(Inventory inv, ClanData clan, SettingsSession session) {
        inv.clear();
        populateChestSettings(inv, clan, session);
    }

    private void populateMainMenu(Inventory inv) {
        ItemStack orangePane = namedItem(Material.ORANGE_STAINED_GLASS_PANE, " ");
        ItemStack grayPane = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, orangePane);
            inv.setItem(18 + i, orangePane);
        }
        for (int i = 9; i < 18; i++) {
            inv.setItem(i, grayPane);
        }
        inv.setItem(MAIN_CHEST_SLOT, clanChestItem(null));
        inv.setItem(MAIN_HEAD_SLOT, futureSettingsItem());
    }

    private void populateChestSettings(Inventory inv, ClanData clan, SettingsSession session) {
        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, filler);
        }
        inv.setItem(CHEST_SLOT, clanChestItem(session.selectedMember));

        for (int i = 0; i < session.members.size() && i < 45; i++) {
            UUID member = session.members.get(i);
            boolean isLeader = member.equals(clan.getLeader());
            ClanChestPermission permission = isLeader ? ClanChestPermission.leaderDefault() : clan.getChestPermission(member);
            boolean selected = member.equals(session.selectedMember);
            inv.setItem(9 + i, memberSkull(member, permission, selected, isLeader));
        }

        if (session.members.size() < 45) {
            for (int i = session.members.size(); i < 45; i++) {
                inv.setItem(9 + i, namedItem(Material.BLACK_STAINED_GLASS_PANE, " "));
            }
        }
    }

    private void openChestSettings(Player player, ClanData clan, SettingsSession session) {
        session.switching = true;
        Inventory inv = Bukkit.createInventory(null, CHEST_INVENTORY_SIZE, CHEST_TITLE);
        populateChestSettings(inv, clan, session);
        player.openInventory(inv);
    }

    private boolean togglePermission(Player player, ClanData clan, UUID member) {
        if (member.equals(clan.getLeader())) {
            player.sendMessage(plugin.getConfigManager().getMessage("settings-leader-chest"));
            return false;
        }
        ClanChestPermission current = clan.getChestPermission(member);
        ClanChestPermission next = current.next();
        clan.setChestPermission(member, next);
        try {
            plugin.getFileManager().saveClan(clan);
        } catch (IOException e) {
            player.sendMessage(plugin.getConfigManager().getPrefix() + "Error saving.");
        }
        return true;
    }

    private ItemStack clanChestItem(UUID selectedMember) {
        ConfigManager cm = plugin.getConfigManager();
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(cm.translateColors("&6Clan Chest"));
            List<String> lore = new ArrayList<>();
            lore.add(cm.translateColors("&7Click to configure"));
            if (selectedMember != null) {
                String name = Bukkit.getOfflinePlayer(selectedMember).getName();
                if (name == null) name = selectedMember.toString().substring(0, 8);
                lore.add(cm.translateColors("&7Selected: &f" + name));
            }
            lore.add(cm.translateColors("&7✅ &aAccess granted"));
            lore.add(cm.translateColors("&7👁 &eView only"));
            lore.add(cm.translateColors("&7❌ &cNo access"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack futureSettingsItem() {
        ConfigManager cm = plugin.getConfigManager();
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(cm.translateColors("&6More Settings"));
            List<String> lore = new ArrayList<>();
            lore.add(cm.translateColors("&7Coming soon"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
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

    private ItemStack memberSkull(UUID member, ClanChestPermission permission, boolean selected, boolean isLeader) {
        ConfigManager cm = plugin.getConfigManager();
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(member);
            meta.setOwningPlayer(op);
            String name = op.getName() != null ? op.getName() : member.toString().substring(0, 8);
            meta.setDisplayName("§f" + name);
            List<String> lore = new ArrayList<>();
            if (isLeader) {
                lore.add(cm.translateColors("&7Leader &f(always access)"));
                lore.add(cm.translateColors("&7Current: " + permissionLabel(permission)));
            } else {
                lore.add(cm.translateColors("&7Click to change permission"));
                lore.add(cm.translateColors("&7Current: " + permissionLabel(permission)));
            }
            if (selected) {
                lore.add(cm.translateColors("&bSelected"));
            }
            meta.setLore(lore);
            skull.setItemMeta(meta);
        }
        return skull;
    }

    private String permissionLabel(ClanChestPermission permission) {
        switch (permission) {
            case EXECUTE:
                return "&a✅ Access granted";
            case DENY:
                return "&c❌ No access";
            default:
                return "&e👁 View only";
        }
    }

    private static class SettingsSession {
        final String clanTag;
        List<UUID> members;
        UUID selectedMember;
        boolean switching;

        SettingsSession(String clanTag, List<UUID> members) {
            this.clanTag = clanTag;
            this.members = members;
        }
    }
}
