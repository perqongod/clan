package org.perq.clan;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class ClanSkillsListener implements Listener {
    private static final Component TITLE = Component.text("Clan Skills", NamedTextColor.DARK_GRAY);
    private static final String RENAME_TITLE = "Clan Rename";
    private static final int INVENTORY_SIZE = 45;
    private static final int OVERVIEW_SLOT = 40;
    private static final int MEMBERS_SLOT = 42;
    private static final int PREVIOUS_PAGE_SLOT = 36;
    private static final int NEXT_PAGE_SLOT = 44;
    private static final int[] SKILL_SLOTS = {19, 22, 25};
    private static final int ANVIL_INPUT_SLOT = 0;
    private static final int ANVIL_RESULT_SLOT = 2;
    private static final double RENAME_COOLDOWN_HOURS = 72.0;
    private static final long RENAME_COOLDOWN_MS = (long) (RENAME_COOLDOWN_HOURS * 3_600_000L);

    private final Clan plugin;
    private final Map<UUID, Integer> pages = new HashMap<>();
    private final Map<UUID, String> renameSessions = new HashMap<>();

    public ClanSkillsListener(Clan plugin) {
        this.plugin = plugin;
    }

    public void openGui(Player player, ClanData clan) {
        Inventory inv = Bukkit.createInventory(null, INVENTORY_SIZE, TITLE);
        pages.put(player.getUniqueId(), 0);
        populateSkillsInventory(inv, clan, player.getUniqueId());
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.getView().title().equals(TITLE)) {
            handleSkillsClick(event);
            return;
        }
        if (event.getView().title().equals(Component.text(RENAME_TITLE))) {
            handleRenameClick(event);
        }
    }

    private void handleSkillsClick(InventoryClickEvent event) {
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
            return;
        }
        if (rawSlot == PREVIOUS_PAGE_SLOT || rawSlot == NEXT_PAGE_SLOT) {
            ConfigManager cm = plugin.getConfigManager();
            int totalPages = getTotalPages(clan, cm);
            int currentPage = pages.getOrDefault(player.getUniqueId(), 0);
            if (rawSlot == PREVIOUS_PAGE_SLOT && currentPage > 0) {
                currentPage--;
            } else if (rawSlot == NEXT_PAGE_SLOT && currentPage < totalPages - 1) {
                currentPage++;
            } else {
                return;
            }
            pages.put(player.getUniqueId(), currentPage);
            populateSkillsInventory(event.getView().getTopInventory(), clan, player.getUniqueId());
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.NAME_TAG) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        ConfigManager cm = plugin.getConfigManager();
        String displayName = meta.getDisplayName();
        if (displayName == null) return;
        String strippedName = ChatColor.stripColor(displayName);
        if (!"Clan Rename".equals(strippedName)) {
            return;
        }
        if (!clan.getLeader().equals(player.getUniqueId())) return;
        if (!ClanSkillProgress.hasRename(clan.getSkillPoints())) return;
        openRenameAnvil(player, clan, cm);
    }

    private void handleRenameClick(InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) return;
        event.setCancelled(true);
        if (rawSlot != ANVIL_RESULT_SLOT) return;
        Player player = (Player) event.getWhoClicked();
        if (!renameSessions.containsKey(player.getUniqueId())) return;
        ItemStack result = event.getView().getTopInventory().getItem(ANVIL_RESULT_SLOT);
        if (result == null) return;
        ItemMeta meta = result.getItemMeta();
        if (meta == null || meta.getDisplayName() == null) return;
        ConfigManager cm = plugin.getConfigManager();
        String newTag = cm.normalizeTag(meta.getDisplayName().trim());
        if (newTag.isEmpty()) return;
        ClanData clan = getPlayerClan(player.getUniqueId());
        if (clan == null) {
            player.closeInventory();
            renameSessions.remove(player.getUniqueId());
            return;
        }
        if (!clan.getLeader().equals(player.getUniqueId())) return;
        if (!ClanSkillProgress.hasRename(clan.getSkillPoints())) return;
        if (isRenameOnCooldown(clan)) return;
        boolean allowColors = player.hasPermission("clan.vip")
                || ClanSkillProgress.hasRename(clan.getSkillPoints());
        TagValidator.ValidationResult validation = new TagValidator(plugin).validate(newTag, allowColors);
        if (!validation.isValid()) return;
        if (plugin.getFileManager().loadClan(newTag) != null) return;
        player.closeInventory();
        renameSessions.remove(player.getUniqueId());
        applyRename(clan, newTag);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().title().equals(TITLE)) {
            pages.remove(event.getPlayer().getUniqueId());
            return;
        }
        if (event.getView().title().equals(Component.text(RENAME_TITLE))) {
            renameSessions.remove(event.getPlayer().getUniqueId());
        }
    }

    private void populateSkillsInventory(Inventory inv, ClanData clan, UUID viewer) {
        inv.clear();
        ConfigManager cm = plugin.getConfigManager();

        ItemStack filler = namedItem(Material.RED_STAINED_GLASS_PANE, " ");
        ItemStack border = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        int rows = inv.getSize() / 9;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = (row * 9) + col;
                boolean isBorder = row == 0 || row == rows - 1 || col == 0 || col == 8;
                inv.setItem(slot, isBorder ? border : filler);
            }
        }

        int points = clan.getSkillPoints();
        int nextUnlock = ClanSkillProgress.getNextUnlockPoints(points);
        int bonusSlots = ClanSkillProgress.getBonusMemberSlots(points);

        List<String> overviewLore = new ArrayList<>();
        overviewLore.add(cm.translateColors("&7Unlock Points: &f" + points));
        overviewLore.add(cm.translateColors("&7Leaderboard Points: &f" + clan.getPoints()));
        overviewLore.add(cm.translateColors("&7Next unlock at: &f" + nextUnlock));
        overviewLore.add(cm.translateColors("&7Next reward: &f" + ClanSkillProgress.getRewardLabel(points)));
        overviewLore.add(cm.translateColors("&eProgress is automatic"));
        inv.setItem(OVERVIEW_SLOT, namedItem(Material.NETHER_STAR, cm.translateColors("&6Clan Skills"), overviewLore));

        List<ItemStack> skillEntries = buildSkillEntries(points, cm);
        int totalPages = Math.max(1, (skillEntries.size() + SKILL_SLOTS.length - 1) / SKILL_SLOTS.length);
        int page = Math.min(pages.getOrDefault(viewer, 0), totalPages - 1);
        pages.put(viewer, page);

        int startIndex = page * SKILL_SLOTS.length;
        for (int i = 0; i < SKILL_SLOTS.length; i++) {
            int index = startIndex + i;
            if (index >= skillEntries.size()) break;
            inv.setItem(SKILL_SLOTS[i], skillEntries.get(index));
        }

        inv.setItem(PREVIOUS_PAGE_SLOT, arrowItem(cm.translateColors("&ePrevious")));
        inv.setItem(NEXT_PAGE_SLOT, arrowItem(cm.translateColors("&eNext")));

        List<String> memberLore = new ArrayList<>();
        memberLore.add(cm.translateColors("&7Bonus slots: &f+" + bonusSlots));
        memberLore.add(cm.translateColors("&7Gain +1 member slot every " + ClanSkillProgress.getBonusSlotStep()
                + " points after " + ClanSkillProgress.getSpawnUnlockPoints()));
        inv.setItem(MEMBERS_SLOT, namedItem(Material.PAPER, cm.translateColors("&6Member Slots"), memberLore));
    }

    private int getTotalPages(ClanData clan, ConfigManager cm) {
        int skillCount = buildSkillEntries(clan.getSkillPoints(), cm).size();
        return Math.max(1, (skillCount + SKILL_SLOTS.length - 1) / SKILL_SLOTS.length);
    }

    private List<ItemStack> buildSkillEntries(int points, ConfigManager cm) {
        List<ItemStack> entries = new ArrayList<>();

        entries.add(buildSkillEntry(cm,
                Material.CHEST,
                "Clan Chest",
                1,
                ClanSkillProgress.getChestUnlockPoints(),
                ClanSkillProgress.hasChest(points),
                Collections.emptyList(),
                "Access to the clan chest"));

        entries.add(buildSkillEntry(cm,
                Material.ENDER_EYE,
                "Clan Spawn",
                2,
                ClanSkillProgress.getSpawnUnlockPoints(),
                ClanSkillProgress.hasSpawn(points),
                Collections.emptyList(),
                "Teleport to the clan spawn"));

        List<String> renameUnlockedLore = new ArrayList<>();
        renameUnlockedLore.add(cm.translateColors("&7Cooldown: &f72h"));
        renameUnlockedLore.add(cm.translateColors("&7Click to rename (colors enabled)"));
        entries.add(buildSkillEntry(cm,
                Material.NAME_TAG,
                "Clan Rename",
                3,
                ClanSkillProgress.getRenameUnlockPoints(),
                ClanSkillProgress.hasRename(points),
                renameUnlockedLore,
                "Rename the clan tag"));

        return entries;
    }

    private ItemStack buildSkillEntry(ConfigManager cm,
                                      Material material,
                                      String name,
                                      int level,
                                      int unlockPoints,
                                      boolean unlocked,
                                      List<String> unlockedLore,
                                      String rewardDescription) {
        if (!unlocked) {
            return namedItem(Material.RED_STAINED_GLASS_PANE, cm.translateColors("&cLevel " + level));
        }
        List<String> lore = new ArrayList<>();
        lore.add(cm.translateColors("&7" + rewardDescription));
        lore.addAll(unlockedLore);
        return namedItem(material, cm.translateColors("&6" + name), lore);
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

    private boolean isRenameOnCooldown(ClanData clan) {
        long lastRename = clan.getLastRenameAt();
        if (lastRename <= 0) return false;
        long elapsed = System.currentTimeMillis() - lastRename;
        return elapsed < RENAME_COOLDOWN_MS;
    }

    private void applyRename(ClanData clan, String newTag) {
        FileManager fileManager = plugin.getFileManager();
        String oldTag = clan.getTag();
        long oldLastRenameAt = clan.getLastRenameAt();
        clan.setTag(newTag);
        clan.setLastRenameAt(System.currentTimeMillis());
        try {
            fileManager.saveClan(clan);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "[Clan] Failed to save clan data for " + newTag + ".", e);
            clan.setTag(oldTag);
            clan.setLastRenameAt(oldLastRenameAt);
            return;
        }
        boolean allPlayersSaved = true;
        for (UUID mem : clan.getMembers()) {
            PlayerData md = fileManager.loadPlayer(mem);
            if (md != null) {
                md.setClanTag(newTag);
                try {
                    fileManager.savePlayer(mem, md);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING,
                            "[Clan] Failed to save player data for " + mem
                                    + " while renaming " + oldTag + " to " + newTag + ".", e);
                    allPlayersSaved = false;
                }
            }
        }
        if (allPlayersSaved) {
            fileManager.deleteClan(oldTag);
        } else {
            plugin.getLogger().warning("[Clan] Skipped deleting old clan data for " + oldTag
                    + " after rename to " + newTag + " due to player save failures. Manual cleanup may be required.");
        }
    }

    private void openRenameAnvil(Player player, ClanData clan, ConfigManager cm) {
        Inventory anvil = Bukkit.createInventory(player, InventoryType.ANVIL, RENAME_TITLE);
        ItemStack tagItem = namedItem(Material.NAME_TAG, cm.translateColors("&f" + clan.getTag()));
        anvil.setItem(ANVIL_INPUT_SLOT, tagItem);
        renameSessions.put(player.getUniqueId(), clan.getTag());
        player.openInventory(anvil);
    }
}
