package org.perq.clan;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class GuiConfigHelper {
    private static final int MIN_ROWS = 1;
    private static final int MAX_ROWS = 6;

    private GuiConfigHelper() {
    }

    static int clampRows(int rows) {
        if (rows < MIN_ROWS) return MIN_ROWS;
        if (rows > MAX_ROWS) return MAX_ROWS;
        return rows;
    }

    static int resolveSlot(FileConfiguration config, String path, int defaultSlot, int size) {
        int slot = config.getInt(path, defaultSlot);
        if (slot >= 0 && slot < size) return slot;
        if (defaultSlot >= 0 && defaultSlot < size) return defaultSlot;
        return -1;
    }

    static List<Integer> resolveOptionalSlots(FileConfiguration config, String path, int size) {
        List<Integer> slots = config.getIntegerList(path);
        if (slots == null || slots.isEmpty()) return null;
        Set<Integer> valid = new LinkedHashSet<>();
        for (Integer slot : slots) {
            if (slot == null) continue;
            if (slot >= 0 && slot < size) {
                valid.add(slot);
            }
        }
        return valid.isEmpty() ? null : new ArrayList<>(valid);
    }

    static List<Integer> resolveSlots(FileConfiguration config, String path, List<Integer> defaults, int size) {
        List<Integer> slots = config.getIntegerList(path);
        if (slots == null || slots.isEmpty()) {
            slots = defaults == null ? Collections.emptyList() : new ArrayList<>(defaults);
        }
        Set<Integer> valid = new LinkedHashSet<>();
        for (Integer slot : slots) {
            if (slot == null) continue;
            if (slot >= 0 && slot < size) {
                valid.add(slot);
            }
        }
        if (valid.isEmpty() && size > 0) {
            valid.add(size / 2);
        }
        return new ArrayList<>(valid);
    }

    static List<Integer> buildDefaultSlots(int size) {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < size; slot++) {
            slots.add(slot);
        }
        return slots;
    }

    static List<String> getConfiguredLore(ConfigurationSection section, String key, List<String> defaultLore) {
        if (section != null && section.contains(key)) {
            List<String> lore = section.getStringList(key);
            if (lore != null) {
                return new ArrayList<>(lore);
            }
        }
        return defaultLore == null ? Collections.emptyList() : new ArrayList<>(defaultLore);
    }

    static ItemStack buildConfiguredItem(ConfigManager cm,
                                         ConfigurationSection section,
                                         Material defaultMaterial,
                                         String defaultName,
                                         List<String> defaultLore,
                                         Map<String, String> placeholders) {
        Material material = resolveMaterial(section, defaultMaterial);
        String name = defaultName;
        if (section != null && section.contains("name")) {
            name = section.getString("name", defaultName);
        }
        List<String> lore = getConfiguredLore(section, "lore", defaultLore);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String appliedName = applyPlaceholders(name, placeholders);
            meta.setDisplayName(cm.translateColors(appliedName));
            if (lore != null && !lore.isEmpty()) {
                List<String> formatted = new ArrayList<>();
                for (String line : lore) {
                    formatted.add(cm.translateColors(applyPlaceholders(line, placeholders)));
                }
                meta.setLore(formatted);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    static Material resolveMaterial(ConfigurationSection section, Material defaultMaterial) {
        if (section != null && section.contains("material")) {
            String materialName = section.getString("material", "");
            if (materialName != null) {
                Material material = Material.matchMaterial(materialName.trim());
                if (material != null) {
                    return material;
                }
            }
        }
        return defaultMaterial;
    }

    static String applyPlaceholders(String text, Map<String, String> placeholders) {
        if (text == null || placeholders == null || placeholders.isEmpty()) {
            return text;
        }
        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
