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
import org.bukkit.inventory.meta.SkullMeta;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;

public class ClanSettingsListener implements Listener {

    private static final String DEFAULT_MAIN_TITLE = "Clan Settings";
    private static final String DEFAULT_CHEST_TITLE = "Clan Chest Settings";
    private static final String DEFAULT_FRIENDLY_FIRE_TITLE = "Clan Friendly Fire Settings";
    private static final String DEFAULT_SKILLS_TITLE = "Clan Skills Settings";
    private static final String DEFAULT_SPAWN_TITLE = "Clan Spawn Settings";
    private static final String DEFAULT_CHEST_NAME = "&6Clan Chest";
    private static final String DEFAULT_FRIENDLY_FIRE_NAME = "&6Friendly Fire";
    private static final String DEFAULT_SKILLS_NAME = "&6Clan Skills";
    private static final String DEFAULT_SPAWN_NAME = "&6Clan Spawn";
    private static final String DEFAULT_INVITATIONS_NAME = "&6Invitations";
    private static final int DEFAULT_MAIN_ROWS = 3;
    private static final int DEFAULT_SUBMENU_ROWS = 6;
    private static final int DEFAULT_HEADER_SLOT = 4;
    private static final int DEFAULT_MEMBER_SLOT_START = 9;
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
        ConfigManager cm = plugin.getConfigManager();
        MainMenuLayout layout = loadMainMenuLayout(cm);
        Inventory inv = Bukkit.createInventory(null, layout.size,
                cm.getComponent("settings-gui.titles.main", DEFAULT_MAIN_TITLE));
        SettingsSession session = new SettingsSession(clan.getTag(), new ArrayList<>(clan.getMembers()));
        sessions.put(player.getUniqueId(), session);
        populateMainMenu(inv, player, clan, layout);
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        SettingsSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        ConfigManager cm = plugin.getConfigManager();
        MainMenuLayout mainLayout = loadMainMenuLayout(cm);
        SubmenuLayout chestLayout = loadSubmenuLayout(cm, "chest");
        SubmenuLayout friendlyLayout = loadSubmenuLayout(cm, "friendly-fire");
        SubmenuLayout skillsLayout = loadSubmenuLayout(cm, "skills");
        SubmenuLayout spawnLayout = loadSubmenuLayout(cm, "spawn");
        Component title = event.getView().title();
        boolean mainMenu = title.equals(cm.getComponent("settings-gui.titles.main", DEFAULT_MAIN_TITLE));
        boolean chestMenu = title.equals(cm.getComponent("settings-gui.titles.chest", DEFAULT_CHEST_TITLE));
        boolean friendlyMenu = title.equals(cm.getComponent("settings-gui.titles.friendly-fire",
                DEFAULT_FRIENDLY_FIRE_TITLE));
        boolean skillsMenu = title.equals(cm.getComponent("settings-gui.titles.skills", DEFAULT_SKILLS_TITLE));
        boolean spawnMenu = title.equals(cm.getComponent("settings-gui.titles.spawn", DEFAULT_SPAWN_TITLE));
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
            if (rawSlot == mainLayout.inviteToggleSlot) {
                boolean invitesDisabled = plugin.toggleInvitation(player);
                player.sendMessage(invitesDisabled ? cm.getMessage("toggle-off") : cm.getMessage("toggle-on"));
                refreshMainMenu(event.getView().getTopInventory(), player, clan);
                return;
            }
            if (rawSlot == mainLayout.chestSlot) {
                if (!clan.getLeader().equals(player.getUniqueId())) {
                    player.sendMessage(cm.getMessage("not-clan-leader"));
                    return;
                }
                session.members = new ArrayList<>(clan.getMembers());
                if (session.selectedMember != null && !session.members.contains(session.selectedMember)) {
                    session.selectedMember = null;
                }
                openChestSettings(player, clan, session, cm, chestLayout);
            }
            if (rawSlot == mainLayout.friendlyFireSlot) {
                if (!clan.getLeader().equals(player.getUniqueId())) {
                    player.sendMessage(cm.getMessage("not-clan-leader"));
                    return;
                }
                session.members = new ArrayList<>(clan.getMembers());
                if (session.selectedMember != null && !session.members.contains(session.selectedMember)) {
                    session.selectedMember = null;
                }
                openFriendlyFireSettings(player, clan, session, cm, friendlyLayout);
            }
            if (rawSlot == mainLayout.skillsSlot) {
                if (!clan.getLeader().equals(player.getUniqueId())) {
                    player.sendMessage(cm.getMessage("not-clan-leader"));
                    return;
                }
                session.members = new ArrayList<>(clan.getMembers());
                if (session.selectedMember != null && !session.members.contains(session.selectedMember)) {
                    session.selectedMember = null;
                }
                openSkillsSettings(player, clan, session, cm, skillsLayout);
            }
            if (rawSlot == mainLayout.spawnSlot) {
                if (!clan.getLeader().equals(player.getUniqueId())) {
                    player.sendMessage(cm.getMessage("not-clan-leader"));
                    return;
                }
                session.members = new ArrayList<>(clan.getMembers());
                if (session.selectedMember != null && !session.members.contains(session.selectedMember)) {
                    session.selectedMember = null;
                }
                openSpawnSettings(player, clan, session, cm, spawnLayout);
            }
            return;
        }

        if (chestMenu) {
            if (rawSlot == chestLayout.headerSlot) {
                if (session.selectedMember != null) {
                    if (isLeaderToggle(player, clan, session.selectedMember, "settings-leader-chest")) return;
                    togglePermission(player, clan, session.selectedMember);
                    refreshChestSettings(event.getView().getTopInventory(), clan, session, chestLayout);
                }
                return;
            }

            int memberIndex = chestLayout.memberSlots.indexOf(rawSlot);
            if (memberIndex < 0 || memberIndex >= session.members.size()) return;

            UUID member = session.members.get(memberIndex);
            if (isLeaderToggle(player, clan, member, "settings-leader-chest")) return;
            session.selectedMember = member;
            togglePermission(player, clan, member);
            refreshChestSettings(event.getView().getTopInventory(), clan, session, chestLayout);
            return;
        }

        if (friendlyMenu) {
            if (rawSlot == friendlyLayout.headerSlot) {
                if (session.selectedMember != null) {
                    toggleFriendlyFirePermission(player, clan, session.selectedMember);
                    refreshFriendlyFireSettings(event.getView().getTopInventory(), clan, session, friendlyLayout);
                }
                return;
            }

            int memberIndex = friendlyLayout.memberSlots.indexOf(rawSlot);
            if (memberIndex < 0 || memberIndex >= session.members.size()) return;

            UUID member = session.members.get(memberIndex);
            session.selectedMember = member;
            toggleFriendlyFirePermission(player, clan, member);
            refreshFriendlyFireSettings(event.getView().getTopInventory(), clan, session, friendlyLayout);
            return;
        }

        if (skillsMenu) {
            if (rawSlot == skillsLayout.headerSlot) {
                if (session.selectedMember != null) {
                    if (isLeaderToggle(player, clan, session.selectedMember, "settings-leader-skills")) return;
                    toggleSkillsPermission(player, clan, session.selectedMember);
                    refreshSkillsSettings(event.getView().getTopInventory(), clan, session, skillsLayout);
                }
                return;
            }

            int memberIndex = skillsLayout.memberSlots.indexOf(rawSlot);
            if (memberIndex < 0 || memberIndex >= session.members.size()) return;

            UUID member = session.members.get(memberIndex);
            if (isLeaderToggle(player, clan, member, "settings-leader-skills")) return;
            session.selectedMember = member;
            toggleSkillsPermission(player, clan, member);
            refreshSkillsSettings(event.getView().getTopInventory(), clan, session, skillsLayout);
            return;
        }

        if (spawnMenu) {
            if (rawSlot == spawnLayout.headerSlot) {
                if (session.selectedMember != null) {
                    if (isLeaderToggle(player, clan, session.selectedMember, "settings-leader-spawn")) return;
                    toggleSpawnPermission(player, clan, session.selectedMember);
                    refreshSpawnSettings(event.getView().getTopInventory(), clan, session, spawnLayout);
                }
                return;
            }

            int memberIndex = spawnLayout.memberSlots.indexOf(rawSlot);
            if (memberIndex < 0 || memberIndex >= session.members.size()) return;

            UUID member = session.members.get(memberIndex);
            if (isLeaderToggle(player, clan, member, "settings-leader-spawn")) return;
            session.selectedMember = member;
            toggleSpawnPermission(player, clan, member);
            refreshSpawnSettings(event.getView().getTopInventory(), clan, session, spawnLayout);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        ConfigManager cm = plugin.getConfigManager();
        Component title = event.getView().title();
        if (!title.equals(cm.getComponent("settings-gui.titles.main", DEFAULT_MAIN_TITLE))
                && !title.equals(cm.getComponent("settings-gui.titles.chest", DEFAULT_CHEST_TITLE))
                && !title.equals(cm.getComponent("settings-gui.titles.friendly-fire",
                        DEFAULT_FRIENDLY_FIRE_TITLE))
                && !title.equals(cm.getComponent("settings-gui.titles.skills", DEFAULT_SKILLS_TITLE))
                && !title.equals(cm.getComponent("settings-gui.titles.spawn", DEFAULT_SPAWN_TITLE))) {
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

    private void refreshChestSettings(Inventory inv, ClanData clan, SettingsSession session, SubmenuLayout layout) {
        inv.clear();
        populateChestSettings(inv, clan, session, layout);
    }

    private void refreshFriendlyFireSettings(Inventory inv, ClanData clan, SettingsSession session, SubmenuLayout layout) {
        inv.clear();
        populateFriendlyFireSettings(inv, clan, session, layout);
    }

    private void refreshSkillsSettings(Inventory inv, ClanData clan, SettingsSession session, SubmenuLayout layout) {
        inv.clear();
        populateSkillsSettings(inv, clan, session, layout);
    }

    private void refreshSpawnSettings(Inventory inv, ClanData clan, SettingsSession session, SubmenuLayout layout) {
        inv.clear();
        populateSpawnSettings(inv, clan, session, layout);
    }

    private void refreshMainMenu(Inventory inv, Player player, ClanData clan) {
        inv.clear();
        populateMainMenu(inv, player, clan, loadMainMenuLayout(plugin.getConfigManager()));
    }

    private void populateMainMenu(Inventory inv, Player player, ClanData clan, MainMenuLayout layout) {
        for (Integer slot : layout.fillerSlots) {
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, layout.filler);
            }
        }
        for (Integer slot : layout.accentSlots) {
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, layout.accent);
            }
        }
        setItemIfValid(inv, layout.chestSlot, clanChestItem(null));
        setItemIfValid(inv, layout.friendlyFireSlot, friendlyFireItem(null));
        setItemIfValid(inv, layout.inviteToggleSlot, invitationToggleItem(player));
        setItemIfValid(inv, layout.skillsSlot, clanSkillsItem(null));
        setItemIfValid(inv, layout.spawnSlot, clanSpawnItem(null));
    }

    private void populateChestSettings(Inventory inv, ClanData clan, SettingsSession session, SubmenuLayout layout) {
        applySubmenuFill(inv, layout);
        setItemIfValid(inv, layout.headerSlot, clanChestItem(session.selectedMember));

        UUID leaderId = clan.getLeader();
        int maxMembers = Math.min(session.members.size(), layout.memberSlots.size());
        for (int i = 0; i < maxMembers; i++) {
            UUID member = session.members.get(i);
            ClanChestPermission permission = effectivePermission(clan, member, leaderId);
            boolean selected = member.equals(session.selectedMember);
            inv.setItem(layout.memberSlots.get(i), memberSkull(member, permission, selected, leaderId));
        }

        fillEmptyMembers(inv, layout, maxMembers);
    }

    private void populateFriendlyFireSettings(Inventory inv, ClanData clan, SettingsSession session, SubmenuLayout layout) {
        applySubmenuFill(inv, layout);
        setItemIfValid(inv, layout.headerSlot, friendlyFireItem(session.selectedMember));

        int maxMembers = Math.min(session.members.size(), layout.memberSlots.size());
        for (int i = 0; i < maxMembers; i++) {
            UUID member = session.members.get(i);
            ClanFriendlyFirePermission permission = effectiveFriendlyFirePermission(clan, member);
            boolean selected = member.equals(session.selectedMember);
            inv.setItem(layout.memberSlots.get(i), friendlyFireMemberSkull(member, permission, selected, clan.getLeader()));
        }

        fillEmptyMembers(inv, layout, maxMembers);
    }

    private void populateSkillsSettings(Inventory inv, ClanData clan, SettingsSession session, SubmenuLayout layout) {
        applySubmenuFill(inv, layout);
        setItemIfValid(inv, layout.headerSlot, clanSkillsItem(session.selectedMember));

        UUID leaderId = clan.getLeader();
        int maxMembers = Math.min(session.members.size(), layout.memberSlots.size());
        for (int i = 0; i < maxMembers; i++) {
            UUID member = session.members.get(i);
            ClanAccessPermission permission = effectiveSkillsPermission(clan, member, leaderId);
            boolean selected = member.equals(session.selectedMember);
            inv.setItem(layout.memberSlots.get(i), accessMemberSkull(member, permission, selected, leaderId));
        }

        fillEmptyMembers(inv, layout, maxMembers);
    }

    private void populateSpawnSettings(Inventory inv, ClanData clan, SettingsSession session, SubmenuLayout layout) {
        applySubmenuFill(inv, layout);
        setItemIfValid(inv, layout.headerSlot, clanSpawnItem(session.selectedMember));

        UUID leaderId = clan.getLeader();
        int maxMembers = Math.min(session.members.size(), layout.memberSlots.size());
        for (int i = 0; i < maxMembers; i++) {
            UUID member = session.members.get(i);
            ClanAccessPermission permission = effectiveSpawnPermission(clan, member, leaderId);
            boolean selected = member.equals(session.selectedMember);
            inv.setItem(layout.memberSlots.get(i), accessMemberSkull(member, permission, selected, leaderId));
        }

        fillEmptyMembers(inv, layout, maxMembers);
    }

    private void openChestSettings(Player player, ClanData clan, SettingsSession session, ConfigManager cm, SubmenuLayout layout) {
        session.switching = true;
        Inventory inv = Bukkit.createInventory(null, layout.size,
                cm.getComponent("settings-gui.titles.chest", DEFAULT_CHEST_TITLE));
        populateChestSettings(inv, clan, session, layout);
        player.openInventory(inv);
    }

    private void openFriendlyFireSettings(Player player, ClanData clan, SettingsSession session, ConfigManager cm, SubmenuLayout layout) {
        session.switching = true;
        Inventory inv = Bukkit.createInventory(null, layout.size,
                cm.getComponent("settings-gui.titles.friendly-fire", DEFAULT_FRIENDLY_FIRE_TITLE));
        populateFriendlyFireSettings(inv, clan, session, layout);
        player.openInventory(inv);
    }

    private void openSkillsSettings(Player player, ClanData clan, SettingsSession session, ConfigManager cm, SubmenuLayout layout) {
        session.switching = true;
        Inventory inv = Bukkit.createInventory(null, layout.size,
                cm.getComponent("settings-gui.titles.skills", DEFAULT_SKILLS_TITLE));
        populateSkillsSettings(inv, clan, session, layout);
        player.openInventory(inv);
    }

    private void openSpawnSettings(Player player, ClanData clan, SettingsSession session, ConfigManager cm, SubmenuLayout layout) {
        session.switching = true;
        Inventory inv = Bukkit.createInventory(null, layout.size,
                cm.getComponent("settings-gui.titles.spawn", DEFAULT_SPAWN_TITLE));
        populateSpawnSettings(inv, clan, session, layout);
        player.openInventory(inv);
    }

    private void togglePermission(Player player, ClanData clan, UUID member) {
        ClanChestPermission current = clan.getChestPermission(member);
        ClanChestPermission next = current == ClanChestPermission.EXECUTE
                ? ClanChestPermission.VIEW
                : ClanChestPermission.EXECUTE;
        clan.setChestPermission(member, next);
        try {
            plugin.getFileManager().saveClan(clan);
        } catch (IOException e) {
            player.sendMessage(plugin.getConfigManager().formatPlain(plugin.getConfigManager().getPrefix() + "Error saving."));
        }
    }

    private void toggleFriendlyFirePermission(Player player, ClanData clan, UUID member) {
        ClanFriendlyFirePermission current = clan.getFriendlyFirePermission(member);
        ClanFriendlyFirePermission next = current.next();
        clan.setFriendlyFirePermission(member, next);
        try {
            plugin.getFileManager().saveClan(clan);
        } catch (IOException e) {
            player.sendMessage(plugin.getConfigManager().formatPlain(plugin.getConfigManager().getPrefix() + "Error saving."));
        }
    }

    private void toggleSkillsPermission(Player player, ClanData clan, UUID member) {
        ClanAccessPermission current = clan.getSkillsPermission(member);
        ClanAccessPermission next = current.next();
        clan.setSkillsPermission(member, next);
        try {
            plugin.getFileManager().saveClan(clan);
        } catch (IOException e) {
            player.sendMessage(plugin.getConfigManager().formatPlain(plugin.getConfigManager().getPrefix() + "Error saving."));
        }
    }

    private void toggleSpawnPermission(Player player, ClanData clan, UUID member) {
        ClanAccessPermission current = clan.getSpawnPermission(member);
        ClanAccessPermission next = current == ClanAccessPermission.EXECUTE
                ? ClanAccessPermission.DENY
                : ClanAccessPermission.EXECUTE;
        clan.setSpawnPermission(member, next);
        try {
            plugin.getFileManager().saveClan(clan);
        } catch (IOException e) {
            player.sendMessage(plugin.getConfigManager().formatPlain(plugin.getConfigManager().getPrefix() + "Error saving."));
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
        Map<String, String> placeholders = new HashMap<>();
        String selectedName = resolveSelectedName(selectedMember);
        if (selectedName != null) {
            placeholders.put("%selected%", selectedName);
        }
        List<String> defaultLore = List.of(
                "&7Click to configure",
                "&7✅ &aFull access",
                "&7❌ &cNo access (view only)"
        );
        List<String> defaultSelectedLore = List.of("&7Selected: &f%selected%");
        return buildSettingsItem("settings-gui.items.clan-chest", Material.CHEST,
                DEFAULT_CHEST_NAME, defaultLore, placeholders, selectedName != null, defaultSelectedLore);
    }

    private ItemStack friendlyFireItem(UUID selectedMember) {
        Map<String, String> placeholders = new HashMap<>();
        String selectedName = resolveSelectedName(selectedMember);
        if (selectedName != null) {
            placeholders.put("%selected%", selectedName);
        }
        List<String> defaultLore = List.of(
                "&7Click to configure",
                "&7✅ &aEnabled",
                "&7❌ &cDisabled"
        );
        List<String> defaultSelectedLore = List.of("&7Selected: &f%selected%");
        return buildSettingsItem("settings-gui.items.friendly-fire", Material.IRON_SWORD,
                DEFAULT_FRIENDLY_FIRE_NAME, defaultLore, placeholders, selectedName != null, defaultSelectedLore);
    }

    private ItemStack clanSkillsItem(UUID selectedMember) {
        Map<String, String> placeholders = new HashMap<>();
        String selectedName = resolveSelectedName(selectedMember);
        if (selectedName != null) {
            placeholders.put("%selected%", selectedName);
        }
        List<String> defaultLore = List.of(
                "&7Click to configure",
                "&7✅ &aFull access",
                "&7❌ &cNo access"
        );
        List<String> defaultSelectedLore = List.of("&7Selected: &f%selected%");
        return buildSettingsItem("settings-gui.items.clan-skills", Material.NETHER_STAR,
                DEFAULT_SKILLS_NAME, defaultLore, placeholders, selectedName != null, defaultSelectedLore);
    }

    private ItemStack clanSpawnItem(UUID selectedMember) {
        Map<String, String> placeholders = new HashMap<>();
        String selectedName = resolveSelectedName(selectedMember);
        if (selectedName != null) {
            placeholders.put("%selected%", selectedName);
        }
        List<String> defaultLore = List.of(
                "&7Click to configure",
                "&7✅ &aFull access",
                "&7👁 &eSee only",
                "&7❌ &cNo access"
        );
        List<String> defaultSelectedLore = List.of("&7Selected: &f%selected%");
        return buildSettingsItem("settings-gui.items.clan-spawn", Material.ENDER_EYE,
                DEFAULT_SPAWN_NAME, defaultLore, placeholders, selectedName != null, defaultSelectedLore);
    }

    private ItemStack invitationToggleItem(Player player) {
        boolean invitesEnabled = plugin.isInvitesEnabled(player.getUniqueId());
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%status%", invitesEnabled ? "&aEnabled" : "&cDisabled");
        List<String> defaultLore = List.of(
                "&7Click to toggle",
                "&7Status: %status%"
        );
        return buildSettingsItem("settings-gui.items.invitations", Material.BELL,
                DEFAULT_INVITATIONS_NAME, defaultLore, placeholders, false, List.of());
    }

    private ItemStack buildSettingsItem(String path,
                                        Material defaultMaterial,
                                        String defaultName,
                                        List<String> defaultLore,
                                        Map<String, String> placeholders,
                                        boolean includeSelected,
                                        List<String> defaultSelectedLore) {
        ConfigManager cm = plugin.getConfigManager();
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection section = config.getConfigurationSection(path);
        ItemStack item = GuiConfigHelper.buildConfiguredItem(cm, section, defaultMaterial, defaultName, defaultLore, placeholders);
        if (!includeSelected) return item;

        List<String> selectedLore = GuiConfigHelper.getConfiguredLore(section, "selected-lore", defaultSelectedLore);
        if (selectedLore.isEmpty()) return item;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        List<String> lore = meta.getLore();
        List<String> updatedLore = lore == null ? new ArrayList<>() : new ArrayList<>(lore);
        for (String line : selectedLore) {
            String applied = GuiConfigHelper.applyPlaceholders(line, placeholders);
            updatedLore.add(cm.translateColors(applied));
        }
        meta.setLore(updatedLore);
        item.setItemMeta(meta);
        return item;
    }

    private String resolveSelectedName(UUID selectedMember) {
        if (selectedMember == null) return null;
        String name = Bukkit.getOfflinePlayer(selectedMember).getName();
        if (name == null) {
            name = selectedMember.toString().substring(0, 8);
        }
        return name;
    }

    private void setItemIfValid(Inventory inv, int slot, ItemStack item) {
        if (slot >= 0 && slot < inv.getSize()) {
            inv.setItem(slot, item);
        }
    }

    private void applySubmenuFill(Inventory inv, SubmenuLayout layout) {
        for (Integer slot : layout.fillerSlots) {
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, layout.filler);
            }
        }
    }

    private void fillEmptyMembers(Inventory inv, SubmenuLayout layout, int startIndex) {
        for (int i = startIndex; i < layout.memberSlots.size(); i++) {
            int slot = layout.memberSlots.get(i);
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, layout.emptyMemberItem);
            }
        }
    }

    private MainMenuLayout loadMainMenuLayout(ConfigManager cm) {
        FileConfiguration config = plugin.getConfig();
        int rows = GuiConfigHelper.clampRows(config.getInt("settings-gui.main.rows", DEFAULT_MAIN_ROWS));
        int size = rows * 9;
        ItemStack filler = GuiConfigHelper.buildConfiguredItem(cm,
                config.getConfigurationSection("settings-gui.main.filler"),
                Material.ORANGE_STAINED_GLASS_PANE,
                " ",
                List.of(),
                Map.of());
        List<Integer> fillerSlots = GuiConfigHelper.resolveOptionalSlots(config, "settings-gui.main.filler.slots", size);
        if (fillerSlots.isEmpty()) {
            fillerSlots = buildDefaultMainFillerSlots(rows);
        }
        ItemStack accent = GuiConfigHelper.buildConfiguredItem(cm,
                config.getConfigurationSection("settings-gui.main.accent"),
                Material.GRAY_STAINED_GLASS_PANE,
                " ",
                List.of(),
                Map.of());
        List<Integer> accentSlots = GuiConfigHelper.resolveOptionalSlots(config, "settings-gui.main.accent.slots", size);
        if (accentSlots.isEmpty()) {
            accentSlots = buildDefaultMainAccentSlots(rows);
        }

        int inviteSlot = GuiConfigHelper.resolveSlot(config, "settings-gui.items.invitations.slot", MAIN_INVITE_TOGGLE_SLOT, size);
        int chestSlot = GuiConfigHelper.resolveSlot(config, "settings-gui.items.clan-chest.slot", MAIN_CHEST_SLOT, size);
        int friendlySlot = GuiConfigHelper.resolveSlot(config, "settings-gui.items.friendly-fire.slot", MAIN_FRIENDLY_FIRE_SLOT, size);
        int skillsSlot = GuiConfigHelper.resolveSlot(config, "settings-gui.items.clan-skills.slot", MAIN_SKILLS_SLOT, size);
        int spawnSlot = GuiConfigHelper.resolveSlot(config, "settings-gui.items.clan-spawn.slot", MAIN_SPAWN_SLOT, size);

        return new MainMenuLayout(size, filler, fillerSlots, accent, accentSlots,
                inviteSlot, chestSlot, friendlySlot, skillsSlot, spawnSlot);
    }

    private SubmenuLayout loadSubmenuLayout(ConfigManager cm, String key) {
        FileConfiguration config = plugin.getConfig();
        String basePath = "settings-gui.submenus." + key;
        int rows = GuiConfigHelper.clampRows(config.getInt(basePath + ".rows", DEFAULT_SUBMENU_ROWS));
        int size = rows * 9;
        ItemStack filler = GuiConfigHelper.buildConfiguredItem(cm,
                config.getConfigurationSection(basePath + ".filler"),
                Material.GRAY_STAINED_GLASS_PANE,
                " ",
                List.of(),
                Map.of());
        List<Integer> fillerSlots = GuiConfigHelper.resolveOptionalSlots(config, basePath + ".filler.slots", size);
        if (fillerSlots.isEmpty()) {
            fillerSlots = buildDefaultSubmenuFillerSlots(size);
        }
        ItemStack emptyMemberItem = GuiConfigHelper.buildConfiguredItem(cm,
                config.getConfigurationSection(basePath + ".empty-member"),
                Material.BLACK_STAINED_GLASS_PANE,
                " ",
                List.of(),
                Map.of());

        int headerSlot = GuiConfigHelper.resolveSlot(config, basePath + ".header.slot", DEFAULT_HEADER_SLOT, size);
        List<Integer> memberSlots = buildMemberSlots(config, basePath, size);
        return new SubmenuLayout(size, filler, fillerSlots, emptyMemberItem, headerSlot, memberSlots);
    }

    private List<Integer> buildDefaultMainFillerSlots(int rows) {
        LinkedHashSet<Integer> slots = new LinkedHashSet<>();
        int size = rows * 9;
        if (rows <= 0) return new ArrayList<>();
        for (int col = 0; col < 9; col++) {
            slots.add(col);
            int bottom = size - 9 + col;
            if (bottom != col && bottom >= 0 && bottom < size) {
                slots.add(bottom);
            }
        }
        return new ArrayList<>(slots);
    }

    private List<Integer> buildDefaultMainAccentSlots(int rows) {
        List<Integer> slots = new ArrayList<>();
        int size = rows * 9;
        for (int row = 1; row <= rows - 2; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = (row * 9) + col;
                if (slot >= 0 && slot < size) {
                    slots.add(slot);
                }
            }
        }
        return slots;
    }

    private List<Integer> buildDefaultSubmenuFillerSlots(int size) {
        List<Integer> slots = new ArrayList<>();
        for (int col = 0; col < Math.min(9, size); col++) {
            slots.add(col);
        }
        return slots;
    }

    private List<Integer> buildMemberSlots(FileConfiguration config, String basePath, int size) {
        List<Integer> defaults = new ArrayList<>();
        for (int slot = DEFAULT_MEMBER_SLOT_START; slot < size; slot++) {
            defaults.add(slot);
        }
        return GuiConfigHelper.resolveSlots(config, basePath + ".member-slots", defaults, size);
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
                return "&a✅ Full access";
            default:
                return "&c❌ No access (view only)";
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

    private static class MainMenuLayout {
        private final int size;
        private final ItemStack filler;
        private final List<Integer> fillerSlots;
        private final ItemStack accent;
        private final List<Integer> accentSlots;
        private final int inviteToggleSlot;
        private final int chestSlot;
        private final int friendlyFireSlot;
        private final int skillsSlot;
        private final int spawnSlot;

        private MainMenuLayout(int size,
                               ItemStack filler,
                               List<Integer> fillerSlots,
                               ItemStack accent,
                               List<Integer> accentSlots,
                               int inviteToggleSlot,
                               int chestSlot,
                               int friendlyFireSlot,
                               int skillsSlot,
                               int spawnSlot) {
            this.size = size;
            this.filler = filler;
            this.fillerSlots = fillerSlots;
            this.accent = accent;
            this.accentSlots = accentSlots;
            this.inviteToggleSlot = inviteToggleSlot;
            this.chestSlot = chestSlot;
            this.friendlyFireSlot = friendlyFireSlot;
            this.skillsSlot = skillsSlot;
            this.spawnSlot = spawnSlot;
        }
    }

    private static class SubmenuLayout {
        private final int size;
        private final ItemStack filler;
        private final List<Integer> fillerSlots;
        private final ItemStack emptyMemberItem;
        private final int headerSlot;
        private final List<Integer> memberSlots;

        private SubmenuLayout(int size,
                              ItemStack filler,
                              List<Integer> fillerSlots,
                              ItemStack emptyMemberItem,
                              int headerSlot,
                              List<Integer> memberSlots) {
            this.size = size;
            this.filler = filler;
            this.fillerSlots = fillerSlots;
            this.emptyMemberItem = emptyMemberItem;
            this.headerSlot = headerSlot;
            this.memberSlots = memberSlots;
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
