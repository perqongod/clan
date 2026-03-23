package org.perq.clan;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles the War Team Selection GUI (6×9 double-chest style inventory).
 *
 * Layout (0-indexed rows 0–5, cols 0–8):
 *   Row 0: Pink glass header blocks
 *   Row 1: Player skulls
 *   Row 2: Arrow status items
 *   Rows 3–5: Green glass footer blocks
 *   Col 4 (all rows): Blue glass separator
 *
 *   Cols 0–3  = "Fighting" side
 *   Cols 5–8  = "Not fighting" side
 */
public class WarTeamSelectionListener implements Listener {

    private final Clan plugin;

    /** Stores open team-selection sessions keyed by leader UUID */
    private final Map<UUID, TeamSelectionSession> sessions = new HashMap<>();

    public WarTeamSelectionListener(Clan plugin) {
        this.plugin = plugin;
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Opens the team selection GUI for the given leader.
     * @param leader     The clan leader (who accepted/initiated the war)
     * @param clanTag    Their clan tag
     * @param members    All clan member UUIDs
     */
    public void openGui(Player leader, String clanTag, List<UUID> members) {
        Inventory inv = Bukkit.createInventory(null, 54, "Clan-Krieg: Team wählen");

        TeamSelectionSession session = new TeamSelectionSession(clanTag, members);
        sessions.put(leader.getUniqueId(), session);

        populateInventory(inv, session);
        leader.openInventory(inv);
    }

    /**
     * Returns the list of UUIDs that are in the "fighting" team for the given leader.
     */
    public List<UUID> getFightingTeam(UUID leaderUUID) {
        TeamSelectionSession session = sessions.get(leaderUUID);
        if (session == null) return new ArrayList<>();
        return new ArrayList<>(session.fighting);
    }

    public void removeSession(UUID leaderUUID) {
        sessions.remove(leaderUUID);
    }

    // ── Event handler ────────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        TeamSelectionSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (!event.getView().title().equals(Component.text("Clan-Krieg: Team wählen"))) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        int row = slot / 9;
        int col = slot % 9;

        // Only clicks in row 1 (player skulls) matter; col 4 = separator (ignore)
        if (row != 1 || col == 4) return;

        // Determine which member was clicked
        // Left side slots (cols 0–3) → fighting, right side (cols 5–8) → not fighting
        // We store member list; cols 0–3 maps to fighting[0–3], cols 5–8 maps to notFighting[0–3]
        UUID clickedMember = null;
        boolean wasFighting = false;

        if (col < 4 && col < session.fighting.size()) {
            clickedMember = session.fighting.get(col);
            wasFighting = true;
        } else if (col > 4) {
            int idx = col - 5;
            if (idx < session.notFighting.size()) {
                clickedMember = session.notFighting.get(idx);
                wasFighting = false;
            }
        }

        if (clickedMember == null) return;

        // Move to other side
        if (wasFighting) {
            session.fighting.remove(clickedMember);
            session.notFighting.add(0, clickedMember);
        } else {
            session.notFighting.remove(clickedMember);
            session.fighting.add(clickedMember);
        }

        // Refresh inventory
        Inventory inv = event.getView().getTopInventory();
        inv.clear();
        populateInventory(inv, session);
    }

    // ── Inventory population ─────────────────────────────────────────────────

    private void populateInventory(Inventory inv, TeamSelectionSession session) {
        ItemStack pink = namedItem(Material.PINK_STAINED_GLASS_PANE, " ");
        ItemStack blue = namedItem(Material.BLUE_STAINED_GLASS_PANE, " ");
        ItemStack green = namedItem(Material.GREEN_STAINED_GLASS_PANE, " ");

        // Row 0: header pink + blue separator in col 4
        for (int col = 0; col < 9; col++) {
            inv.setItem(col, col == 4 ? blue : pink);
        }

        // Row 2: arrows / status
        for (int col = 0; col < 9; col++) {
            if (col == 4) { inv.setItem(18 + col, blue); continue; }
            ItemStack arrow;
            if (col < 4) {
                arrow = namedItem(Material.ARROW, "§aKämpft");
            } else {
                arrow = namedItem(Material.ARROW, "§cKämpft nicht");
            }
            inv.setItem(18 + col, arrow);
        }

        // Rows 3–5: green footer + blue separator
        for (int row = 3; row <= 5; row++) {
            for (int col = 0; col < 9; col++) {
                inv.setItem(row * 9 + col, col == 4 ? blue : green);
            }
        }

        // Row 1: player skulls
        // Fighting side (cols 0–3)
        for (int i = 0; i < 4; i++) {
            if (i < session.fighting.size()) {
                inv.setItem(9 + i, playerSkull(session.fighting.get(i)));
            } else {
                inv.setItem(9 + i, namedItem(Material.GRAY_STAINED_GLASS_PANE, "§7Leer"));
            }
        }
        // Separator
        inv.setItem(9 + 4, blue);
        // Not-fighting side (cols 5–8)
        for (int i = 0; i < 4; i++) {
            if (i < session.notFighting.size()) {
                inv.setItem(9 + 5 + i, playerSkull(session.notFighting.get(i)));
            } else {
                inv.setItem(9 + 5 + i, namedItem(Material.GRAY_STAINED_GLASS_PANE, "§7Leer"));
            }
        }
    }

    private ItemStack namedItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack playerSkull(UUID uuid) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            meta.setOwningPlayer(op);
            String name = op.getName() != null ? op.getName() : uuid.toString().substring(0, 8);
            meta.displayName(Component.text("§f" + name));
            skull.setItemMeta(meta);
        }
        return skull;
    }


    // ── Session data class ───────────────────────────────────────────────────

    private static class TeamSelectionSession {
        final String clanTag;
        final List<UUID> fighting;
        final List<UUID> notFighting;

        TeamSelectionSession(String clanTag, List<UUID> members) {
            this.clanTag = clanTag;
            // Initially all members are on the not-fighting side
            this.fighting = new ArrayList<>();
            this.notFighting = new ArrayList<>(members);
        }
    }
}
