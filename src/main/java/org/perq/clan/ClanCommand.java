package org.perq.clan;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
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
    private Map<UUID, PendingForceKick> pendingForceKicks = new HashMap<>();
    private Map<UUID, PendingTagAction> pendingForceDeletes = new HashMap<>();
    private Map<UUID, PendingTagAction> pendingLeaderRequests = new HashMap<>();
    /** Millis before a pending confirmation expires. */
    private static final long DELETE_CONFIRM_TIMEOUT_MS = 30_000L;

    private record PendingForceKick(UUID targetId, String targetName, long timestamp) {}
    private record PendingTagAction(String tag, long timestamp) {}

    public ClanCommand(Clan plugin) {
        this.plugin = plugin;
    }

    private static final Set<String> SUBCOMMANDS = new HashSet<>(Arrays.asList(
            "create", "delete", "invite", "accept", "deny", "leave", "kick",
            "promote", "demote", "rename", "info", "toggle", "stats", "ranking",
            "chest", "spawn", "setspawn", "force", "requests", "join", "request", "admin"
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
                    // First invocation – ask for confirmation with Accept and Deny buttons
                    pendingDeletes.put(playerUUID, System.currentTimeMillis());
                    String confirmText = plugin.getConfigManager().getMessage("delete-confirm") + " ";
                    Component confirmMsg = Component.text(confirmText)
                            .append(Component.text("[Accept]")
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
                InviteData invite = new InviteData(cl.getTag());
                try {
                    plugin.getFileManager().saveInvite(target.getUniqueId(), invite);
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
                    if (args.length >= 3 && args[2].equalsIgnoreCase("confirm")) {
                        PendingForceKick pending = pendingForceKicks.get(playerUUID);
                        if (pending == null || System.currentTimeMillis() - pending.timestamp() > DELETE_CONFIRM_TIMEOUT_MS) {
                            pendingForceKicks.remove(playerUUID);
                            player.sendMessage(plugin.getConfigManager().getMessage("action-cancelled"));
                            return true;
                        }
                        pendingForceKicks.remove(playerUUID);
                        Player forceTarget = plugin.getServer().getPlayer(pending.targetId());
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
                        return true;
                    } else if (args.length >= 3 && args[2].equalsIgnoreCase("deny")) {
                        pendingForceKicks.remove(playerUUID);
                        player.sendMessage(plugin.getConfigManager().getMessage("action-cancelled"));
                        return true;
                    }
                    if (args.length < 3) {
                        player.sendMessage(plugin.getConfigManager().getPrefix() + "Verwendung: /clan force kick <spieler>");
                        return true;
                    }
                    Player forceTarget = plugin.getServer().getPlayer(args[2]);
                    if (forceTarget == null) {
                        player.sendMessage(plugin.getConfigManager().getMessage("player-not-found"));
                        return true;
                    }
                    pendingForceKicks.put(playerUUID, new PendingForceKick(forceTarget.getUniqueId(), forceTarget.getName(), System.currentTimeMillis()));
                    String confirmText = plugin.getConfigManager().getMessage("force-kick-confirm").replace("%player%", forceTarget.getName()) + " ";
                    Component confirmMsg = Component.text(confirmText)
                            .append(Component.text("[Accept]")
                                    .color(TextColor.color(0x55FF55))
                                    .clickEvent(ClickEvent.runCommand("/clan force kick confirm")))
                            .append(Component.text(" / ").color(TextColor.color(0xAAAAAA)))
                            .append(Component.text("[Deny]")
                                    .color(TextColor.color(0xFF5555))
                                    .clickEvent(ClickEvent.runCommand("/clan force kick deny")));
                    player.sendMessage(confirmMsg);
                    return true;
                } else if (args[1].equalsIgnoreCase("delete")) {
                    if (args.length >= 3 && args[2].equalsIgnoreCase("confirm")) {
                        PendingTagAction pending = pendingForceDeletes.get(playerUUID);
                        if (pending == null || System.currentTimeMillis() - pending.timestamp() > DELETE_CONFIRM_TIMEOUT_MS) {
                            pendingForceDeletes.remove(playerUUID);
                            player.sendMessage(plugin.getConfigManager().getMessage("clan-deleted-cancelled"));
                            return true;
                        }
                        pendingForceDeletes.remove(playerUUID);
                        String forceDeleteTag = pending.tag();
                        ClanData forceDeleteClan = plugin.getFileManager().loadClan(forceDeleteTag);
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
                        plugin.getFileManager().deleteClan(forceDeleteTag);
                        player.sendMessage(plugin.getConfigManager().getMessage("clan-deleted"));
                        // Broadcast to all players
                        String broadcastMsg = plugin.getConfigManager().getMessage("clan-dissolved").replace("%tag%", forceDeleteTag);
                        for (Player p : plugin.getServer().getOnlinePlayers()) {
                            p.sendMessage(broadcastMsg);
                        }
                        return true;
                    } else if (args.length >= 3 && args[2].equalsIgnoreCase("deny")) {
                        pendingForceDeletes.remove(playerUUID);
                        player.sendMessage(plugin.getConfigManager().getMessage("delete-denied"));
                        return true;
                    }
                    if (args.length < 3) {
                        player.sendMessage(plugin.getConfigManager().getPrefix() + "Verwendung: /clan force delete <tag>");
                        return true;
                    }
                    String forceDeleteTag = args[2];
                    ClanData forceDeleteClan = plugin.getFileManager().loadClan(forceDeleteTag);
                    if (forceDeleteClan == null) {
                        player.sendMessage(plugin.getConfigManager().getMessage("clan-not-found"));
                        return true;
                    }
                    pendingForceDeletes.put(playerUUID, new PendingTagAction(forceDeleteTag, System.currentTimeMillis()));
                    String confirmText = plugin.getConfigManager().getMessage("force-delete-confirm").replace("%tag%", forceDeleteTag) + " ";
                    Component confirmMsg = Component.text(confirmText)
                            .append(Component.text("[Accept]")
                                    .color(TextColor.color(0x55FF55))
                                    .clickEvent(ClickEvent.runCommand("/clan force delete confirm")))
                            .append(Component.text(" / ").color(TextColor.color(0xAAAAAA)))
                            .append(Component.text("[Deny]")
                                    .color(TextColor.color(0xFF5555))
                                    .clickEvent(ClickEvent.runCommand("/clan force delete deny")));
                    player.sendMessage(confirmMsg);
                    return true;
                } else {
                    player.sendMessage(plugin.getConfigManager().getPrefix() + "Verwendung: /clan force kick <spieler> | /clan force delete <tag>");
                }
                break;

            case "requests":
                List<InviteData> requests = plugin.getFileManager().loadAllInvites(playerUUID);
                if (requests.isEmpty()) {
                    player.sendMessage(plugin.getConfigManager().getMessage("no-requests"));
                    return true;
                }
                player.sendMessage(plugin.getConfigManager().getMessage("requests-header"));
                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");
                for (InviteData req : requests) {
                    String reqDate = sdf.format(new Date(req.getTimestamp()));
                    String reqClanTag = req.getFromClan();
                    Component reqLine = Component.text("  " + reqClanTag + " - " + reqDate + " ")
                            .append(Component.text("[Join]")
                                    .color(TextColor.color(0x55FF55))
                                    .clickEvent(ClickEvent.runCommand("/clan join " + reqClanTag)));
                    player.sendMessage(reqLine);
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
                if (args[1].equalsIgnoreCase("confirm")) {
                    PendingTagAction pending = pendingLeaderRequests.get(playerUUID);
                    if (pending == null || System.currentTimeMillis() - pending.timestamp() > DELETE_CONFIRM_TIMEOUT_MS) {
                        pendingLeaderRequests.remove(playerUUID);
                        player.sendMessage(plugin.getConfigManager().getMessage("clan-deleted-cancelled"));
                        return true;
                    }
                    pendingLeaderRequests.remove(playerUUID);
                    ClanData currentClan = getPlayerClan(playerUUID);
                    if (currentClan == null) {
                        player.sendMessage(plugin.getConfigManager().getMessage("no-clan"));
                        return true;
                    }
                    if (!currentClan.getLeader().equals(playerUUID)) {
                        player.sendMessage(plugin.getConfigManager().getMessage("not-clan-leader"));
                        return true;
                    }
                    String reqTag = pending.tag();
                    ClanData reqClan = plugin.getFileManager().loadClan(reqTag);
                    if (reqClan == null) {
                        player.sendMessage(plugin.getConfigManager().getMessage("clan-not-found"));
                        return true;
                    }
                    String dissolvedTag = currentClan.getTag();
                    for (UUID mem : currentClan.getMembers()) {
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
                    String broadcastMsg = plugin.getConfigManager().getMessage("clan-dissolved").replace("%tag%", dissolvedTag);
                    for (Player p : plugin.getServer().getOnlinePlayers()) {
                        p.sendMessage(broadcastMsg);
                    }
                    sendClanRequest(player, playerUUID, reqTag, reqClan);
                    return true;
                } else if (args[1].equalsIgnoreCase("deny")) {
                    pendingLeaderRequests.remove(playerUUID);
                    player.sendMessage(plugin.getConfigManager().getMessage("delete-denied"));
                    return true;
                }
                String reqTag = args[1];
                ClanData reqClan = plugin.getFileManager().loadClan(reqTag);
                if (reqClan == null) {
                    player.sendMessage(plugin.getConfigManager().getMessage("clan-not-found"));
                    return true;
                }
                PlayerData reqPlayerData = plugin.getFileManager().loadPlayer(playerUUID);
                if (reqPlayerData == null) {
                    reqPlayerData = new PlayerData(player.getName());
                }
                // Check if a request to this clan already exists
                List<InviteData> existingRequests = plugin.getFileManager().loadAllInvites(playerUUID);
                for (InviteData existing : existingRequests) {
                    if (reqTag.equals(existing.getFromClan())) {
                        player.sendMessage(plugin.getConfigManager().getPrefix() + "Du hast bereits eine Anfrage an diesen Clan gesendet.");
                        return true;
                    }
                }
                if (reqPlayerData.getClanTag() != null) {
                    ClanData currentClan = plugin.getFileManager().loadClan(reqPlayerData.getClanTag());
                    if (currentClan != null && currentClan.getLeader().equals(playerUUID)) {
                        if (currentClan.getTag().equalsIgnoreCase(reqTag)) {
                            player.sendMessage(plugin.getConfigManager().getMessage("already-in-clan"));
                            return true;
                        }
                        pendingLeaderRequests.put(playerUUID, new PendingTagAction(reqTag, System.currentTimeMillis()));
                        String confirmText = plugin.getConfigManager().getMessage("leader-request-confirm") + " ";
                        Component confirmMsg = Component.text(confirmText)
                                .append(Component.text("[Accept]")
                                        .color(TextColor.color(0x55FF55))
                                        .clickEvent(ClickEvent.runCommand("/clan request confirm")))
                                .append(Component.text(" / ").color(TextColor.color(0xAAAAAA)))
                                .append(Component.text("[Deny]")
                                        .color(TextColor.color(0xFF5555))
                                        .clickEvent(ClickEvent.runCommand("/clan request deny")));
                        player.sendMessage(confirmMsg);
                        return true;
                    }
                    player.sendMessage(plugin.getConfigManager().getMessage("already-in-clan"));
                    return true;
                }
                sendClanRequest(player, playerUUID, reqTag, reqClan);
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

            default:
                player.sendMessage(plugin.getConfigManager().getPrefix() + "Unbekannter Subcommand.");
                break;
        }

        return true;
    }

    private void sendClanRequest(Player player, UUID playerUUID, String reqTag, ClanData reqClan) {
        InviteData reqInvite = new InviteData(reqTag);
        try {
            plugin.getFileManager().saveInvite(playerUUID, reqInvite);
            player.sendMessage(plugin.getConfigManager().getMessage("request-sent").replace("%tag%", reqTag));
            // Notify the clan leader if they have notifications enabled (toggle on)
            Player leaderOnline = plugin.getServer().getPlayer(reqClan.getLeader());
            if (leaderOnline != null) {
                PlayerData leaderData = plugin.getFileManager().loadPlayer(reqClan.getLeader());
                if (leaderData == null || leaderData.isInvitesEnabled()) {
                    leaderOnline.sendMessage(plugin.getConfigManager().getMessage("request-received")
                            .replace("%player%", player.getName()));
                }
            }
        } catch (IOException e) {
            player.sendMessage(plugin.getConfigManager().getPrefix() + "Fehler beim Speichern.");
        }
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return null;
        }

        Player player = (Player) sender;
        UUID playerUUID = player.getUniqueId();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList(
                    "create", "delete", "invite", "accept", "deny", "leave", "kick",
                    "promote", "demote", "rename", "info", "toggle", "stats", "ranking",
                    "chest", "spawn", "setspawn", "requests", "request"));
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
                case "join":
                case "request":
                    return null; // Spieler-Namen automatisch vervollständigen lassen

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
            return null; // let Bukkit complete online player names or clan tags
        }

        return null;
    }
}
