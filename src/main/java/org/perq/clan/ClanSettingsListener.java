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

    private static final String TITLE = "Clan Settings";
    private static final int INVENTORY_SIZE = 54;
    private static final int CHEST_SLOT = 4;

    private final Clan plugin;
    private final Map<UUID, SettingsSession> sessions = new HashMap<>();

    public ClanSettingsListener(Clan plugin) {
        this.plugin = plugin;
    }

    public void openGui(Player leader, ClanData clan) {
        Inventory inv = Bukkit.createInventory(null, INVENTORY_SIZE, TITLE);
        SettingsSession session = new SettingsSession(clan.getTag(), new ArrayList<>(clan.getMembers()));
        sessions.put(leader.getUniqueId(), session);
        populateInventory(inv, clan, session);
        leader.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        SettingsSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (!event.getView().title().equals(Component.text(TITLE))) return;

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) return;

        event.setCancelled(true);

        ClanData clan = plugin.getFileManager().loadClan(session.clanTag);
        if (clan == null) {
            sessions.remove(player.getUniqueId());
            player.closeInventory();
            return;
        }

        if (rawSlot == CHEST_SLOT) {
            if (session.selectedMember != null) {
                togglePermission(player, clan, session.selectedMember);
                refresh(event.getView().getTopInventory(), clan, session);
            }
            return;
        }

        if (rawSlot < 9) return;

        int memberIndex = rawSlot - 9;
        if (memberIndex >= session.members.size()) return;

        UUID member = session.members.get(memberIndex);
        session.selectedMember = member;
        togglePermission(player, clan, member);
        refresh(event.getView().getTopInventory(), clan, session);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getView().title().equals(Component.text(TITLE))) return;
        sessions.remove(event.getPlayer().getUniqueId());
    }

    private void refresh(Inventory inv, ClanData clan, SettingsSession session) {
        inv.clear();
        populateInventory(inv, clan, session);
    }

    private void populateInventory(Inventory inv, ClanData clan, SettingsSession session) {
        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, filler);
        }
        inv.setItem(CHEST_SLOT, clanChestItem(session.selectedMember));

        for (int i = 0; i < session.members.size() && i < 45; i++) {
            UUID member = session.members.get(i);
            ClanChestPermission permission = clan.getChestPermission(member);
            boolean selected = member.equals(session.selectedMember);
            inv.setItem(9 + i, memberSkull(member, permission, selected));
        }

        if (session.members.size() < 45) {
            for (int i = session.members.size(); i < 45; i++) {
                inv.setItem(9 + i, namedItem(Material.BLACK_STAINED_GLASS_PANE, " "));
            }
        }
    }

    private void togglePermission(Player player, ClanData clan, UUID member) {
        ClanChestPermission current = clan.getChestPermission(member);
        ClanChestPermission next = current.next();
        clan.setChestPermission(member, next);
        try {
            plugin.getFileManager().saveClan(clan);
        } catch (IOException e) {
            player.sendMessage(plugin.getConfigManager().getPrefix() + "Fehler beim Speichern.");
        }
    }

    private ItemStack clanChestItem(UUID selectedMember) {
        ConfigManager cm = plugin.getConfigManager();
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(cm.translateColors("&6Clan Chest"));
            List<String> lore = new ArrayList<>();
            lore.add(cm.translateColors("&7Klick um einzustellen"));
            if (selectedMember != null) {
                String name = Bukkit.getOfflinePlayer(selectedMember).getName();
                if (name == null) name = selectedMember.toString().substring(0, 8);
                lore.add(cm.translateColors("&7Ausgewählt: &f" + name));
            }
            lore.add(cm.translateColors("&7✅ &aZugriff erlaubt"));
            lore.add(cm.translateColors("&7👁 &eNur sehen"));
            lore.add(cm.translateColors("&7❌ &cKein Zugriff"));
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

    private ItemStack memberSkull(UUID member, ClanChestPermission permission, boolean selected) {
        ConfigManager cm = plugin.getConfigManager();
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(member);
            meta.setOwningPlayer(op);
            String name = op.getName() != null ? op.getName() : member.toString().substring(0, 8);
            meta.setDisplayName("§f" + name);
            List<String> lore = new ArrayList<>();
            lore.add(cm.translateColors("&7Klick um Berechtigung zu ändern"));
            lore.add(cm.translateColors("&7Aktuell: " + permissionLabel(permission)));
            if (selected) {
                lore.add(cm.translateColors("&bAusgewählt"));
            }
            meta.setLore(lore);
            skull.setItemMeta(meta);
        }
        return skull;
    }

    private String permissionLabel(ClanChestPermission permission) {
        switch (permission) {
            case EXECUTE:
                return "&a✅ Zugriff erlaubt";
            case DENY:
                return "&c❌ Kein Zugriff";
            default:
                return "&e👁 Nur sehen";
        }
    }

    private static class SettingsSession {
        final String clanTag;
        final List<UUID> members;
        UUID selectedMember;

        SettingsSession(String clanTag, List<UUID> members) {
            this.clanTag = clanTag;
            this.members = members;
        }
    }
}
