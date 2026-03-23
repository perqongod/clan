package org.perq.clan;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ClanCommand implements CommandExecutor, TabCompleter {
    private final Clan plugin;
    private Map<String, Inventory> clanChests = new HashMap<>();
    private Map<UUID, Long> spawnCooldowns = new HashMap<>();
    private Map<UUID, Integer> spawnTaskIds = new HashMap<>();
    /** Stores UUIDs of players who have initiated /clan delete but not yet confirmed. */
    private Map<UUID, Long> pendingDeletes = new HashMap<>();
    /** Stores admin UUID → target player name for /clan force kick confirmations. */
    private Map<UUID, String> pendingForceKicks = new HashMap<>();
    /** Stores admin UUID → clan tag for /clan force delete confirmations. */
    private Map<UUID, String> pendingForceDeletes = new HashMap<>();
    /** Stores leader UUID → target clan tag for /clan request confirmations. */
    private Map<UUID, String> pendingLeaderRequests = new HashMap<>();
    /** Stores leader UUID → target player name for /clan leader transfer confirmations. */
    private Map<UUID, String> pendingLeaderTransfers = new HashMap<>();
    /** Invite cooldown: sender UUID → last invite timestamp */
    private Map<UUID, Long> inviteCooldowns = new HashMap<>();
    /** Request cooldown: sender UUID → last request timestamp */
    private Map<UUID, Long> requestCooldowns = new HashMap<>();
    /** Millis before a pending confirmation expires. */
    private static final long DELETE_CONFIRM_TIMEOUT_MS = 30_000L;

    public ClanCommand(Clan plugin) {
        this.plugin = plugin;
    }

    private static final Set<String> SUBCOMMANDS = new HashSet<>(Arrays.asList(
            "create", "delete", "disband", "invite", "accept", "deny", "leave", "kick",
            "promote", "demote", "rename", "info", "toggle", "stats", "ranking",
            "chest", "spawn", "setspawn", "force", "requests", "join", "request",
            "chat", "leader", "logs", "skills", "war", "admin"
    ));

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern verwendet werden.");
            return true;
        }

        Player player = (Player) sender;
        UUID playerUUID = player.getUniqueId();

        // Check for basic clan permission
        if (!player.hasPermission("clan.default")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        // When using /c or /clan with args that are not a known subcommand, treat as clan chat
        if ((label.equalsIgnoreCase("c") || label.equalsIgnoreCase("clan")) && args.length > 0 && !SUBCOMMANDS.contains(args[0].toLowerCase())) {
            ClanData chatClan = getPlayerClan(playerUUID);
            if (chatClan == null) {
                player.sendMessage(plugin.getConfigManager().getMessage("no-clan"));
                return true;
            }
            String chatMessage = String.join(" ", args);
            ConfigManager cm = plugin.getConfigManager();
            String format = cm.translateColors(
                    cm.getClanChatFormat()
                            .replace("%player%", player.getName())
                            .replace("%message%", chatMessage)
            );
            for (UUID mem : chatClan.getMembers()) {
                Player p = plugin.getServer().getPlayer(mem);
                if (p != null) {
                    p.sendMessage(format);
                }
            }
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(plugin.getConfigManager().translateColors(plugin.getConfigManager().getPrefix() + "/clan create <tag> &7- um deinen Clan zu erstellen"));
            player.sendMessage(plugin.getConfigManager().translateColors(plugin.getConfigManager().getPrefix() + "/clan delete &7- um deinen Clan zu löschen"));
            player.sendMessage(plugin.getConfigManager().translateColors(plugin.getConfigManager().getPrefix() + "/clan invite <spieler> &7- um einen Spieler einzuladen"));
            player.sendMessage(plugin.getConfigManager().translateColors(plugin.getConfigManager().getPrefix() + "/clan accept <tag> &7- um eine Einladung anzunehmen"));
            player.sendMessage(plugin.getConfigManager().translateColors(plugin.getConfigManager().getPrefix() + "/clan deny <tag> &7- um eine Einladung abzulehnen"));
            player.sendMessage(plugin.getConfigManager().translateColors(plugin.getConfigManager().getPrefix() + "/clan leave &7- um deinen Clan zu verlassen"));
            player.sendMessage(plugin.getConfigManager().translateColors(plugin.getConfigManager().getPrefix() + "/clan kick <spieler> &7- um einen Spieler zu kicken"));
            player.sendMessage(plugin.getConfigManager().translateColors(plugin.getConfigManager().getPrefix() + "/clan promote <spieler> &7- um einen Spieler zu befördern"));
            player.sendMessage(plugin.getConfigManager().translateColors(plugin.getConfigManager().getPrefix() + "/clan demote <spieler> &7- um einen Spieler zu degradieren"));
            player.sendMessage(plugin.getConfigManager().translateColors(plugin.getConfigManager().getPrefix() + "/clan rename <neuerTag> &7- um den Clan umzubenennen"));
            player.sendMessage(plugin.getConfigManager().translateColors(plugin.getConfigManager().getPrefix() + "/clan info &7- um Clan-Info zu sehen"));
            player.sendMessage(plugin.getConfigManager().translateColors(plugin.getConfigManager().getPrefix() + "/clan toggle &7- um Einladungen ein-/auszuschalten"));
            player.sendMessage(plugin.getConfigManager().translateColors(plugin.getConfigManager().getPrefix() + "/clan stats &7- um Clan-Stats zu sehen"));
            player.sendMessage(plugin.getConfigManager().translateColors(plugin.getConfigManager().getPrefix() + "/clan ranking &7- um das Clan-Ranking zu sehen"));
            player.sendMessage(plugin.getConfigManager().translateColors(plugin.getConfigManager().getPrefix() + "/clan chest &7- um die Clan-Kiste zu öffnen"));
            player.sendMessage(plugin.getConfigManager().translateColors(plugin.getConfigManager().getPrefix() + "/clan spawn &7- um zum Clan-Spawn zu teleportieren"));
            player.sendMessage(plugin.getConfigManager().translateColors(plugin.getConfigManager().getPrefix() + "/clan setspawn &7- um den Clan-Spawn zu setzen"));
            player.sendMessage(plugin.getConfigManager().translateColors(plugin.getConfigManager().getPrefix() + "/clan request <tag> &7- um eine Beitrittsanfrage zu senden"));
            player.sendMessage(plugin.getConfigManager().translateColors(plugin.getConfigManager().getPrefix() + "/clan requests &7- um deine Clan-Einladungen zu sehen"));
            if (player.hasPermission("clan.admin")) {
                player.sendMessage(plugin.getConfigManager().translateColors(plugin.getConfigManager().getPrefix() + "&7Tipp: &f/clan admin &7für Admin-Befehle"));
            }
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "create":
                if (args.length < 2) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "Verwendung: /clan create <tag>");
                    return true;
                }
                String tag = args[1];
                
                // Check if player has VIP permission for colored tags
                boolean isVip = player.hasPermission("clan.vip");
                
                // Validate tag
                TagValidator validator = new TagValidator(plugin);
                TagValidator.ValidationResult validationResult = validator.validate(tag, isVip);
                if (!validationResult.isValid()) {
                    player.sendMessage(validationResult.getErrorMessage());
                    return true;
                }
                
                if (plugin.getFileManager().loadClan(tag) != null) {
                    player.sendMessage(plugin.getConfigManager().getMessage("clan-exists"));
                    return true;
                }
                PlayerData pData = plugin.getFileManager().loadPlayer(playerUUID);
                if (pData == null) {
                    pData = new PlayerData(player.getName());
                }
                if (pData.getClanTag() != null) {
                    player.sendMessage(plugin.getConfigManager().getMessage("already-in-clan"));
                    return true;
                }
                ClanData clan = new ClanData(tag, playerUUID);
                try {
                    plugin.getFileManager().saveClan(clan);
                    pData.setClanTag(tag);
                    pData.setRole("LEADER");
                    plugin.getFileManager().savePlayer(playerUUID, pData);
                    player.sendMessage(plugin.getConfigManager().getMessage("clan-created").replace("%tag%", tag));
                } catch (IOException e) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "Fehler beim Speichern.");
                }
                break;

            case "delete":
                ClanData c = getPlayerClan(playerUUID);
                if (c == null) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no-clan"));
                    return true;
                }
                if (!c.getLeader().equals(playerUUID)) {
                    player.sendMessage(plugin.getConfigManager().getMessage("not-clan-leader"));
                    return true;
                }
                // Check for "confirm" sub-argument
                if (args.length >= 2 && args[1].equalsIgnoreCase("confirm")) {
                    Long pending = pendingDeletes.get(playerUUID);
                    if (pending == null || System.currentTimeMillis() - pending > DELETE_CONFIRM_TIMEOUT_MS) {
                        player.sendMessage(plugin.getConfigManager().getMessage("clan-deleted-cancelled"));
                        return true;
                    }
                    pendingDeletes.remove(playerUUID);
                    String dissolvedTag = c.getTag();
                    // Remove from all members
                    for (UUID mem : c.getMembers()) {
                        PlayerData pd = plugin.getFileManager().loadPlayer(mem);
                        if (pd != null) {
                            pd.setClanTag(null);
                            pd.setRole("MEMBER");
                            try {
                                plugin.getFileManager().savePlayer(mem, pd);
                            } catch (IOException e) {
                                // ignore
                            }
                        }
                    }
                    plugin.getFileManager().deleteClan(dissolvedTag);
                    player.sendMessage(plugin.getConfigManager().getMessage("clan-deleted"));
                    // Broadcast to all players
                    String broadcastMsg = plugin.getConfigManager().getMessage("clan-dissolved").replace("%tag%", dissolvedTag);
                    for (Player p : plugin.getServer().getOnlinePlayers()) {
                        p.sendMessage(broadcastMsg);
                    }
                } else if (args.length >= 2 && args[1].equalsIgnoreCase("deny")) {
                    pendingDeletes.remove(playerUUID);
                    player.sendMessage(plugin.getConfigManager().getMessage("delete-denied"));
                } else {
                    // First invocation – ask for confirmation with [accept] and [Deny] buttons
                    pendingDeletes.put(playerUUID, System.currentTimeMillis());
                    String confirmText = plugin.getConfigManager().getMessage("delete-confirm") + " ";
                    Component confirmMsg = Component.text(confirmText)
                            .append(Component.text("[accept]")
                                    .color(TextColor.color(0x55FF55))
                                    .clickEvent(ClickEvent.runCommand("/clan delete confirm")))
                            .append(Component.text(" / ").color(TextColor.color(0xAAAAAA)))
                            .append(Component.text("[Deny]")
                                    .color(TextColor.color(0xFF5555))
                                    .clickEvent(ClickEvent.runCommand("/clan delete deny")));
                    player.sendMessage(confirmMsg);
                }
                break;

            case "invite":
                if (args.length < 2) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "Verwendung: /clan invite <spieler>");
                    return true;
                }
                ClanData cl = getPlayerClan(playerUUID);
                if (cl == null) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no-clan"));
                    return true;
                }
                // Invite cooldown check
                {
                    long invCooldownMs = plugin.getConfigManager().getInviteCooldownSeconds() * 1000L;
                    Long lastInvite = inviteCooldowns.get(playerUUID);
                    if (lastInvite != null && System.currentTimeMillis() - lastInvite < invCooldownMs) {
                        player.sendMessage(plugin.getConfigManager().getMessage("invite-cooldown"));
                        return true;
                    }
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(plugin.getConfigManager().getMessage("player-not-found"));
                    return true;
                }
                PlayerData tData = plugin.getFileManager().loadPlayer(target.getUniqueId());
                if (tData == null) {
                    tData = new PlayerData(target.getName());
                }
                if (tData.getClanTag() != null) {
                    player.sendMessage(plugin.getConfigManager().getMessage("already-in-clan"));
                    return true;
                }
                if (!tData.isInvitesEnabled()) {
                    player.sendMessage(plugin.getConfigManager().getMessage("invitations-disabled"));
                    return true;
                }
                // Max members check
                if (cl.getMembers().size() >= plugin.getConfigManager().getMaxMembers()) {
                    player.sendMessage(plugin.getConfigManager().getMessage("clan-full"));
                    return true;
                }
                InviteData invite = new InviteData(cl.getTag());
                try {
                    plugin.getFileManager().saveInvite(target.getUniqueId(), invite);
                    inviteCooldowns.put(playerUUID, System.currentTimeMillis());
                    player.sendMessage(plugin.getConfigManager().getMessage("invitation-sent").replace("%player%", target.getName()));
                    target.sendMessage(plugin.getConfigManager().getMessage("invitation-received").replace("%tag%", cl.getTag()));
                } catch (IOException e) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "Fehler beim Speichern.");
                }
                break;

            case "accept":
                if (args.length < 2) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "Verwendung: /clan accept <tag>");
                    return true;
                }
                tag = args[1];
                List<InviteData> acceptInvites = plugin.getFileManager().loadAllInvites(playerUUID);
                InviteData acceptInv = null;
                for (InviteData i : acceptInvites) {
                    if (tag.equals(i.getFromClan())) { acceptInv = i; break; }
                }
                if (acceptInv == null) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "Keine Einladung gefunden.");
                    return true;
                }
                ClanData cln = plugin.getFileManager().loadClan(tag);
                if (cln == null) {
                    player.sendMessage(plugin.getConfigManager().getMessage("clan-not-found"));
                    return true;
                }
                PlayerData plData = plugin.getFileManager().loadPlayer(playerUUID);
                if (plData == null) {
                    plData = new PlayerData(player.getName());
                }
                if (plData.getClanTag() != null) {
                    player.sendMessage(plugin.getConfigManager().getMessage("already-in-clan"));
                    return true;
                }
                cln.getMembers().add(playerUUID);
                plData.setClanTag(tag);
                plData.setRole("MEMBER");
                try {
                    plugin.getFileManager().saveClan(cln);
                    plugin.getFileManager().savePlayer(playerUUID, plData);
                    plugin.getFileManager().deleteSpecificInvite(playerUUID, tag);
                    player.sendMessage(plugin.getConfigManager().getMessage("invitation-accepted").replace("%tag%", tag));
                } catch (IOException e) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "Fehler beim Speichern.");
                }
                break;

            case "deny":
                if (args.length < 2) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "Verwendung: /clan deny <tag>");
                    return true;
                }
                String denyTag = args[1];
                List<InviteData> denyInvites = plugin.getFileManager().loadAllInvites(playerUUID);
                boolean foundDeny = false;
                for (InviteData i : denyInvites) {
                    if (denyTag.equals(i.getFromClan())) { foundDeny = true; break; }
                }
                if (!foundDeny) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "Keine Einladung gefunden.");
                    return true;
                }
                try {
                    plugin.getFileManager().deleteSpecificInvite(playerUUID, denyTag);
                } catch (IOException e) {
                    // ignore
                }
                player.sendMessage(plugin.getConfigManager().getMessage("invitation-denied"));
                break;

            case "leave":
                ClanData cll = getPlayerClan(playerUUID);
                if (cll == null) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no-clan"));
                    return true;
                }
                if (cll.getLeader().equals(playerUUID)) {
                    // Transfer leadership or delete
                    if (cll.getMembers().size() > 1) {
                        // Transfer to first mod or member
                        UUID newLeader = null;
                        if (!cll.getModerators().isEmpty()) {
                            newLeader = cll.getModerators().get(0);
                            cll.getModerators().remove(0);
                        } else {
                            for (UUID mem : cll.getMembers()) {
                                if (!mem.equals(playerUUID)) {
                                    newLeader = mem;
                                    break;
                                }
                            }
                        }
                        if (newLeader != null) {
                            cll.setLeader(newLeader);
                            PlayerData nlData = plugin.getFileManager().loadPlayer(newLeader);
                            if (nlData != null) {
                                nlData.setRole("LEADER");
                                try {
                                    plugin.getFileManager().savePlayer(newLeader, nlData);
                                } catch (IOException e) {}
                            }
                        }
                    } else {
                        plugin.getFileManager().deleteClan(cll.getTag());
                    }
                }
                cll.getMembers().remove(playerUUID);
                if (cll.getModerators().contains(playerUUID)) {
                    cll.getModerators().remove(playerUUID);
                }
                PlayerData pllData = plugin.getFileManager().loadPlayer(playerUUID);
                if (pllData != null) {
                    pllData.setClanTag(null);
                    pllData.setRole("MEMBER");
                    try {
                        plugin.getFileManager().savePlayer(playerUUID, pllData);
                        plugin.getFileManager().saveClan(cll);
                    } catch (IOException e) {}
                }
                player.sendMessage(plugin.getConfigManager().getMessage("left-clan"));
                break;

            case "kick":
                if (args.length < 2) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "Verwendung: /clan kick <spieler>");
                    return true;
                }
                ClanData clk = getPlayerClan(playerUUID);
                if (clk == null) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no-clan"));
                    return true;
                }
                PlayerData pkData = plugin.getFileManager().loadPlayer(playerUUID);
                if (pkData == null || (!"LEADER".equals(pkData.getRole()) && !"MOD".equals(pkData.getRole()))) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(plugin.getConfigManager().getMessage("player-not-found"));
                    return true;
                }
                if (target.getUniqueId().equals(playerUUID)) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "Du kannst dich nicht selbst kicken.");
                    return true;
                }
                clk.getMembers().remove(target.getUniqueId());
                if (clk.getModerators().contains(target.getUniqueId())) {
                    clk.getModerators().remove(target.getUniqueId());
                }
                PlayerData tkData = plugin.getFileManager().loadPlayer(target.getUniqueId());
                if (tkData != null) {
                    tkData.setClanTag(null);
                    tkData.setRole("MEMBER");
                    try {
                        plugin.getFileManager().savePlayer(target.getUniqueId(), tkData);
                        plugin.getFileManager().saveClan(clk);
                    } catch (IOException e) {}
                }
                player.sendMessage(plugin.getConfigManager().getMessage("kicked").replace("%player%", target.getName()));
                target.sendMessage(plugin.getConfigManager().getMessage("kicked"));
                break;

            case "promote":
                if (args.length < 2) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "Verwendung: /clan promote <spieler>");
                    return true;
                }
                ClanData clp = getPlayerClan(playerUUID);
                if (clp == null) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no-clan"));
                    return true;
                }
                if (!clp.getLeader().equals(playerUUID)) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(plugin.getConfigManager().getMessage("player-not-found"));
                    return true;
                }
                PlayerData tpData = plugin.getFileManager().loadPlayer(target.getUniqueId());
                if (tpData == null || !clp.getMembers().contains(target.getUniqueId())) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "Spieler ist nicht im Clan.");
                    return true;
                }
                String currentRole = tpData.getRole();
                if ("MEMBER".equals(currentRole)) {
                    tpData.setRole("MOD");
                    clp.getModerators().add(target.getUniqueId());
                } else if ("MOD".equals(currentRole)) {
                    tpData.setRole("LEADER");
                    clp.setLeader(target.getUniqueId());
                    clp.getModerators().remove(target.getUniqueId());
                }
                try {
                    plugin.getFileManager().savePlayer(target.getUniqueId(), tpData);
                    plugin.getFileManager().saveClan(clp);
                    player.sendMessage(plugin.getConfigManager().getMessage("promoted").replace("%player%", target.getName()));
                } catch (IOException e) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "Fehler beim Speichern.");
                }
                break;

            case "demote":
                if (args.length < 2) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "Verwendung: /clan demote <spieler>");
                    return true;
                }
                ClanData cld = getPlayerClan(playerUUID);
                if (cld == null) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no-clan"));
                    return true;
                }
                if (!cld.getLeader().equals(playerUUID)) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(plugin.getConfigManager().getMessage("player-not-found"));
                    return true;
                }
                PlayerData tdData = plugin.getFileManager().loadPlayer(target.getUniqueId());
                if (tdData == null || !cld.getMembers().contains(target.getUniqueId())) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "Spieler ist nicht im Clan.");
                    return true;
                }
                if ("MOD".equals(tdData.getRole())) {
                    tdData.setRole("MEMBER");
                    cld.getModerators().remove(target.getUniqueId());
                    try {
                        plugin.getFileManager().savePlayer(target.getUniqueId(), tdData);
                        plugin.getFileManager().saveClan(cld);
                        player.sendMessage(plugin.getConfigManager().getMessage("demoted").replace("%player%", target.getName()));
                    } catch (IOException e) {
                        player.sendMessage(plugin.getConfigManager().getPrefix() + "Fehler beim Speichern.");
                    }
                } else {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "Spieler kann nicht degradiert werden.");
                }
                break;

            case "rename":
                if (args.length < 2) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "Verwendung: /clan rename <neuerTag>");
                    return true;
                }
                ClanData clr = getPlayerClan(playerUUID);
                if (clr == null) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no-clan"));
                    return true;
                }
                if (!clr.getLeader().equals(playerUUID)) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                String newTag = args[1];
                // Validate the new tag
                boolean isVipRename = player.hasPermission("clan.vip");
                TagValidator renameValidator = new TagValidator(plugin);
                TagValidator.ValidationResult renameResult = renameValidator.validate(newTag, isVipRename);
                if (!renameResult.isValid()) {
                    player.sendMessage(renameResult.getErrorMessage());
                    return true;
                }
                if (plugin.getFileManager().loadClan(newTag) != null) {
                    player.sendMessage(plugin.getConfigManager().getMessage("clan-exists"));
                    return true;
                }
                // Update all members
                for (UUID mem : clr.getMembers()) {
                    PlayerData md = plugin.getFileManager().loadPlayer(mem);
                    if (md != null) {
                        md.setClanTag(newTag);
                        try {
                            plugin.getFileManager().savePlayer(mem, md);
                        } catch (IOException e) {}
                    }
                }
                plugin.getFileManager().deleteClan(clr.getTag());
                clr.setTag(newTag);
                try {
                    plugin.getFileManager().saveClan(clr);
                    player.sendMessage(plugin.getConfigManager().getMessage("renamed").replace("%tag%", newTag));
                } catch (IOException e) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "Fehler beim Speichern.");
                }
                break;

            case "info":
                ClanData cli = getPlayerClan(playerUUID);
                if (cli == null) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no-clan"));
                    return true;
                }
                StringBuilder sb = new StringBuilder();
                sb.append(plugin.getConfigManager().getMessage("clan-info-header")).append("\n");
                sb.append(plugin.getConfigManager().getPrefix()).append("Tag: ").append(cli.getTag()).append("\n");
                sb.append(plugin.getConfigManager().getPrefix()).append("Punkte: ").append(cli.getPoints()).append("\n");
                sb.append(plugin.getConfigManager().getPrefix()).append("Rang: ").append(cli.getRank()).append("\n");
                sb.append(plugin.getConfigManager().getPrefix()).append("Erstelldatum: ").append(cli.getCreated()).append("\n");
                sb.append(plugin.getConfigManager().getPrefix()).append("Online-Zeit: ").append(String.format("%.1f", cli.getOnlineTime())).append("h\n");
                sb.append(plugin.getConfigManager().getPrefix()).append("Leader: ").append(Bukkit.getOfflinePlayer(cli.getLeader()).getName()).append("\n");
                String mods = cli.getModerators().isEmpty() ? "Keine" : cli.getModerators().stream().map(uuid -> Bukkit.getOfflinePlayer(uuid).getName()).reduce((a,b) -> a + ", " + b).orElse("Keine");
                sb.append(plugin.getConfigManager().getPrefix()).append("Moderatoren: ").append(mods).append("\n");
                sb.append(plugin.getConfigManager().getPrefix()).append("Mitglieder:\n");
                for (UUID mem : cli.getMembers()) {
                    String name = Bukkit.getOfflinePlayer(mem).getName();
                    PlayerData md = plugin.getFileManager().loadPlayer(mem);
                    double time = md != null ? md.getOnlineTime() : 0.0;
                    String online = plugin.getServer().getPlayer(mem) != null ? "online" : "offline";
                    sb.append("  - ").append(name).append(" (").append(md != null ? md.getRole() : "MEMBER").append(") ").append(online).append(" (").append(String.format("%.1f", time)).append("h)\n");
                }
                player.sendMessage(plugin.getConfigManager().translateColors(sb.toString()));
                break;

            case "toggle":
                boolean toggled = plugin.toggleInvitation(playerUUID);
                if (toggled) {
                    player.sendMessage(plugin.getConfigManager().getMessage("toggle-off"));
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessage("toggle-on"));
                }
                PlayerData ptData = plugin.getFileManager().loadPlayer(playerUUID);
                if (ptData != null) {
                    ptData.setInvitesEnabled(!toggled);
                    try {
                        plugin.getFileManager().savePlayer(playerUUID, ptData);
                    } catch (IOException e) {}
                }
                break;

            case "stats":
                ClanData cls = getPlayerClan(playerUUID);
                if (cls == null) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no-clan"));
                    return true;
                }
                player.sendMessage(plugin.getConfigManager().getPrefix() + "Clan: " + cls.getTag() + " | Punkte: " + cls.getPoints() + " | Rang: " + cls.getRank() + " | Mitglieder: " + cls.getMembers().size());
                break;

            case "ranking":
                Map<String, ClanData> allClans = plugin.getFileManager().loadAllClans();
                List<Map.Entry<String, ClanData>> sorted = new ArrayList<>(allClans.entrySet());
                sorted.sort((a, b) -> Integer.compare(b.getValue().getPoints(), a.getValue().getPoints()));
                player.sendMessage(plugin.getConfigManager().getPrefix() + "Clan-Ranking:");
                int rank = 1;
                for (Map.Entry<String, ClanData> entry : sorted) {
                    player.sendMessage(rank + ". " + entry.getKey() + " - " + entry.getValue().getPoints() + " Punkte [" + entry.getValue().getRank() + "]");
                    rank++;
                    if (rank > 10) break;
                }
                break;

            case "chest":
                ClanData clc = getPlayerClan(playerUUID);
                if (clc == null) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no-clan"));
                    return true;
                }
                Inventory chest = clanChests.get(clc.getTag());
                if (chest == null) {
                    chest = Bukkit.createInventory(null, 27, "Clan-Kiste: " + clc.getTag());
                    clanChests.put(clc.getTag(), chest);
                }
                player.openInventory(chest);
                break;

            case "spawn":
                ClanData cls2 = getPlayerClan(playerUUID);
                if (cls2 == null) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no-clan"));
                    return true;
                }
                if (cls2.getSpawn() == null) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "Clan-Spawn nicht gesetzt.");
                    return true;
                }
                long now = System.currentTimeMillis();
                if (spawnCooldowns.containsKey(playerUUID) && now < spawnCooldowns.get(playerUUID)) {
                    long remaining = (spawnCooldowns.get(playerUUID) - now) / 1000 + 1;
                    player.sendActionBar(plugin.getConfigManager().translateColors("&#FF0000Clan-Spawn Cooldown: " + remaining + "s"));
                    return true;
                }
                player.teleport(cls2.getSpawn());
                player.sendMessage(plugin.getConfigManager().getPrefix() + "Teleportiert zum Clan-Spawn.");
                spawnCooldowns.put(playerUUID, now + 3000);
                int taskId = plugin.getServer().getScheduler().runTaskTimer(plugin, new BukkitRunnable() {
                    @Override
                    public void run() {
                        long current = System.currentTimeMillis();
                        if (!spawnCooldowns.containsKey(playerUUID) || current >= spawnCooldowns.get(playerUUID)) {
                            spawnCooldowns.remove(playerUUID);
                            spawnTaskIds.remove(playerUUID);
                            cancel();
                            return;
                        }
                        long rem = (spawnCooldowns.get(playerUUID) - current) / 1000 + 1;
                        player.sendActionBar(plugin.getConfigManager().translateColors("&#FF0000Clan-Spawn Cooldown: " + rem + "s"));
                    }
                }, 0L, 20L).getTaskId();
                spawnTaskIds.put(playerUUID, taskId);
                break;

            case "setspawn":
                ClanData cls3 = getPlayerClan(playerUUID);
                if (cls3 == null) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no-clan"));
                    return true;
                }
                if (!cls3.getLeader().equals(playerUUID)) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                cls3.setSpawn(player.getLocation());
                try {
                    plugin.getFileManager().saveClan(cls3);
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "Clan-Spawn gesetzt.");
                } catch (IOException e) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "Fehler beim Speichern.");
                }
                break;

            case "force":
                if (!player.hasPermission("clan.admin")) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "Verwendung: /clan force kick <spieler> | /clan force delete <tag>");
                    return true;
                }
                if (args[1].equalsIgnoreCase("kick") || args[1].equalsIgnoreCase("leave")) {
                    if (args.length < 3) {
                        player.sendMessage(plugin.getConfigManager().getPrefix() + "Verwendung: /clan force kick <spieler>");
                        return true;
                    }
                    String forceKickTargetName = args[2];
                    if (forceKickTargetName.equalsIgnoreCase("confirm")) {
                        // Execute pending force kick
                        String pendingTarget = pendingForceKicks.remove(playerUUID);
                        if (pendingTarget == null) {
                            player.sendMessage(plugin.getConfigManager().getMessage("clan-deleted-cancelled"));
                            return true;
                        }
                        Player forceTarget = plugin.getServer().getPlayer(pendingTarget);
                        if (forceTarget == null) {
                            player.sendMessage(plugin.getConfigManager().getMessage("player-not-found"));
                            return true;
                        }
                        ClanData forceTargetClan = getPlayerClan(forceTarget.getUniqueId());
                        if (forceTargetClan == null) {
                            player.sendMessage(plugin.getConfigManager().getMessage("force-leave-no-clan"));
                            return true;
                        }
                        forceTargetClan.getMembers().remove(forceTarget.getUniqueId());
                        if (forceTargetClan.getModerators().contains(forceTarget.getUniqueId())) {
                            forceTargetClan.getModerators().remove(forceTarget.getUniqueId());
                        }
                        PlayerData forceTargetData = plugin.getFileManager().loadPlayer(forceTarget.getUniqueId());
                        if (forceTargetData != null) {
                            forceTargetData.setClanTag(null);
                            forceTargetData.setRole("MEMBER");
                            try {
                                plugin.getFileManager().savePlayer(forceTarget.getUniqueId(), forceTargetData);
                                plugin.getFileManager().saveClan(forceTargetClan);
                            } catch (IOException e) { /* ignore */ }
                        }
                        player.sendMessage(plugin.getConfigManager().getMessage("force-leave-success").replace("%player%", forceTarget.getName()));
                        forceTarget.sendMessage(plugin.getConfigManager().getMessage("left-clan"));
                    } else if (forceKickTargetName.equalsIgnoreCase("deny")) {
                        pendingForceKicks.remove(playerUUID);
                        player.sendMessage(plugin.getConfigManager().getMessage("delete-denied"));
                    } else {
                        Player forceTarget = plugin.getServer().getPlayer(forceKickTargetName);
                        if (forceTarget == null) {
                            player.sendMessage(plugin.getConfigManager().getMessage("player-not-found"));
                            return true;
                        }
                        ClanData forceTargetClan = getPlayerClan(forceTarget.getUniqueId());
                        if (forceTargetClan == null) {
                            player.sendMessage(plugin.getConfigManager().getMessage("force-leave-no-clan"));
                            return true;
                        }
                        // Store pending and show confirmation
                        pendingForceKicks.put(playerUUID, forceKickTargetName);
                        String kickConfirmText = plugin.getConfigManager().getMessage("force-kick-confirm")
                                .replace("%player%", forceTarget.getName()) + " ";
                        Component kickConfirmMsg = Component.text(kickConfirmText)
                                .append(Component.text("[accept]")
                                        .color(TextColor.color(0x55FF55))
                                        .clickEvent(ClickEvent.runCommand("/clan force kick confirm")))
                                .append(Component.text(" / ").color(TextColor.color(0xAAAAAA)))
                                .append(Component.text("[Deny]")
                                        .color(TextColor.color(0xFF5555))
                                        .clickEvent(ClickEvent.runCommand("/clan force kick deny")));
                        player.sendMessage(kickConfirmMsg);
                    }
                } else if (args[1].equalsIgnoreCase("delete")) {
                    if (args.length < 3) {
                        player.sendMessage(plugin.getConfigManager().getPrefix() + "Verwendung: /clan force delete <tag>");
                        return true;
                    }
                    String forceDeleteArg = args[2];
                    if (forceDeleteArg.equalsIgnoreCase("confirm")) {
                        // Execute pending force delete
                        String pendingTag = pendingForceDeletes.remove(playerUUID);
                        if (pendingTag == null) {
                            player.sendMessage(plugin.getConfigManager().getMessage("clan-deleted-cancelled"));
                            return true;
                        }
                        ClanData forceDeleteClan = plugin.getFileManager().loadClan(pendingTag);
                        if (forceDeleteClan == null) {
                            player.sendMessage(plugin.getConfigManager().getMessage("clan-not-found"));
                            return true;
                        }
                        for (UUID mem : forceDeleteClan.getMembers()) {
                            PlayerData pd = plugin.getFileManager().loadPlayer(mem);
                            if (pd != null) {
                                pd.setClanTag(null);
                                pd.setRole("MEMBER");
                                try {
                                    plugin.getFileManager().savePlayer(mem, pd);
                                } catch (IOException e) { /* ignore */ }
                            }
                        }
                        plugin.getFileManager().deleteClan(pendingTag);
                        player.sendMessage(plugin.getConfigManager().getMessage("clan-deleted"));
                        String broadcastMsg = plugin.getConfigManager().getMessage("clan-dissolved").replace("%tag%", pendingTag);
                        for (Player p : plugin.getServer().getOnlinePlayers()) {
                            p.sendMessage(broadcastMsg);
                        }
                    } else if (forceDeleteArg.equalsIgnoreCase("deny")) {
                        pendingForceDeletes.remove(playerUUID);
                        player.sendMessage(plugin.getConfigManager().getMessage("delete-denied"));
                    } else {
                        String forceDeleteTag = forceDeleteArg;
                        ClanData forceDeleteClan = plugin.getFileManager().loadClan(forceDeleteTag);
                        if (forceDeleteClan == null) {
                            player.sendMessage(plugin.getConfigManager().getMessage("clan-not-found"));
                            return true;
                        }
                        // Store pending and show confirmation
                        pendingForceDeletes.put(playerUUID, forceDeleteTag);
                        String delConfirmText = plugin.getConfigManager().getMessage("force-delete-confirm")
                                .replace("%tag%", forceDeleteTag) + " ";
                        Component delConfirmMsg = Component.text(delConfirmText)
                                .append(Component.text("[accept]")
                                        .color(TextColor.color(0x55FF55))
                                        .clickEvent(ClickEvent.runCommand("/clan force delete confirm")))
                                .append(Component.text(" / ").color(TextColor.color(0xAAAAAA)))
                                .append(Component.text("[Deny]")
                                        .color(TextColor.color(0xFF5555))
                                        .clickEvent(ClickEvent.runCommand("/clan force delete deny")));
                        player.sendMessage(delConfirmMsg);
                    }
                } else {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "Verwendung: /clan force kick <spieler> | /clan force delete <tag>");
                }
                break;

            case "requests":
                // Only clan leaders can view join requests
                {
                    ClanData reqClanCheck = getPlayerClan(playerUUID);
                    if (reqClanCheck == null) {
                        player.sendMessage(plugin.getConfigManager().getMessage("no-clan"));
                        return true;
                    }
                    if (!reqClanCheck.getLeader().equals(playerUUID)) {
                        player.sendMessage(plugin.getConfigManager().getMessage("request-no-permission"));
                        return true;
                    }
                    // Handle accept/deny sub-actions: /clan requests accept <uuid> | /clan requests deny <uuid>
                    if (args.length >= 3) {
                        String action = args[1].toLowerCase();
                        String requesterUuidStr = args[2];
                        UUID requesterUUID;
                        try {
                            requesterUUID = UUID.fromString(requesterUuidStr);
                        } catch (IllegalArgumentException e) {
                            player.sendMessage(plugin.getConfigManager().getPrefix() + "Ungültige UUID.");
                            return true;
                        }
                        boolean wasPresent = reqClanCheck.getPendingRequesters().remove(requesterUuidStr);
                        if (!wasPresent) {
                            player.sendMessage(plugin.getConfigManager().getPrefix() + "Anfrage nicht gefunden.");
                            return true;
                        }
                        if (action.equals("accept")) {
                            if (reqClanCheck.getMembers().size() >= plugin.getConfigManager().getMaxMembers()) {
                                player.sendMessage(plugin.getConfigManager().getMessage("clan-full"));
                                reqClanCheck.getPendingRequesters().add(requesterUuidStr);
                                return true;
                            }
                            reqClanCheck.getMembers().add(requesterUUID);
                            PlayerData reqPD = plugin.getFileManager().loadPlayer(requesterUUID);
                            if (reqPD == null) reqPD = new PlayerData(Bukkit.getOfflinePlayer(requesterUUID).getName());
                            reqPD.setClanTag(reqClanCheck.getTag());
                            reqPD.setRole("MEMBER");
                            try {
                                plugin.getFileManager().savePlayer(requesterUUID, reqPD);
                                plugin.getFileManager().saveClan(reqClanCheck);
                                plugin.getFileManager().addLog(reqClanCheck.getTag(),
                                        new ClanLog(ClanLog.Type.JOIN, Bukkit.getOfflinePlayer(requesterUUID).getName() + " ist dem Clan beigetreten (Request)"));
                            } catch (IOException e) {
                                player.sendMessage(plugin.getConfigManager().getPrefix() + "Fehler beim Speichern.");
                            }
                            player.sendMessage(plugin.getConfigManager().getMessage("invitation-accepted").replace("%tag%", Bukkit.getOfflinePlayer(requesterUUID).getName()));
                            Player reqOnline = plugin.getServer().getPlayer(requesterUUID);
                            if (reqOnline != null) {
                                reqOnline.sendMessage(plugin.getConfigManager().getMessage("request-accepted-notify"));
                            }
                        } else if (action.equals("deny")) {
                            try {
                                plugin.getFileManager().saveClan(reqClanCheck);
                            } catch (IOException e) { /* ignore */ }
                            player.sendMessage(plugin.getConfigManager().getPrefix() + "Anfrage abgelehnt.");
                            Player reqOnline = plugin.getServer().getPlayer(requesterUUID);
                            if (reqOnline != null) {
                                reqOnline.sendMessage(plugin.getConfigManager().getMessage("request-denied-notify"));
                            }
                        }
                        return true;
                    }
                    // List pending requesters
                    List<String> pendingReqs = reqClanCheck.getPendingRequesters();
                    if (pendingReqs.isEmpty()) {
                        player.sendMessage(plugin.getConfigManager().getMessage("no-requests"));
                        return true;
                    }
                    player.sendMessage(plugin.getConfigManager().getMessage("requests-header"));
                    for (String uuidStr : pendingReqs) {
                        String rName;
                        try {
                            rName = Bukkit.getOfflinePlayer(UUID.fromString(uuidStr)).getName();
                            if (rName == null) rName = uuidStr;
                        } catch (IllegalArgumentException e) {
                            rName = uuidStr;
                        }
                        Component reqLine = Component.text("  " + rName + " ")
                                .append(Component.text("[Accept]")
                                        .color(TextColor.color(0x55FF55))
                                        .clickEvent(ClickEvent.runCommand("/clan requests accept " + uuidStr)))
                                .append(Component.text(" / ").color(TextColor.color(0xAAAAAA)))
                                .append(Component.text("[Deny]")
                                        .color(TextColor.color(0xFF5555))
                                        .clickEvent(ClickEvent.runCommand("/clan requests deny " + uuidStr)));
                        player.sendMessage(reqLine);
                    }
                }
                break;

            case "join":
                if (args.length < 2) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "Verwendung: /clan join <tag>");
                    return true;
                }
                String joinTag = args[1];
                List<InviteData> joinInvites = plugin.getFileManager().loadAllInvites(playerUUID);
                InviteData joinInv = null;
                for (InviteData i : joinInvites) {
                    if (joinTag.equals(i.getFromClan())) { joinInv = i; break; }
                }
                if (joinInv == null) {
                    player.sendMessage(plugin.getConfigManager().getMessage("join-not-invited"));
                    return true;
                }
                ClanData joinClan = plugin.getFileManager().loadClan(joinTag);
                if (joinClan == null) {
                    player.sendMessage(plugin.getConfigManager().getMessage("clan-not-found"));
                    return true;
                }
                PlayerData joinPlayerData = plugin.getFileManager().loadPlayer(playerUUID);
                if (joinPlayerData == null) {
                    joinPlayerData = new PlayerData(player.getName());
                }
                // Auto-leave current clan if player is NOT LEADER
                if (joinPlayerData.getClanTag() != null) {
                    if ("LEADER".equals(joinPlayerData.getRole())) {
                        player.sendMessage(plugin.getConfigManager().getMessage("leader-cannot-join"));
                        return true;
                    }
                    // Leave current clan automatically
                    ClanData currentClan = plugin.getFileManager().loadClan(joinPlayerData.getClanTag());
                    if (currentClan != null) {
                        currentClan.getMembers().remove(playerUUID);
                        if (currentClan.getModerators().contains(playerUUID)) {
                            currentClan.getModerators().remove(playerUUID);
                        }
                        try {
                            plugin.getFileManager().saveClan(currentClan);
                        } catch (IOException e) { /* ignore */ }
                    }
                }
                joinClan.getMembers().add(playerUUID);
                joinPlayerData.setClanTag(joinTag);
                joinPlayerData.setRole("MEMBER");
                try {
                    plugin.getFileManager().saveClan(joinClan);
                    plugin.getFileManager().savePlayer(playerUUID, joinPlayerData);
                    plugin.getFileManager().deleteSpecificInvite(playerUUID, joinTag);
                    player.sendMessage(plugin.getConfigManager().getMessage("invitation-accepted").replace("%tag%", joinTag));
                } catch (IOException e) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "Fehler beim Speichern.");
                }
                break;

            case "request":
                if (args.length < 2) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "Verwendung: /clan request <tag>");
                    return true;
                }
                String reqTag = args[1];
                // Handle pending leader confirmation
                if (reqTag.equalsIgnoreCase("confirm")) {
                    String pendingReqTag = pendingLeaderRequests.remove(playerUUID);
                    if (pendingReqTag == null) {
                        player.sendMessage(plugin.getConfigManager().getMessage("clan-deleted-cancelled"));
                        return true;
                    }
                    ClanData leaderOldClan = getPlayerClan(playerUUID);
                    if (leaderOldClan != null) {
                        String oldTag = leaderOldClan.getTag();
                        for (UUID mem : leaderOldClan.getMembers()) {
                            PlayerData pd = plugin.getFileManager().loadPlayer(mem);
                            if (pd != null) {
                                pd.setClanTag(null);
                                pd.setRole("MEMBER");
                                try {
                                    plugin.getFileManager().savePlayer(mem, pd);
                                } catch (IOException e) { /* ignore */ }
                            }
                        }
                        plugin.getFileManager().deleteClan(oldTag);
                        String dissolveMsg = plugin.getConfigManager().getMessage("clan-dissolved").replace("%tag%", oldTag);
                        for (Player p : plugin.getServer().getOnlinePlayers()) {
                            p.sendMessage(dissolveMsg);
                        }
                    }
                    // Send request to target clan (store in pendingRequesters)
                    ClanData targetReqClan = plugin.getFileManager().loadClan(pendingReqTag);
                    if (targetReqClan == null) {
                        player.sendMessage(plugin.getConfigManager().getMessage("clan-not-found"));
                        return true;
                    }
                    if (!targetReqClan.getPendingRequesters().contains(playerUUID.toString())) {
                        targetReqClan.getPendingRequesters().add(playerUUID.toString());
                        try {
                            plugin.getFileManager().saveClan(targetReqClan);
                        } catch (IOException e) {
                            player.sendMessage(plugin.getConfigManager().getPrefix() + "Fehler beim Speichern.");
                        }
                    }
                    player.sendMessage(plugin.getConfigManager().getMessage("request-sent").replace("%tag%", pendingReqTag));
                    Player leaderTargetOnline = plugin.getServer().getPlayer(targetReqClan.getLeader());
                    if (leaderTargetOnline != null) {
                        Component leaderNotif = Component.text(plugin.getConfigManager().translateColors(
                                plugin.getConfigManager().getMessage("request-leader-notification")) + " ")
                                .append(Component.text("[anzeigen]")
                                        .color(TextColor.color(0x55FF55))
                                        .clickEvent(ClickEvent.runCommand("/clan requests")));
                        leaderTargetOnline.sendMessage(leaderNotif);
                    }
                    return true;
                }
                if (reqTag.equalsIgnoreCase("deny")) {
                    pendingLeaderRequests.remove(playerUUID);
                    player.sendMessage(plugin.getConfigManager().getMessage("delete-denied"));
                    return true;
                }
                // Request cooldown check
                {
                    long reqCooldownMs = plugin.getConfigManager().getRequestCooldownSeconds() * 1000L;
                    Long lastReq = requestCooldowns.get(playerUUID);
                    if (lastReq != null && System.currentTimeMillis() - lastReq < reqCooldownMs) {
                        player.sendMessage(plugin.getConfigManager().getMessage("request-cooldown"));
                        return true;
                    }
                }
                ClanData reqClan = plugin.getFileManager().loadClan(reqTag);
                if (reqClan == null) {
                    player.sendMessage(plugin.getConfigManager().getMessage("clan-not-found"));
                    return true;
                }
                PlayerData reqPlayerData = plugin.getFileManager().loadPlayer(playerUUID);
                if (reqPlayerData == null) {
                    reqPlayerData = new PlayerData(player.getName());
                }
                // If player is a leader, ask for confirmation before dissolving
                if (reqPlayerData.getClanTag() != null && "LEADER".equals(reqPlayerData.getRole())) {
                    pendingLeaderRequests.put(playerUUID, reqTag);
                    String leaderConfirmText = plugin.getConfigManager().getMessage("leader-request-confirm") + " ";
                    Component leaderConfirmMsg = Component.text(leaderConfirmText)
                            .append(Component.text("[accept]")
                                    .color(TextColor.color(0x55FF55))
                                    .clickEvent(ClickEvent.runCommand("/clan request confirm")))
                            .append(Component.text(" / ").color(TextColor.color(0xAAAAAA)))
                            .append(Component.text("[Deny]")
                                    .color(TextColor.color(0xFF5555))
                                    .clickEvent(ClickEvent.runCommand("/clan request deny")));
                    player.sendMessage(leaderConfirmMsg);
                    return true;
                }
                if (reqPlayerData.getClanTag() != null) {
                    player.sendMessage(plugin.getConfigManager().getMessage("already-in-clan"));
                    return true;
                }
                // Check if already requested
                if (reqClan.getPendingRequesters().contains(playerUUID.toString())) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "Du hast bereits eine Anfrage an diesen Clan gesendet.");
                    return true;
                }
                reqClan.getPendingRequesters().add(playerUUID.toString());
                try {
                    plugin.getFileManager().saveClan(reqClan);
                    requestCooldowns.put(playerUUID, System.currentTimeMillis());
                    player.sendMessage(plugin.getConfigManager().getMessage("request-sent").replace("%tag%", reqTag));
                    Player leaderOnline = plugin.getServer().getPlayer(reqClan.getLeader());
                    if (leaderOnline != null) {
                        Component leaderNotif = Component.text(plugin.getConfigManager().translateColors(
                                plugin.getConfigManager().getMessage("request-leader-notification")) + " ")
                                .append(Component.text("[anzeigen]")
                                        .color(TextColor.color(0x55FF55))
                                        .clickEvent(ClickEvent.runCommand("/clan requests")));
                        leaderOnline.sendMessage(leaderNotif);
                    }
                } catch (IOException e) {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "Fehler beim Speichern.");
                }
                break;

            case "admin":
                if (!player.hasPermission("clan.admin")) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
                    return true;
                }
                player.sendMessage(plugin.getConfigManager().translateColors(plugin.getConfigManager().getPrefix() + "&cAdmin-Befehle:"));
                player.sendMessage(plugin.getConfigManager().translateColors(plugin.getConfigManager().getPrefix() + "/clan force kick <spieler> &7- Spieler aus Clan entfernen"));
                player.sendMessage(plugin.getConfigManager().translateColors(plugin.getConfigManager().getPrefix() + "/clan force delete <tag> &7- Clan auflösen"));
                break;

            case "disband":
                // Alias for delete – same confirmation flow
                {
                    ClanData disbandClan = getPlayerClan(playerUUID);
                    if (disbandClan == null) {
                        player.sendMessage(plugin.getConfigManager().getMessage("no-clan"));
                        return true;
                    }
                    if (!disbandClan.getLeader().equals(playerUUID)) {
                        player.sendMessage(plugin.getConfigManager().getMessage("not-clan-leader"));
                        return true;
                    }
                    if (args.length >= 2 && args[1].equalsIgnoreCase("confirm")) {
                        Long pending = pendingDeletes.get(playerUUID);
                        if (pending == null || System.currentTimeMillis() - pending > DELETE_CONFIRM_TIMEOUT_MS) {
                            player.sendMessage(plugin.getConfigManager().getMessage("clan-deleted-cancelled"));
                            return true;
                        }
                        pendingDeletes.remove(playerUUID);
                        String dissolvedTag = disbandClan.getTag();
                        for (UUID mem : disbandClan.getMembers()) {
                            PlayerData pd = plugin.getFileManager().loadPlayer(mem);
                            if (pd != null) {
                                pd.setClanTag(null);
                                pd.setRole("MEMBER");
                                try { plugin.getFileManager().savePlayer(mem, pd); } catch (IOException e) { /* ignore */ }
                            }
                        }
                        plugin.getFileManager().deleteClan(dissolvedTag);
                        player.sendMessage(plugin.getConfigManager().getMessage("clan-deleted"));
                        String broadcastMsg = plugin.getConfigManager().translateColors(
                                plugin.getConfigManager().getClanSystemPrefix() + "&cDer [" + dissolvedTag + "] Clan wurde aufgelöst.");
                        for (Player p : plugin.getServer().getOnlinePlayers()) {
                            p.sendMessage(broadcastMsg);
                        }
                    } else if (args.length >= 2 && args[1].equalsIgnoreCase("deny")) {
                        pendingDeletes.remove(playerUUID);
                        player.sendMessage(plugin.getConfigManager().getMessage("delete-denied"));
                    } else {
                        pendingDeletes.put(playerUUID, System.currentTimeMillis());
                        Component confirmMsg = Component.text(plugin.getConfigManager().getMessage("delete-confirm") + " ")
                                .append(Component.text("[accept]").color(TextColor.color(0x55FF55))
                                        .clickEvent(ClickEvent.runCommand("/clan disband confirm")))
                                .append(Component.text(" / ").color(TextColor.color(0xAAAAAA)))
                                .append(Component.text("[Deny]").color(TextColor.color(0xFF5555))
                                        .clickEvent(ClickEvent.runCommand("/clan disband deny")));
                        player.sendMessage(confirmMsg);
                    }
                }
                break;

            case "chat":
                {
                    ClanData chatClan2 = getPlayerClan(playerUUID);
                    if (chatClan2 == null) {
                        player.sendMessage(plugin.getConfigManager().getMessage("no-clan"));
                        return true;
                    }
                    if (args.length >= 2) {
                        // Send message to clan chat directly
                        String chatMsg = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                        ConfigManager cm2 = plugin.getConfigManager();
                        String format2 = cm2.translateColors(
                                cm2.getClanChatFormat()
                                        .replace("%player%", player.getName())
                                        .replace("%message%", chatMsg)
                        );
                        for (UUID mem : chatClan2.getMembers()) {
                            Player p = plugin.getServer().getPlayer(mem);
                            if (p != null) p.sendMessage(format2);
                        }
                    } else {
                        // Toggle clan chat mode
                        boolean enabled = plugin.toggleClanChat(playerUUID);
                        if (enabled) {
                            player.sendMessage(plugin.getConfigManager().getMessage("clan-chat-enabled"));
                        } else {
                            player.sendMessage(plugin.getConfigManager().getMessage("clan-chat-disabled"));
                        }
                    }
                }
                break;

            case "leader":
                {
                    ClanData leaderClan = getPlayerClan(playerUUID);
                    if (leaderClan == null) {
                        player.sendMessage(plugin.getConfigManager().getMessage("no-clan"));
                        return true;
                    }
                    if (!leaderClan.getLeader().equals(playerUUID)) {
                        player.sendMessage(plugin.getConfigManager().getMessage("not-clan-leader"));
                        return true;
                    }
                    if (args.length >= 2 && args[1].equalsIgnoreCase("confirm")) {
                        String targetName = pendingLeaderTransfers.remove(playerUUID);
                        if (targetName == null) {
                            player.sendMessage(plugin.getConfigManager().getMessage("clan-deleted-cancelled"));
                            return true;
                        }
                        Player leaderTarget = plugin.getServer().getPlayer(targetName);
                        if (leaderTarget == null) {
                            player.sendMessage(plugin.getConfigManager().getMessage("player-not-found"));
                            return true;
                        }
                        if (!leaderClan.getMembers().contains(leaderTarget.getUniqueId())) {
                            player.sendMessage(plugin.getConfigManager().getPrefix() + "Spieler ist nicht im Clan.");
                            return true;
                        }
                        // Transfer leadership
                        UUID newLeaderId = leaderTarget.getUniqueId();
                        leaderClan.setLeader(newLeaderId);
                        leaderClan.getModerators().remove(newLeaderId);
                        PlayerData newLeaderData = plugin.getFileManager().loadPlayer(newLeaderId);
                        if (newLeaderData != null) { newLeaderData.setRole("LEADER"); }
                        PlayerData oldLeaderData = plugin.getFileManager().loadPlayer(playerUUID);
                        if (oldLeaderData != null) { oldLeaderData.setRole("MEMBER"); }
                        try {
                            plugin.getFileManager().saveClan(leaderClan);
                            if (newLeaderData != null) plugin.getFileManager().savePlayer(newLeaderId, newLeaderData);
                            if (oldLeaderData != null) plugin.getFileManager().savePlayer(playerUUID, oldLeaderData);
                        } catch (IOException e) {
                            player.sendMessage(plugin.getConfigManager().getPrefix() + "Fehler beim Speichern.");
                        }
                        player.sendMessage(plugin.getConfigManager().getMessage("leader-transferred").replace("%player%", leaderTarget.getName()));
                        leaderTarget.sendMessage(plugin.getConfigManager().translateColors(
                                plugin.getConfigManager().getPrefix() + "&aDu bist nun der Leader des Clans " + leaderClan.getTag() + "."));
                    } else if (args.length >= 2 && args[1].equalsIgnoreCase("deny")) {
                        pendingLeaderTransfers.remove(playerUUID);
                        player.sendMessage(plugin.getConfigManager().getMessage("delete-denied"));
                    } else {
                        if (args.length < 2) {
                            player.sendMessage(plugin.getConfigManager().getPrefix() + "Verwendung: /clan leader <spieler>");
                            return true;
                        }
                        String ltTargetName = args[1];
                        Player ltTarget = plugin.getServer().getPlayer(ltTargetName);
                        if (ltTarget == null) {
                            player.sendMessage(plugin.getConfigManager().getMessage("player-not-found"));
                            return true;
                        }
                        if (!leaderClan.getMembers().contains(ltTarget.getUniqueId())) {
                            player.sendMessage(plugin.getConfigManager().getPrefix() + "Spieler ist nicht im Clan.");
                            return true;
                        }
                        pendingLeaderTransfers.put(playerUUID, ltTargetName);
                        Component confirmMsg = Component.text(
                                plugin.getConfigManager().getMessage("leader-transfer-confirm").replace("%player%", ltTarget.getName()) + " ")
                                .append(Component.text("[accept]").color(TextColor.color(0x55FF55))
                                        .clickEvent(ClickEvent.runCommand("/clan leader confirm")))
                                .append(Component.text(" / ").color(TextColor.color(0xAAAAAA)))
                                .append(Component.text("[Deny]").color(TextColor.color(0xFF5555))
                                        .clickEvent(ClickEvent.runCommand("/clan leader deny")));
                        player.sendMessage(confirmMsg);
                    }
                }
                break;

            case "logs":
                {
                    ClanData logClan = getPlayerClan(playerUUID);
                    if (logClan == null) {
                        player.sendMessage(plugin.getConfigManager().getMessage("no-clan"));
                        return true;
                    }
                    List<ClanLog> logs = plugin.getFileManager().loadLogs(logClan.getTag());
                    if (logs.isEmpty()) {
                        player.sendMessage(plugin.getConfigManager().getMessage("no-logs"));
                        return true;
                    }
                    // Create a written book with log entries
                    ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
                    BookMeta meta = (BookMeta) book.getItemMeta();
                    meta.setTitle("Clan Logs");
                    meta.setAuthor("ClanSystem");
                    List<Component> pages = new ArrayList<>();
                    // Build pages: ~14 lines per page
                    StringBuilder pageBuilder = new StringBuilder();
                    int lineCount = 0;
                    for (ClanLog log : logs) {
                        String line = log.format() + "\n";
                        lineCount++;
                        pageBuilder.append(line);
                        if (lineCount >= 10) {
                            pages.add(Component.text(pageBuilder.toString()));
                            pageBuilder = new StringBuilder();
                            lineCount = 0;
                        }
                    }
                    if (pageBuilder.length() > 0) {
                        pages.add(Component.text(pageBuilder.toString()));
                    }
                    meta.pages(pages);
                    book.setItemMeta(meta);
                    player.openBook(book);
                }
                break;

            case "skills":
                {
                    ClanData skillsClan = getPlayerClan(playerUUID);
                    if (skillsClan == null) {
                        player.sendMessage(plugin.getConfigManager().getMessage("no-clan"));
                        return true;
                    }
                    String skillsTitle = plugin.getConfigManager().getMessage("skills-title");
                    Inventory skillsInv = Bukkit.createInventory(null, 54, skillsTitle);
                    // Glass panes for row 1 (slots 0-8) and row 6 (slots 45-53), except column 5 (slot index 4/49)
                    ItemStack glassPane = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
                    ItemMeta glassMeta = glassPane.getItemMeta();
                    glassMeta.displayName(Component.text(" "));
                    glassPane.setItemMeta(glassMeta);
                    Set<Integer> separatorSlots = new HashSet<>(Arrays.asList(4, 13, 22, 31, 40, 49));
                    for (int i = 0; i <= 8; i++) {
                        if (!separatorSlots.contains(i)) skillsInv.setItem(i, glassPane.clone());
                    }
                    for (int i = 45; i <= 53; i++) {
                        if (!separatorSlots.contains(i)) skillsInv.setItem(i, glassPane.clone());
                    }
                    // Skill items in rows 2-5 (slots 9-44), except separator column
                    String[] skillNames = {
                        "§bGeschwindigkeit I", "§bStärke I", "§bSprung I", "§bRegeneration I",
                        "§bGeschwindigkeit II", "§bStärke II", "§bSprung II", "§bRegeneration II",
                        "§bGeschwindigkeit III", "§bStärke III", "§bSprung III", "§bRegeneration III",
                        "§bGeschwindigkeit IV", "§bStärke IV", "§bSprung IV", "§bRegeneration IV",
                        "§bGeschwindigkeit V", "§bStärke V", "§bSprung V", "§bRegeneration V",
                        "§bGeschwindigkeit VI", "§bStärke VI", "§bSprung VI", "§bRegeneration VI",
                        "§bGeschwindigkeit VII", "§bStärke VII", "§bSprung VII", "§bRegeneration VII",
                        "§bGeschwindigkeit VIII"
                    };
                    int nameIdx = 0;
                    for (int i = 9; i <= 44; i++) {
                        if (separatorSlots.contains(i)) continue;
                        ItemStack skillItem = new ItemStack(Material.ENCHANTED_BOOK);
                        ItemMeta skillMeta = skillItem.getItemMeta();
                        String sName = nameIdx < skillNames.length ? skillNames[nameIdx++] : "§bSkill";
                        skillMeta.displayName(Component.text(sName));
                        skillItem.setItemMeta(skillMeta);
                        skillsInv.setItem(i, skillItem);
                    }
                    player.openInventory(skillsInv);
                }
                break;

            case "war":
                {
                    ClanData warClan = getPlayerClan(playerUUID);
                    if (warClan == null) {
                        player.sendMessage(plugin.getConfigManager().getMessage("no-clan"));
                        return true;
                    }
                    if (args.length < 2 || args[1].equalsIgnoreCase("info")) {
                        player.sendMessage(plugin.getConfigManager().getMessage("war-info"));
                        return true;
                    }
                    String warSub = args[1].toLowerCase();
                    WarManager wm = plugin.getWarManager();
                    if (warSub.equals("accept")) {
                        // The leader of the target clan accepts the war
                        if (!warClan.getLeader().equals(playerUUID)) {
                            player.sendMessage(plugin.getConfigManager().getMessage("not-clan-leader"));
                            return true;
                        }
                        WarData pending = wm.getPendingWar(warClan.getTag());
                        if (pending == null) {
                            player.sendMessage(plugin.getConfigManager().getPrefix() + "Kein ausstehender Krieg.");
                            return true;
                        }
                        wm.removePendingWar(warClan.getTag());
                        pending.setStatus(WarData.Status.RUNNING);
                        pending.setStartTime(System.currentTimeMillis());
                        wm.addActiveWar(pending.getClan1(), pending);
                        wm.addActiveWar(pending.getClan2(), pending);
                        // Notify both clans
                        ClanData clan1Data = plugin.getFileManager().loadClan(pending.getClan1());
                        ClanData clan2Data = plugin.getFileManager().loadClan(pending.getClan2());
                        String acceptMsg = plugin.getConfigManager().getMessage("war-accepted");
                        if (clan1Data != null) {
                            for (UUID mem : clan1Data.getMembers()) {
                                Player p = plugin.getServer().getPlayer(mem);
                                if (p != null) p.sendMessage(acceptMsg);
                            }
                        }
                        if (clan2Data != null) {
                            for (UUID mem : clan2Data.getMembers()) {
                                Player p = plugin.getServer().getPlayer(mem);
                                if (p != null) p.sendMessage(acceptMsg);
                            }
                        }
                        // Schedule war end after duration
                        final WarData warFinal = pending;
                        long durationTicks = plugin.getConfigManager().getWarDurationMinutes() * 60L * 20L;
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            if (wm.hasActiveWar(warFinal.getClan1())) {
                                endWar(warFinal);
                            }
                        }, durationTicks);
                        return true;
                    }
                    if (warSub.equals("deny")) {
                        if (!warClan.getLeader().equals(playerUUID)) {
                            player.sendMessage(plugin.getConfigManager().getMessage("not-clan-leader"));
                            return true;
                        }
                        WarData pending = wm.getPendingWar(warClan.getTag());
                        if (pending == null) {
                            player.sendMessage(plugin.getConfigManager().getPrefix() + "Kein ausstehender Krieg.");
                            return true;
                        }
                        wm.removePendingWar(warClan.getTag());
                        player.sendMessage(plugin.getConfigManager().getMessage("war-denied"));
                        Player attackerLeader = plugin.getServer().getPlayer(
                                plugin.getFileManager().loadClan(pending.getClan1()) != null
                                        ? plugin.getFileManager().loadClan(pending.getClan1()).getLeader()
                                        : pending.getTargetLeader());
                        if (attackerLeader != null) {
                            attackerLeader.sendMessage(plugin.getConfigManager().getMessage("war-denied"));
                        }
                        return true;
                    }
                    // /clan war <clantag> – send war request
                    String targetWarTag = args[1];
                    if (!warClan.getLeader().equals(playerUUID)) {
                        player.sendMessage(plugin.getConfigManager().getMessage("not-clan-leader"));
                        return true;
                    }
                    // Check points
                    if (warClan.getPoints() < plugin.getConfigManager().getWarCostPoints()) {
                        player.sendMessage(plugin.getConfigManager().getMessage("war-not-enough-points"));
                        return true;
                    }
                    // Check cooldown
                    long cooldownMs = plugin.getConfigManager().getWarCooldownMinutes() * 60L * 1000L;
                    if (wm.isOnCooldown(warClan.getTag(), cooldownMs)) {
                        player.sendMessage(plugin.getConfigManager().getMessage("war-cooldown"));
                        return true;
                    }
                    // Check already at war
                    if (wm.hasActiveWar(warClan.getTag())) {
                        player.sendMessage(plugin.getConfigManager().getMessage("war-already-at-war"));
                        return true;
                    }
                    ClanData targetWarClan = plugin.getFileManager().loadClan(targetWarTag);
                    if (targetWarClan == null) {
                        player.sendMessage(plugin.getConfigManager().getMessage("clan-not-found"));
                        return true;
                    }
                    if (wm.hasActiveWar(targetWarTag)) {
                        player.sendMessage(plugin.getConfigManager().getMessage("war-target-at-war"));
                        return true;
                    }
                    // Create pending war
                    WarData warReq = new WarData(warClan.getTag(), targetWarTag, targetWarClan.getLeader());
                    wm.addPendingWar(targetWarTag, warReq);
                    player.sendMessage(plugin.getConfigManager().getMessage("war-request-sent").replace("%tag%", targetWarTag));
                    // Notify target leader
                    Player targetLeaderPlayer = plugin.getServer().getPlayer(targetWarClan.getLeader());
                    if (targetLeaderPlayer != null) {
                        Component warNotif = Component.text(plugin.getConfigManager().translateColors(
                                plugin.getConfigManager().getMessage("war-request-received").replace("%tag%", warClan.getTag())) + " ")
                                .append(Component.text("[Accept]").color(TextColor.color(0x55FF55))
                                        .clickEvent(ClickEvent.runCommand("/clan war accept")))
                                .append(Component.text(" / ").color(TextColor.color(0xAAAAAA)))
                                .append(Component.text("[Deny]").color(TextColor.color(0xFF5555))
                                        .clickEvent(ClickEvent.runCommand("/clan war deny")));
                        targetLeaderPlayer.sendMessage(warNotif);
                    }
                }
                break;

            default:
                player.sendMessage(plugin.getConfigManager().getPrefix() + "Unbekannter Subcommand.");
                break;
        }

        return true;
    }

    private ClanData getPlayerClan(UUID player) {
        PlayerData p = plugin.getFileManager().loadPlayer(player);
        if (p == null || p.getClanTag() == null) return null;
        return plugin.getFileManager().loadClan(p.getClanTag());
    }

    private String getRank(int points) {
        Map<String, Integer> ranks = plugin.getConfigManager().getRanks();
        String currentRank = "Bronze";
        for (Map.Entry<String, Integer> entry : ranks.entrySet()) {
            if (points >= entry.getValue()) {
                currentRank = entry.getKey();
            } else {
                break;
            }
        }
        return currentRank;
    }

    private void endWar(WarData war) {
        WarManager wm = plugin.getWarManager();
        wm.removeActiveWar(war.getClan1());
        wm.removeActiveWar(war.getClan2());
        war.setStatus(WarData.Status.FINISHED);
        // Determine winner by points comparison
        ClanData clan1Data = plugin.getFileManager().loadClan(war.getClan1());
        ClanData clan2Data = plugin.getFileManager().loadClan(war.getClan2());
        String winnerTag;
        String loserTag;
        if (clan1Data != null && clan2Data != null) {
            if (clan1Data.getPoints() >= clan2Data.getPoints()) {
                winnerTag = war.getClan1();
                loserTag = war.getClan2();
            } else {
                winnerTag = war.getClan2();
                loserTag = war.getClan1();
            }
        } else {
            winnerTag = war.getClan1();
            loserTag = war.getClan2();
        }
        war.setWinner(winnerTag);
        wm.setCooldown(war.getClan1());
        wm.setCooldown(war.getClan2());
        // Update points
        ClanData winnerData = plugin.getFileManager().loadClan(winnerTag);
        ClanData loserData = plugin.getFileManager().loadClan(loserTag);
        int winnerPts = plugin.getConfigManager().getWarWinnerPoints();
        int loserPts = plugin.getConfigManager().getWarLoserPoints();
        if (winnerData != null) {
            winnerData.setPoints(winnerData.getPoints() + winnerPts);
            try { plugin.getFileManager().saveClan(winnerData); } catch (IOException e) { /* ignore */ }
            plugin.getFileManager().addLog(winnerTag, new ClanLog(ClanLog.Type.WAR_WON, "Krieg gegen " + loserTag + " gewonnen"));
        }
        if (loserData != null) {
            loserData.setPoints(Math.max(0, loserData.getPoints() - loserPts));
            try { plugin.getFileManager().saveClan(loserData); } catch (IOException e) { /* ignore */ }
            plugin.getFileManager().addLog(loserTag, new ClanLog(ClanLog.Type.WAR_LOST, "Krieg gegen " + winnerTag + " verloren"));
        }
        // Notify players
        String wonMsg = plugin.getConfigManager().getMessage("war-won");
        String lostMsg = plugin.getConfigManager().getMessage("war-lost");
        String broadcastMsg = plugin.getConfigManager().getMessage("war-broadcast")
                .replace("%clan1%", war.getClan1()).replace("%clan2%", war.getClan2())
                .replace("%winner%", winnerTag);
        if (winnerData != null) {
            for (UUID mem : winnerData.getMembers()) {
                Player p = plugin.getServer().getPlayer(mem);
                if (p != null) p.sendMessage(plugin.getConfigManager().translateColors(wonMsg));
            }
        }
        if (loserData != null) {
            for (UUID mem : loserData.getMembers()) {
                Player p = plugin.getServer().getPlayer(mem);
                if (p != null) p.sendMessage(plugin.getConfigManager().translateColors(lostMsg));
            }
        }
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.sendMessage(plugin.getConfigManager().translateColors(broadcastMsg));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return null;
        }

        Player player = (Player) sender;
        UUID playerUUID = player.getUniqueId();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList(
                    "create", "delete", "disband", "invite", "accept", "deny", "leave", "kick",
                    "promote", "demote", "rename", "info", "toggle", "stats", "ranking",
                    "chest", "spawn", "setspawn", "requests", "request",
                    "chat", "leader", "logs", "skills", "war"));
            if (player.hasPermission("clan.admin")) {
                subs.add("force");
                subs.add("admin");
            }
            return subs;
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            ClanData clan = getPlayerClan(playerUUID);

            switch (sub) {
                case "invite":
                case "accept":
                case "deny":
                case "request":
                case "leader":
                case "chat":
                    return null;

                case "war":
                    return Arrays.asList("info", "accept", "deny");

                case "force":
                    return Arrays.asList("kick", "delete");

                case "kick":
                case "promote":
                case "demote":
                    if (clan != null) {
                        List<String> memberNames = new ArrayList<>();
                        for (UUID mem : clan.getMembers()) {
                            memberNames.add(Bukkit.getOfflinePlayer(mem).getName());
                        }
                        return memberNames;
                    }
                    break;

                case "rename":
                    return Arrays.asList("neuerTag");

                default:
                    break;
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("force")) {
            return null;
        }

        return null;
    }
}
