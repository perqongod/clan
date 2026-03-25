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
    private static final String FRIENDLY_FIRE_TITLE = "Clan Friendly Fire Settings";
    private static final String SKILLS_TITLE = "Clan Skills Settings";
    private static final String SPAWN_TITLE = "Clan Spawn Settings";
    private static final int MAIN_INVENTORY_SIZE = 27;
    private static final int CHEST_INVENTORY_SIZE = 54;
    private static final int FRIENDLY_FIRE_INVENTORY_SIZE = 54;
    private static final int SKILLS_INVENTORY_SIZE = 54;
    private static final int SPAWN_INVENTORY_SIZE = 54;
    private static final int CHEST_SLOT = 4;
    private static final int FRIENDLY_FIRE_SLOT = 4;
    private static final int SKILLS_SLOT = 4;
    private static final int SPAWN_SLOT = 4;
    private static final int MAIN_INVITE_TOGGLE_SLOT = 10;
    private static final int MAIN_CHEST_SLOT = 11;
    private static final int MAIN_FRIENDLY_FIRE_SLOT = 12;
    private static final int MAIN_SKILLS_SLOT = 13;
    private static final int MAIN_SPAWN_SLOT = 14;

    private final Clan plugin;
    private final Map<UUID, SettingsSession> sessions = new HashMap<>();

    public ClanSettingsListener(Clan plugin) {
        this.plugin = plugin;
    }

    public void openGui(Player player, ClanData clan) {
        Inventory inv = Bukkit.createInventory(null, MAIN_INVENTORY_SIZE, MAIN_TITLE);
        SettingsSession session = new SettingsSession(clan.getTag(), new ArrayList<>(clan.getMembers()));
        sessions.put(player.getUniqueId(), session);
        populateMainMenu(inv, player, clan);
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        SettingsSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        Component title = event.getView().title();
        boolean mainMenu = title.equals(Component.text(MAIN_TITLE));
        boolean chestMenu = title.equals(Component.text(CHEST_TITLE));
        boolean friendlyMenu = title.equals(Component.text(FRIENDLY_FIRE_TITLE));
        boolean skillsMenu = title.equals(Component.text(SKILLS_TITLE));
        boolean spawnMenu = title.equals(Component.text(SPAWN_TITLE));
        if (!mainMenu && !chestMenu && !friendlyMenu && !skillsMenu && !spawnMenu) return;

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
            ConfigManager cm = plugin.getConfigManager();
            if (rawSlot == MAIN_INVITE_TOGGLE_SLOT) {
                boolean invitesDisabled = plugin.toggleInvitation(player);
                player.sendMessage(invitesDisabled ? cm.getMessage("toggle-off") : cm.getMessage("toggle-on"));
                refreshMainMenu(event.getView().getTopInventory(), player, clan);
                return;
            }
            if (rawSlot == MAIN_CHEST_SLOT) {
                if (!clan.getLeader().equals(player.getUniqueId())) {
                    player.sendMessage(cm.getMessage("not-clan-leader"));
                    return;
                }
                session.members = new ArrayList<>(clan.getMembers());
                if (session.selectedMember != null && !session.members.contains(session.selectedMember)) {
                    session.selectedMember = null;
                }
                openChestSettings(player, clan, session);
            }
            if (rawSlot == MAIN_FRIENDLY_FIRE_SLOT) {
                if (!clan.getLeader().equals(player.getUniqueId())) {
                    player.sendMessage(cm.getMessage("not-clan-leader"));
                    return;
                }
                session.members = new ArrayList<>(clan.getMembers());
                if (session.selectedMember != null && !session.members.contains(session.selectedMember)) {
                    session.selectedMember = null;
                }
                openFriendlyFireSettings(player, clan, session);
            }
            if (rawSlot == MAIN_SKILLS_SLOT) {
                if (!clan.getLeader().equals(player.getUniqueId())) {
                    player.sendMessage(cm.getMessage("not-clan-leader"));
                    return;
                }
                session.members = new ArrayList<>(clan.getMembers());
                if (session.selectedMember != null && !session.members.contains(session.selectedMember)) {
                    session.selectedMember = null;
                }
                openSkillsSettings(player, clan, session);
            }
            if (rawSlot == MAIN_SPAWN_SLOT) {
                if (!clan.getLeader().equals(player.getUniqueId())) {
                    player.sendMessage(cm.getMessage("not-clan-leader"));
                    return;
                }
                session.members = new ArrayList<>(clan.getMembers());
                if (session.selectedMember != null && !session.members.contains(session.selectedMember)) {
                    session.selectedMember = null;
                }
                openSpawnSettings(player, clan, session);
            }
            return;
        }

        if (chestMenu) {
            if (rawSlot == CHEST_SLOT) {
                if (session.selectedMember != null) {
                    if (isLeaderToggle(player, clan, session.selectedMember, "settings-leader-chest")) return;
                    togglePermission(player, clan, session.selectedMember);
                    refreshChestSettings(event.getView().getTopInventory(), clan, session);
                }
                return;
            }

            if (rawSlot < 9) return;

            int memberIndex = rawSlot - 9;
            if (memberIndex >= session.members.size()) return;

            UUID member = session.members.get(memberIndex);
            if (isLeaderToggle(player, clan, member, "settings-leader-chest")) return;
            session.selectedMember = member;
            togglePermission(player, clan, member);
            refreshChestSettings(event.getView().getTopInventory(), clan, session);
            return;
        }

        if (friendlyMenu) {
            if (rawSlot == FRIENDLY_FIRE_SLOT) {
                if (session.selectedMember != null) {
                    toggleFriendlyFirePermission(player, clan, session.selectedMember);
                    refreshFriendlyFireSettings(event.getView().getTopInventory(), clan, session);
                }
                return;
            }

            if (rawSlot < 9) return;

            int memberIndex = rawSlot - 9;
            if (memberIndex >= session.members.size()) return;

            UUID member = session.members.get(memberIndex);
            session.selectedMember = member;
            toggleFriendlyFirePermission(player, clan, member);
            refreshFriendlyFireSettings(event.getView().getTopInventory(), clan, session);
            return;
        }

        if (skillsMenu) {
            if (rawSlot == SKILLS_SLOT) {
                if (session.selectedMember != null) {
                    if (isLeaderToggle(player, clan, session.selectedMember, "settings-leader-skills")) return;
                    toggleSkillsPermission(player, clan, session.selectedMember);
                    refreshSkillsSettings(event.getView().getTopInventory(), clan, session);
                }
                return;
            }

            if (rawSlot < 9) return;

            int memberIndex = rawSlot - 9;
            if (memberIndex >= session.members.size()) return;

            UUID member = session.members.get(memberIndex);
            if (isLeaderToggle(player, clan, member, "settings-leader-skills")) return;
            session.selectedMember = member;
            toggleSkillsPermission(player, clan, member);
            refreshSkillsSettings(event.getView().getTopInventory(), clan, session);
            return;
        }

        if (spawnMenu) {
            if (rawSlot == SPAWN_SLOT) {
                if (session.selectedMember != null) {
                    if (isLeaderToggle(player, clan, session.selectedMember, "settings-leader-spawn")) return;
                    toggleSpawnPermission(player, clan, session.selectedMember);
                    refreshSpawnSettings(event.getView().getTopInventory(), clan, session);
                }
                return;
            }

            if (rawSlot < 9) return;

            int memberIndex = rawSlot - 9;
            if (memberIndex >= session.members.size()) return;

            UUID member = session.members.get(memberIndex);
            if (isLeaderToggle(player, clan, member, "settings-leader-spawn")) return;
            session.selectedMember = member;
            toggleSpawnPermission(player, clan, member);
            refreshSpawnSettings(event.getView().getTopInventory(), clan, session);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Component title = event.getView().title();
        if (!title.equals(Component.text(MAIN_TITLE))
                && !title.equals(Component.text(CHEST_TITLE))
                && !title.equals(Component.text(FRIENDLY_FIRE_TITLE))
                && !title.equals(Component.text(SKILLS_TITLE))
                && !title.equals(Component.text(SPAWN_TITLE))) {
            return;
        }
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

    private void refreshFriendlyFireSettings(Inventory inv, ClanData clan, SettingsSession session) {
        inv.clear();
        populateFriendlyFireSettings(inv, clan, session);
    }

    private void refreshSkillsSettings(Inventory inv, ClanData clan, SettingsSession session) {
        inv.clear();
        populateSkillsSettings(inv, clan, session);
    }

    private void refreshSpawnSettings(Inventory inv, ClanData clan, SettingsSession session) {
        inv.clear();
        populateSpawnSettings(inv, clan, session);
    }

    private void refreshMainMenu(Inventory inv, Player player, ClanData clan) {
        inv.clear();
        populateMainMenu(inv, player, clan);
    }

    private void populateMainMenu(Inventory inv, Player player, ClanData clan) {
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
        inv.setItem(MAIN_FRIENDLY_FIRE_SLOT, friendlyFireItem(null));
        inv.setItem(MAIN_INVITE_TOGGLE_SLOT, invitationToggleItem(player));
        inv.setItem(MAIN_SKILLS_SLOT, clanSkillsItem(null));
        inv.setItem(MAIN_SPAWN_SLOT, clanSpawnItem(null));
    }

    private void populateChestSettings(Inventory inv, ClanData clan, SettingsSession session) {
        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, filler);
        }
        inv.setItem(CHEST_SLOT, clanChestItem(session.selectedMember));

        UUID leaderId = clan.getLeader();
        for (int i = 0; i < session.members.size() && i < 45; i++) {
            UUID member = session.members.get(i);
            ClanChestPermission permission = effectivePermission(clan, member, leaderId);
            boolean selected = member.equals(session.selectedMember);
            inv.setItem(9 + i, memberSkull(member, permission, selected, leaderId));
        }

        if (session.members.size() < 45) {
            for (int i = session.members.size(); i < 45; i++) {
                inv.setItem(9 + i, namedItem(Material.BLACK_STAINED_GLASS_PANE, " "));
            }
        }
    }

    private void populateFriendlyFireSettings(Inventory inv, ClanData clan, SettingsSession session) {
        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, filler);
        }
        inv.setItem(FRIENDLY_FIRE_SLOT, friendlyFireItem(session.selectedMember));

        for (int i = 0; i < session.members.size() && i < 45; i++) {
            UUID member = session.members.get(i);
            ClanFriendlyFirePermission permission = effectiveFriendlyFirePermission(clan, member);
            boolean selected = member.equals(session.selectedMember);
            inv.setItem(9 + i, friendlyFireMemberSkull(member, permission, selected, clan.getLeader()));
        }

        if (session.members.size() < 45) {
            for (int i = session.members.size(); i < 45; i++) {
                inv.setItem(9 + i, namedItem(Material.BLACK_STAINED_GLASS_PANE, " "));
            }
        }
    }

    private void populateSkillsSettings(Inventory inv, ClanData clan, SettingsSession session) {
        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, filler);
        }
        inv.setItem(SKILLS_SLOT, clanSkillsItem(session.selectedMember));

        UUID leaderId = clan.getLeader();
        for (int i = 0; i < session.members.size() && i < 45; i++) {
            UUID member = session.members.get(i);
            ClanAccessPermission permission = effectiveSkillsPermission(clan, member, leaderId);
            boolean selected = member.equals(session.selectedMember);
            inv.setItem(9 + i, accessMemberSkull(member, permission, selected, leaderId));
        }

        if (session.members.size() < 45) {
            for (int i = session.members.size(); i < 45; i++) {
                inv.setItem(9 + i, namedItem(Material.BLACK_STAINED_GLASS_PANE, " "));
            }
        }
    }

    private void populateSpawnSettings(Inventory inv, ClanData clan, SettingsSession session) {
        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, filler);
        }
        inv.setItem(SPAWN_SLOT, clanSpawnItem(session.selectedMember));

        UUID leaderId = clan.getLeader();
        for (int i = 0; i < session.members.size() && i < 45; i++) {
            UUID member = session.members.get(i);
            ClanAccessPermission permission = effectiveSpawnPermission(clan, member, leaderId);
            boolean selected = member.equals(session.selectedMember);
            inv.setItem(9 + i, accessMemberSkull(member, permission, selected, leaderId));
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

    private void openFriendlyFireSettings(Player player, ClanData clan, SettingsSession session) {
        session.switching = true;
        Inventory inv = Bukkit.createInventory(null, FRIENDLY_FIRE_INVENTORY_SIZE, FRIENDLY_FIRE_TITLE);
        populateFriendlyFireSettings(inv, clan, session);
        player.openInventory(inv);
    }

    private void openSkillsSettings(Player player, ClanData clan, SettingsSession session) {
        session.switching = true;
        Inventory inv = Bukkit.createInventory(null, SKILLS_INVENTORY_SIZE, SKILLS_TITLE);
        populateSkillsSettings(inv, clan, session);
        player.openInventory(inv);
    }

    private void openSpawnSettings(Player player, ClanData clan, SettingsSession session) {
        session.switching = true;
        Inventory inv = Bukkit.createInventory(null, SPAWN_INVENTORY_SIZE, SPAWN_TITLE);
        populateSpawnSettings(inv, clan, session);
        player.openInventory(inv);
    }

    private void togglePermission(Player player, ClanData clan, UUID member) {
        ClanChestPermission current = clan.getChestPermission(member);
        ClanChestPermission next = current.next();
        clan.setChestPermission(member, next);
        try {
            plugin.getFileManager().saveClan(clan);
        } catch (IOException e) {
            player.sendMessage(plugin.getConfigManager().getPrefix() + "Error saving.");
        }
    }

    private void toggleFriendlyFirePermission(Player player, ClanData clan, UUID member) {
        ClanFriendlyFirePermission current = clan.getFriendlyFirePermission(member);
        ClanFriendlyFirePermission next = current.next();
        clan.setFriendlyFirePermission(member, next);
        try {
            plugin.getFileManager().saveClan(clan);
        } catch (IOException e) {
            player.sendMessage(plugin.getConfigManager().getPrefix() + "Error saving.");
        }
    }

    private void toggleSkillsPermission(Player player, ClanData clan, UUID member) {
        ClanAccessPermission current = clan.getSkillsPermission(member);
        ClanAccessPermission next = current.next();
        clan.setSkillsPermission(member, next);
        try {
            plugin.getFileManager().saveClan(clan);
        } catch (IOException e) {
            player.sendMessage(plugin.getConfigManager().getPrefix() + "Error saving.");
        }
    }

    private void toggleSpawnPermission(Player player, ClanData clan, UUID member) {
        ClanAccessPermission current = clan.getSpawnPermission(member);
        ClanAccessPermission next = current.next();
        clan.setSpawnPermission(member, next);
        try {
            plugin.getFileManager().saveClan(clan);
        } catch (IOException e) {
            player.sendMessage(plugin.getConfigManager().getPrefix() + "Error saving.");
        }
    }

    private boolean isLeaderToggle(Player player, ClanData clan, UUID member, String messageKey) {
        if (member == null) return false;
        if (!member.equals(clan.getLeader())) return false;
        player.sendMessage(plugin.getConfigManager().getMessage(messageKey));
        return true;
    }

    private ClanChestPermission effectivePermission(ClanData clan, UUID member, UUID leaderId) {
        if (member.equals(leaderId)) {
            return ClanChestPermission.leaderDefault();
        }
        return clan.getChestPermission(member);
    }

    private ClanFriendlyFirePermission effectiveFriendlyFirePermission(ClanData clan, UUID member) {
        return clan.getFriendlyFirePermission(member);
    }

    private ClanAccessPermission effectiveSkillsPermission(ClanData clan, UUID member, UUID leaderId) {
        if (member.equals(leaderId)) {
            return ClanAccessPermission.leaderDefault();
        }
        return clan.getSkillsPermission(member);
    }

    private ClanAccessPermission effectiveSpawnPermission(ClanData clan, UUID member, UUID leaderId) {
        if (member.equals(leaderId)) {
            return ClanAccessPermission.leaderDefault();
        }
        return clan.getSpawnPermission(member);
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

    private ItemStack friendlyFireItem(UUID selectedMember) {
        ConfigManager cm = plugin.getConfigManager();
        ItemStack item = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(cm.translateColors("&6Friendly Fire"));
            List<String> lore = new ArrayList<>();
            lore.add(cm.translateColors("&7Click to configure"));
            if (selectedMember != null) {
                String name = Bukkit.getOfflinePlayer(selectedMember).getName();
                if (name == null) name = selectedMember.toString().substring(0, 8);
                lore.add(cm.translateColors("&7Selected: &f" + name));
            }
            lore.add(cm.translateColors("&7✅ &aEnabled"));
            lore.add(cm.translateColors("&7❌ &cDisabled"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack clanSkillsItem(UUID selectedMember) {
        ConfigManager cm = plugin.getConfigManager();
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(cm.translateColors("&6Clan Skills"));
            List<String> lore = new ArrayList<>();
            lore.add(cm.translateColors("&7Click to configure"));
            if (selectedMember != null) {
                String name = Bukkit.getOfflinePlayer(selectedMember).getName();
                if (name == null) name = selectedMember.toString().substring(0, 8);
                lore.add(cm.translateColors("&7Selected: &f" + name));
            }
            lore.add(cm.translateColors("&7✅ &aFull access"));
            lore.add(cm.translateColors("&7👁 &eSee only"));
            lore.add(cm.translateColors("&7❌ &cNo access"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack clanSpawnItem(UUID selectedMember) {
        ConfigManager cm = plugin.getConfigManager();
        ItemStack item = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(cm.translateColors("&6Clan Spawn"));
            List<String> lore = new ArrayList<>();
            lore.add(cm.translateColors("&7Click to configure"));
            if (selectedMember != null) {
                String name = Bukkit.getOfflinePlayer(selectedMember).getName();
                if (name == null) name = selectedMember.toString().substring(0, 8);
                lore.add(cm.translateColors("&7Selected: &f" + name));
            }
            lore.add(cm.translateColors("&7✅ &aFull access"));
            lore.add(cm.translateColors("&7👁 &eSee only"));
            lore.add(cm.translateColors("&7❌ &cNo access"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack invitationToggleItem(Player player) {
        ConfigManager cm = plugin.getConfigManager();
        boolean invitesEnabled = plugin.isInvitesEnabled(player.getUniqueId());
        ItemStack item = new ItemStack(Material.BELL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(cm.translateColors("&6Invitations"));
            List<String> lore = new ArrayList<>();
            lore.add(cm.translateColors("&7Click to toggle"));
            lore.add(cm.translateColors("&7Status: " + (invitesEnabled ? "&aEnabled" : "&cDisabled")));
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

    private ItemStack memberSkull(UUID member, ClanChestPermission permission, boolean selected, UUID leaderId) {
        ConfigManager cm = plugin.getConfigManager();
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(member);
            meta.setOwningPlayer(op);
            String name = op.getName() != null ? op.getName() : member.toString().substring(0, 8);
            meta.setDisplayName("§f" + name);
            List<String> lore = new ArrayList<>();
            boolean isLeader = member.equals(leaderId);
            if (isLeader) {
                lore.add(cm.translateColors("&7Clan Leader &f(always enabled)"));
            } else {
                lore.add(cm.translateColors("&7Click to change permission"));
            }
            lore.add(cm.translateColors("&7Current: " + permissionLabel(permission)));
            if (selected) {
                lore.add(cm.translateColors("&bSelected"));
            }
            meta.setLore(lore);
            skull.setItemMeta(meta);
        }
        return skull;
    }

    private ItemStack accessMemberSkull(UUID member, ClanAccessPermission permission, boolean selected, UUID leaderId) {
        ConfigManager cm = plugin.getConfigManager();
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(member);
            meta.setOwningPlayer(op);
            String name = op.getName() != null ? op.getName() : member.toString().substring(0, 8);
            meta.setDisplayName("§f" + name);
            List<String> lore = new ArrayList<>();
            boolean isLeader = member.equals(leaderId);
            if (isLeader) {
                lore.add(cm.translateColors("&7Clan Leader &f(always enabled)"));
            } else {
                lore.add(cm.translateColors("&7Click to change permission"));
            }
            lore.add(cm.translateColors("&7Current: " + accessPermissionLabel(permission)));
            if (selected) {
                lore.add(cm.translateColors("&bSelected"));
            }
            meta.setLore(lore);
            skull.setItemMeta(meta);
        }
        return skull;
    }

    private ItemStack friendlyFireMemberSkull(UUID member, ClanFriendlyFirePermission permission, boolean selected, UUID leaderId) {
        ConfigManager cm = plugin.getConfigManager();
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(member);
            meta.setOwningPlayer(op);
            String name = op.getName() != null ? op.getName() : member.toString().substring(0, 8);
            meta.setDisplayName("§f" + name);
            List<String> lore = new ArrayList<>();
            boolean isLeader = member.equals(leaderId);
            if (isLeader) {
                lore.add(cm.translateColors("&7Clan Leader"));
            }
            lore.add(cm.translateColors("&7Click to change permission"));
            lore.add(cm.translateColors("&7Current: " + friendlyFireLabel(permission)));
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

    private String accessPermissionLabel(ClanAccessPermission permission) {
        switch (permission) {
            case EXECUTE:
                return "&a✅ Full access";
            case DENY:
                return "&c❌ No access";
            default:
                return "&e👁 See only";
        }
    }

    private String friendlyFireLabel(ClanFriendlyFirePermission permission) {
        if (permission == ClanFriendlyFirePermission.DENY) {
            return "&c❌ Disabled";
        }
        return "&a✅ Enabled";
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
