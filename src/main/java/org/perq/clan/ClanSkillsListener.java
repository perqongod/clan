package org.perq.clan;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
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
    private static final String DEFAULT_TITLE = "&8Clan Skills";
    private static final String RENAME_TITLE = "Clan Rename";
    private static final int DEFAULT_ROWS = 5;
    private static final int MIN_ROWS = 3;
    private static final int MAX_ROWS = 6;
    private static final int DEFAULT_OVERVIEW_SLOT = 40;
    private static final int DEFAULT_PREVIOUS_PAGE_SLOT = 36;
    private static final int DEFAULT_NEXT_PAGE_SLOT = 44;
    private static final int[] DEFAULT_SKILL_SLOTS = {21, 22, 23};
    private static final int RENAME_SKILL_INDEX = 2;
    private static final String DEFAULT_OVERVIEW_NAME = "&6Clan Skills";
    private static final String DEFAULT_PREVIOUS_NAME = "&ePrevious";
    private static final String DEFAULT_NEXT_NAME = "&eNext";
    private static final String DEFAULT_LOCKED_NAME = "&cLevel %level%";
    private static final String DEFAULT_REWARD_LORE = "&7%reward%";
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
        ConfigManager cm = plugin.getConfigManager();
        SkillsGuiLayout layout = loadSkillsGuiLayout(cm);
        Inventory inv = Bukkit.createInventory(null, layout.size, layout.title);
        pages.put(player.getUniqueId(), 0);
        populateSkillsInventory(inv, clan, player.getUniqueId(), layout);
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        ConfigManager cm = plugin.getConfigManager();
        if (event.getView().title().equals(getSkillsTitle(cm))) {
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
        ConfigManager cm = plugin.getConfigManager();
        SkillsGuiLayout layout = loadSkillsGuiLayout(cm);
        if (rawSlot == layout.overviewSlot) {
            return;
        }
        if (rawSlot == layout.previousSlot || rawSlot == layout.nextSlot) {
            int totalPages = getTotalPages(clan, cm, layout);
            int currentPage = pages.getOrDefault(player.getUniqueId(), 0);
            if (rawSlot == layout.previousSlot && currentPage > 0) {
                currentPage--;
            } else if (rawSlot == layout.nextSlot && currentPage < totalPages - 1) {
                currentPage++;
            } else {
                return;
            }
            pages.put(player.getUniqueId(), currentPage);
            populateSkillsInventory(event.getView().getTopInventory(), clan, player.getUniqueId(), layout);
            return;
        }

        int slotIndex = layout.skillSlots.indexOf(rawSlot);
        if (slotIndex < 0) return;
        int currentPage = pages.getOrDefault(player.getUniqueId(), 0);
        int skillIndex = (currentPage * layout.skillSlots.size()) + slotIndex;
        if (skillIndex != RENAME_SKILL_INDEX) return;
        List<ItemStack> skillEntries = buildSkillEntries(clan.getSkillPoints(), cm, plugin.getConfig());
        if (skillIndex >= skillEntries.size()) return;
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
        ConfigManager cm = plugin.getConfigManager();
        if (event.getView().title().equals(getSkillsTitle(cm))) {
            pages.remove(event.getPlayer().getUniqueId());
            return;
        }
        if (event.getView().title().equals(Component.text(RENAME_TITLE))) {
            renameSessions.remove(event.getPlayer().getUniqueId());
        }
    }

    private void populateSkillsInventory(Inventory inv, ClanData clan, UUID viewer, SkillsGuiLayout layout) {
        inv.clear();
        ConfigManager cm = plugin.getConfigManager();

        ItemStack filler = layout.filler;
        ItemStack accent = layout.accent;
        int rows = inv.getSize() / 9;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                inv.setItem((row * 9) + col, filler);
            }
        }
        for (int row = 1; row <= rows - 2; row++) {
            inv.setItem((row * 9) + 1, accent);
            inv.setItem((row * 9) + 7, accent);
        }
        for (int row = 2; row <= rows - 2; row++) {
            for (int col = 3; col <= 5; col++) {
                inv.setItem((row * 9) + col, accent);
            }
        }

        int points = clan.getSkillPoints();
        int nextUnlock = ClanSkillProgress.getNextUnlockPoints(points);
        int bonusSlots = ClanSkillProgress.getBonusMemberSlots(points);

        ItemStack overviewItem = buildOverviewItem(cm, plugin.getConfig(), clan, points, nextUnlock, bonusSlots);
        if (layout.overviewSlot >= 0 && layout.overviewSlot < inv.getSize()) {
            inv.setItem(layout.overviewSlot, overviewItem);
        }

        List<ItemStack> skillEntries = buildSkillEntries(points, cm, plugin.getConfig());
        int totalPages = Math.max(1, (skillEntries.size() + layout.skillSlots.size() - 1) / layout.skillSlots.size());
        int page = Math.min(pages.getOrDefault(viewer, 0), totalPages - 1);
        pages.put(viewer, page);

        int startIndex = page * layout.skillSlots.size();
        for (int i = 0; i < layout.skillSlots.size(); i++) {
            int index = startIndex + i;
            if (index >= skillEntries.size()) break;
            inv.setItem(layout.skillSlots.get(i), skillEntries.get(index));
        }

        if (layout.previousSlot >= 0 && layout.previousSlot < inv.getSize()) {
            inv.setItem(layout.previousSlot, layout.previousItem);
        }
        if (layout.nextSlot >= 0 && layout.nextSlot < inv.getSize()) {
            inv.setItem(layout.nextSlot, layout.nextItem);
        }

    }

    private int getTotalPages(ClanData clan, ConfigManager cm, SkillsGuiLayout layout) {
        int skillCount = buildSkillEntries(clan.getSkillPoints(), cm, plugin.getConfig()).size();
        return Math.max(1, (skillCount + layout.skillSlots.size() - 1) / layout.skillSlots.size());
    }

    private List<ItemStack> buildSkillEntries(int points, ConfigManager cm, FileConfiguration config) {
        ConfigurationSection skillsSection = config.getConfigurationSection("skills-gui.skills");
        List<ItemStack> entries = new ArrayList<>();

        entries.add(buildSkillEntry(cm, skillsSection,
                "chest",
                Material.CHEST,
                "&6Clan Chest",
                Collections.singletonList(DEFAULT_REWARD_LORE),
                Collections.emptyList(),
                1,
                ClanSkillProgress.getChestUnlockPoints(),
                ClanSkillProgress.hasChest(points),
                "Access to the clan chest"));

        entries.add(buildSkillEntry(cm, skillsSection,
                "spawn",
                Material.ENDER_EYE,
                "&6Clan Spawn",
                Collections.singletonList(DEFAULT_REWARD_LORE),
                Collections.emptyList(),
                2,
                ClanSkillProgress.getSpawnUnlockPoints(),
                ClanSkillProgress.hasSpawn(points),
                "Teleport to the clan spawn"));

        List<String> renameUnlockedLore = new ArrayList<>();
        renameUnlockedLore.add("&7Cooldown: &f72h");
        renameUnlockedLore.add("&7Click to rename (colors enabled)");
        entries.add(buildSkillEntry(cm, skillsSection,
                "rename",
                Material.NAME_TAG,
                "&6Clan Rename",
                Collections.singletonList(DEFAULT_REWARD_LORE),
                renameUnlockedLore,
                3,
                ClanSkillProgress.getRenameUnlockPoints(),
                ClanSkillProgress.hasRename(points),
                "Rename the clan tag"));

        return entries;
    }

    private ItemStack buildSkillEntry(ConfigManager cm,
                                      ConfigurationSection skillsSection,
                                      String skillKey,
                                      Material material,
                                      String name,
                                      List<String> defaultLore,
                                      List<String> defaultUnlockedLore,
                                      int level,
                                      int unlockPoints,
                                      boolean unlocked,
                                      String rewardDescription) {
        Map<String, String> placeholders = buildSkillPlaceholders(level, unlockPoints, rewardDescription);
        ConfigurationSection lockedSection = skillsSection == null ? null : skillsSection.getConfigurationSection("locked");
        if (!unlocked) {
            List<String> lockedLore = getConfiguredLore(lockedSection, "lore", Collections.emptyList());
            return buildConfiguredItem(cm, lockedSection, Material.RED_STAINED_GLASS_PANE,
                    DEFAULT_LOCKED_NAME, lockedLore, placeholders);
        }
        ConfigurationSection skillSection = skillsSection == null ? null : skillsSection.getConfigurationSection(skillKey);
        List<String> lore = getConfiguredLore(skillSection, "lore", defaultLore);
        List<String> unlockedLore = getConfiguredLore(skillSection, "unlocked-lore", defaultUnlockedLore);
        List<String> combinedLore = new ArrayList<>(lore);
        combinedLore.addAll(unlockedLore);
        return buildConfiguredItem(cm, skillSection, material, name, combinedLore, placeholders);
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

    private SkillsGuiLayout loadSkillsGuiLayout(ConfigManager cm) {
        FileConfiguration config = plugin.getConfig();
        int rows = clampRows(config.getInt("skills-gui.rows", DEFAULT_ROWS));
        int size = rows * 9;
        Component title = getSkillsTitle(cm);
        int overviewSlot = resolveSlot(config, "skills-gui.overview.slot", DEFAULT_OVERVIEW_SLOT, size);
        int previousSlot = resolveSlot(config, "skills-gui.navigation.previous.slot", DEFAULT_PREVIOUS_PAGE_SLOT, size);
        int nextSlot = resolveSlot(config, "skills-gui.navigation.next.slot", DEFAULT_NEXT_PAGE_SLOT, size);
        List<Integer> skillSlots = resolveSkillSlots(config, size);

        ItemStack filler = buildConfiguredItem(cm,
                config.getConfigurationSection("skills-gui.filler"),
                Material.GRAY_STAINED_GLASS_PANE,
                " ",
                Collections.emptyList(),
                Collections.emptyMap());
        ItemStack accent = buildConfiguredItem(cm,
                config.getConfigurationSection("skills-gui.accent"),
                Material.RED_STAINED_GLASS_PANE,
                " ",
                Collections.emptyList(),
                Collections.emptyMap());
        ItemStack previousItem = buildConfiguredItem(cm,
                config.getConfigurationSection("skills-gui.navigation.previous"),
                Material.ARROW,
                DEFAULT_PREVIOUS_NAME,
                Collections.emptyList(),
                Collections.emptyMap());
        ItemStack nextItem = buildConfiguredItem(cm,
                config.getConfigurationSection("skills-gui.navigation.next"),
                Material.ARROW,
                DEFAULT_NEXT_NAME,
                Collections.emptyList(),
                Collections.emptyMap());

        return new SkillsGuiLayout(size, title, overviewSlot, previousSlot, nextSlot, skillSlots,
                filler, accent, previousItem, nextItem);
    }

    private Component getSkillsTitle(ConfigManager cm) {
        String title = plugin.getConfig().getString("skills-gui.title", DEFAULT_TITLE);
        return LegacyComponentSerializer.legacySection().deserialize(cm.translateColors(title));
    }

    private int clampRows(int rows) {
        if (rows < MIN_ROWS) return MIN_ROWS;
        if (rows > MAX_ROWS) return MAX_ROWS;
        return rows;
    }

    private int resolveSlot(FileConfiguration config, String path, int defaultSlot, int size) {
        int slot = config.getInt(path, defaultSlot);
        if (slot >= 0 && slot < size) return slot;
        if (defaultSlot >= 0 && defaultSlot < size) return defaultSlot;
        return -1;
    }

    private List<Integer> resolveSkillSlots(FileConfiguration config, int size) {
        List<Integer> slots = config.getIntegerList("skills-gui.skills.slots");
        if (slots == null || slots.isEmpty()) {
            slots = new ArrayList<>();
            for (int slot : DEFAULT_SKILL_SLOTS) {
                slots.add(slot);
            }
        }
        List<Integer> valid = new ArrayList<>();
        for (Integer slot : slots) {
            if (slot == null) continue;
            if (slot >= 0 && slot < size && !valid.contains(slot)) {
                valid.add(slot);
            }
        }
        if (valid.isEmpty() && size > 0) {
            valid.add(0);
        }
        return valid;
    }

    private ItemStack buildOverviewItem(ConfigManager cm,
                                        FileConfiguration config,
                                        ClanData clan,
                                        int points,
                                        int nextUnlock,
                                        int bonusSlots) {
        ConfigurationSection section = config.getConfigurationSection("skills-gui.overview");
        List<String> defaultLore = new ArrayList<>();
        defaultLore.add("&7Unlock Points: &f%skill_points%");
        defaultLore.add("&7Leaderboard Points: &f%clan_points%");
        defaultLore.add("&7Next unlock at: &f%next_unlock%");
        defaultLore.add("&7Next reward: &f%next_reward%");
        defaultLore.add("&7Bonus slots: &f+%bonus_slots%");
        defaultLore.add("&7Gain +1 member slot every %bonus_step% points after %spawn_unlock%");
        defaultLore.add("&eProgress is automatic");
        List<String> lore = getConfiguredLore(section, "lore", defaultLore);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("skill_points", String.valueOf(points));
        placeholders.put("clan_points", String.valueOf(clan.getPoints()));
        placeholders.put("next_unlock", String.valueOf(nextUnlock));
        placeholders.put("next_reward", ClanSkillProgress.getRewardLabel(points));
        placeholders.put("bonus_slots", String.valueOf(bonusSlots));
        placeholders.put("bonus_step", String.valueOf(ClanSkillProgress.getBonusSlotStep()));
        placeholders.put("spawn_unlock", String.valueOf(ClanSkillProgress.getSpawnUnlockPoints()));
        return buildConfiguredItem(cm, section, Material.NETHER_STAR, DEFAULT_OVERVIEW_NAME, lore, placeholders);
    }

    private Map<String, String> buildSkillPlaceholders(int level, int unlockPoints, String rewardDescription) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("level", String.valueOf(level));
        placeholders.put("unlock_points", String.valueOf(unlockPoints));
        placeholders.put("reward", rewardDescription);
        placeholders.put("rename_cooldown_hours", String.valueOf((int) RENAME_COOLDOWN_HOURS));
        return placeholders;
    }

    private List<String> getConfiguredLore(ConfigurationSection section, String key, List<String> defaultLore) {
        if (section != null && section.contains(key)) {
            return new ArrayList<>(section.getStringList(key));
        }
        return new ArrayList<>(defaultLore);
    }

    private ItemStack buildConfiguredItem(ConfigManager cm,
                                          ConfigurationSection section,
                                          Material defaultMaterial,
                                          String defaultName,
                                          List<String> lore,
                                          Map<String, String> placeholders) {
        Material material = resolveMaterial(section, defaultMaterial);
        String name = defaultName;
        if (section != null && section.contains("name")) {
            name = section.getString("name", defaultName);
        }
        name = cm.translateColors(applyPlaceholders(name, placeholders));
        List<String> translatedLore = new ArrayList<>();
        for (String line : lore) {
            translatedLore.add(cm.translateColors(applyPlaceholders(line, placeholders)));
        }
        return namedItem(material, name, translatedLore.isEmpty() ? null : translatedLore);
    }

    private Material resolveMaterial(ConfigurationSection section, Material defaultMaterial) {
        if (section == null) return defaultMaterial;
        String materialName = section.getString("material");
        if (materialName == null || materialName.isEmpty()) return defaultMaterial;
        Material material = Material.matchMaterial(materialName);
        return material != null ? material : defaultMaterial;
    }

    private String applyPlaceholders(String text, Map<String, String> placeholders) {
        String result = text == null ? "" : text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return result;
    }

    private static class SkillsGuiLayout {
        private final int size;
        private final Component title;
        private final int overviewSlot;
        private final int previousSlot;
        private final int nextSlot;
        private final List<Integer> skillSlots;
        private final ItemStack filler;
        private final ItemStack accent;
        private final ItemStack previousItem;
        private final ItemStack nextItem;

        private SkillsGuiLayout(int size,
                                Component title,
                                int overviewSlot,
                                int previousSlot,
                                int nextSlot,
                                List<Integer> skillSlots,
                                ItemStack filler,
                                ItemStack accent,
                                ItemStack previousItem,
                                ItemStack nextItem) {
            this.size = size;
            this.title = title;
            this.overviewSlot = overviewSlot;
            this.previousSlot = previousSlot;
            this.nextSlot = nextSlot;
            this.skillSlots = skillSlots;
            this.filler = filler;
            this.accent = accent;
            this.previousItem = previousItem;
            this.nextItem = nextItem;
        }
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
