package org.perq.clan;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles the War Team Selection GUI (6×9 double-chest style inventory).
 */
public class WarTeamSelectionListener implements Listener {

    private static final Component TITLE = Component.text("Clan War: Select Team");

    private final Clan plugin;

    /** Stores open team-selection sessions keyed by leader UUID */
    private final Map<UUID, TeamSelectionSession> sessions = new HashMap<>();

    public WarTeamSelectionListener(Clan plugin) {
        this.plugin = plugin;
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Opens the team selection GUI for the given leader.
     */
    public void openGui(Player leader, WarManager.ActiveWar war, String clanTag) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        TeamSelectionSession session = new TeamSelectionSession(war, clanTag);
        sessions.put(leader.getUniqueId(), session);
        populateInventory(inv, session);
        leader.openInventory(inv);
    }

    public void refreshWar(WarManager.ActiveWar war) {
        for (Map.Entry<UUID, TeamSelectionSession> entry : sessions.entrySet()) {
            TeamSelectionSession session = entry.getValue();
            if (session.war != war) continue;
            Player leader = Bukkit.getPlayer(entry.getKey());
            if (leader == null) continue;
            if (!leader.getOpenInventory().title().equals(TITLE)) continue;
            Inventory inv = leader.getOpenInventory().getTopInventory();
            inv.clear();
            populateInventory(inv, session);
        }
    }

    public void closeSessionsForWar(WarManager.ActiveWar war) {
        Iterator<Map.Entry<UUID, TeamSelectionSession>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TeamSelectionSession> entry = iterator.next();
            if (entry.getValue().war != war) continue;
            Player leader = Bukkit.getPlayer(entry.getKey());
            if (leader != null && leader.getOpenInventory().title().equals(TITLE)) {
                leader.closeInventory();
            }
            iterator.remove();
        }
    }

    // ── Event handler ────────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        TeamSelectionSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (!event.getView().title().equals(TITLE)) return;

        event.setCancelled(true);

        if (!plugin.getWarManager().canSelectTeams(session.war)) {
            player.sendMessage(plugin.getConfigManager().getMessage("war-selection-locked"));
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        int row = slot / 9;
        int col = slot % 9;

        // Only clicks in row 1 (player skulls) matter; col 4 = separator (ignore)
        if (row != 1 || col == 4) return;

        session.refreshLists();

        UUID clickedMember = null;
        boolean wasFighting = false;

        if (col < 4) {
            int idx = col;
            if (idx < session.notFighting.size()) {
                clickedMember = session.notFighting.get(idx);
                wasFighting = false;
            }
        } else if (col > 4) {
            int idx = col - 5;
            if (idx < session.fighting.size()) {
                clickedMember = session.fighting.get(idx);
                wasFighting = true;
            }
        }

        if (clickedMember == null) return;

        if (wasFighting) {
            plugin.getWarManager().deselectFighter(session.war, session.clanTag, clickedMember);
        } else {
            plugin.getWarManager().selectFighter(session.war, session.clanTag, clickedMember);
        }
    }

    // ── Inventory population ─────────────────────────────────────────────────

    private void populateInventory(Inventory inv, TeamSelectionSession session) {
        session.refreshLists();
        ItemStack red = namedItem(Material.RED_STAINED_GLASS_PANE, Component.text(" "));
        ItemStack blue = namedItem(Material.BLUE_STAINED_GLASS_PANE, Component.text(" "));
        ItemStack green = namedItem(Material.GREEN_STAINED_GLASS_PANE, Component.text(" "));
        ItemStack gray = namedItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));

        // Row 0: header red/green + blue separator in col 4
        for (int col = 0; col < 9; col++) {
            if (col == 4) {
                inv.setItem(col, blue);
            } else if (col < 4) {
                inv.setItem(col, red);
            } else {
                inv.setItem(col, green);
            }
        }

        // Rows 2–4: gray filler + blue separator
        for (int row = 2; row <= 4; row++) {
            for (int col = 0; col < 9; col++) {
                inv.setItem(row * 9 + col, col == 4 ? blue : gray);
            }
        }

        // Row 1: player skulls
        for (int i = 0; i < 4; i++) {
            if (i < session.notFighting.size()) {
                inv.setItem(9 + i, playerSkull(session.notFighting.get(i), "Nicht kämpfend", NamedTextColor.GRAY));
            } else {
                inv.setItem(9 + i, gray);
            }
        }
        inv.setItem(9 + 4, blue);
        for (int i = 0; i < 4; i++) {
            if (i < session.fighting.size()) {
                UUID member = session.fighting.get(i);
                WarManager.InviteStatus status = session.war.getInviteStatus(member);
                boolean ready = session.war.isReady(member);
                StatusDisplay display = getStatusDisplay(status, ready);
                inv.setItem(9 + 5 + i, playerSkull(member, display.label, display.color));
            } else {
                inv.setItem(9 + 5 + i, gray);
            }
        }

        // Row 5: bottom bar with arrows + red/green blocks
        ItemStack leftArrow = namedItem(Material.ARROW, Component.text("Nicht kämpfend", NamedTextColor.RED));
        ItemStack rightArrow = namedItem(Material.ARROW, Component.text("Kämpfend", NamedTextColor.GREEN));
        for (int col = 0; col < 9; col++) {
            int slot = 45 + col;
            if (col == 0) {
                inv.setItem(slot, leftArrow);
            } else if (col == 8) {
                inv.setItem(slot, rightArrow);
            } else if (col == 4) {
                inv.setItem(slot, blue);
            } else if (col < 4) {
                inv.setItem(slot, red);
            } else {
                inv.setItem(slot, green);
            }
        }
    }

    private StatusDisplay getStatusDisplay(WarManager.InviteStatus status, boolean ready) {
        if (status == WarManager.InviteStatus.DECLINED) {
            return new StatusDisplay("Abgelehnt", NamedTextColor.RED);
        }
        if (ready) {
            return new StatusDisplay("Bereit", NamedTextColor.GREEN);
        }
        if (status == WarManager.InviteStatus.ACCEPTED) {
            return new StatusDisplay("Angenommen", NamedTextColor.DARK_GREEN);
        }
        return new StatusDisplay("Eingeladen", NamedTextColor.YELLOW);
    }

    private ItemStack namedItem(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack playerSkull(UUID uuid, String status, NamedTextColor color) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            meta.setOwningPlayer(op);
            String name = op.getName() != null ? op.getName() : uuid.toString().substring(0, 8);
            meta.displayName(Component.text(name, color));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(status, color));
            meta.lore(lore);
            skull.setItemMeta(meta);
        }
        return skull;
    }

    private static class StatusDisplay {
        private final String label;
        private final NamedTextColor color;

        private StatusDisplay(String label, NamedTextColor color) {
            this.label = label;
            this.color = color;
        }
    }

    // ── Session data class ───────────────────────────────────────────────────

    private static class TeamSelectionSession {
        final WarManager.ActiveWar war;
        final String clanTag;
        List<UUID> fighting = new ArrayList<>();
        List<UUID> notFighting = new ArrayList<>();

        TeamSelectionSession(WarManager.ActiveWar war, String clanTag) {
            this.war = war;
            this.clanTag = clanTag;
        }

        void refreshLists() {
            this.notFighting = war.getNotSelectedForClan(clanTag);
            this.fighting = war.getSelectedForClan(clanTag);
        }
    }
}
