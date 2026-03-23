package org.perq.clan;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class ClanCommand implements CommandExecutor, TabCompleter {

    private final Clan plugin;
    private final Map<String, Inventory> clanChests = new HashMap<>();
    private final Map<UUID, Long> spawnCooldowns = new HashMap<>();
    private final Map<UUID, Integer> spawnTaskIds = new HashMap<>();
    /** Stores UUIDs of players who initiated /clan disband|delete but not yet confirmed. */
    private final Map<UUID, Long> pendingDeletes = new HashMap<>();
    /** Admin UUID -> target player name for /clan force kick confirmations. */
    private final Map<UUID, String> pendingForceKicks = new HashMap<>();
    /** Admin UUID -> clan tag for /clan force delete confirmations. */
    private final Map<UUID, String> pendingForceDeletes = new HashMap<>();
    /** Leader UUID -> target clan tag for /clan request leader-dissolve confirmation. */
    private final Map<UUID, String> pendingLeaderRequests = new HashMap<>();
    /** Leader UUID -> target player name for /clan leader transfer confirmation. */
    private final Map<UUID, String> pendingLeaderTransfers = new HashMap<>();
    /** Inviter UUID -> last invite timestamp for 10-second cooldown. */
    private final Map<UUID, Long> inviteCooldowns = new HashMap<>();
    /** War arena spawns keyed by "a" and "b". */
    private final Map<String, Location> arenaSpawns = new HashMap<>();

    private static final long DELETE_CONFIRM_TIMEOUT_MS = 30_000L;
    private static final long INVITE_COOLDOWN_MS = 10_000L;

    private static final Set<String> SUBCOMMANDS = new HashSet<>(Arrays.asList(
            "create", "disband", "delete", "invite", "accept", "deny", "join", "leave",
            "kick", "promote", "demote", "leader", "rename", "info", "toggle", "stats",
            "ranking", "chest", "spawn", "setspawn", "request", "requests",
            "accept-request", "deny-request", "logs", "skills", "settings", "war", "force", "admin"
    ));

    public ClanCommand(Clan plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern verwendet werden.");
            return true;
        }

        Player player = (Player) sender;
        UUID playerUUID = player.getUniqueId();
        ConfigManager cm = plugin.getConfigManager();

        if (!player.hasPermission("clan.default")) {
            player.sendMessage(cm.getMessage("no-permission"));
            return true;
        }

        // Clan-chat routing: unknown sub-arg treated as chat message
        if (args.length > 0 && !SUBCOMMANDS.contains(args[0].toLowerCase())) {
            ClanData chatClan = getPlayerClan(playerUUID);
            if (chatClan == null) {
                player.sendMessage(cm.getMessage("no-clan"));
                return true;
            }
            String chatMessage = String.join(" ", args);
            String format = cm.translateColors(
                    cm.getClanChatFormat()
                            .replace("%player%", player.getName())
                            .replace("%message%", chatMessage)
            );
            for (UUID mem : chatClan.getMembers()) {
                Player p = Bukkit.getPlayer(mem);
                if (p != null) p.sendMessage(format);
            }
            return true;
        }

        if (args.length == 0) {
            sendHelp(player, cm);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {

            case "create": {
                if (args.length < 2) {
                    player.sendMessage(cm.getPrefix() + "Verwendung: /clan create <tag>");
                    return true;
                }
                String tag = args[1];
                boolean isVip = player.hasPermission("clan.vip");
                TagValidator.ValidationResult vr = new TagValidator(plugin).validate(tag, isVip);
                if (!vr.isValid()) {
                    player.sendMessage(vr.getErrorMessage());
                    return true;
                }
                if (plugin.getFileManager().loadClan(tag) != null) {
                    player.sendMessage(cm.getMessage("clan-exists"));
                    return true;
                }
                PlayerData pData = plugin.getFileManager().loadPlayer(playerUUID);
                if (pData == null) pData = new PlayerData(player.getName());
                if (pData.getClanTag() != null) {
                    player.sendMessage(cm.getMessage("already-in-clan"));
                    return true;
                }
                ClanData newClan = new ClanData(tag, playerUUID);
                newClan.addLog(player.getName() + " hat den Clan gegr\u00fcndet.");
                try {
                    plugin.getFileManager().saveClan(newClan);
                    pData.setClanTag(tag);
                    pData.setRole("LEADER");
                    plugin.getFileManager().savePlayer(playerUUID, pData);
                    player.sendMessage(cm.getMessage("clan-created").replace("%tag%", tag));
                } catch (IOException e) {
                    player.sendMessage(cm.getPrefix() + "Fehler beim Speichern.");
                }
                break;
            }

            case "disband":
            case "delete": {
                ClanData disbandClan = getPlayerClan(playerUUID);
                if (disbandClan == null) {
                    player.sendMessage(cm.getMessage("no-clan"));
                    return true;
                }
                if (!disbandClan.getLeader().equals(playerUUID)) {
                    player.sendMessage(cm.getMessage("not-clan-leader"));
                    return true;
                }
                String confirmCmd = "/clan " + sub + " confirm";
                String denyCmd = "/clan " + sub + " deny";
                if (args.length >= 2 && args[1].equalsIgnoreCase("confirm")) {
                    Long pending = pendingDeletes.get(playerUUID);
                    if (pending == null || System.currentTimeMillis() - pending > DELETE_CONFIRM_TIMEOUT_MS) {
                        player.sendMessage(cm.getMessage("clan-deleted-cancelled"));
                        return true;
                    }
                    pendingDeletes.remove(playerUUID);
                    String dissolvedTag = disbandClan.getTag();
                    for (UUID mem : new ArrayList<>(disbandClan.getMembers())) {
                        PlayerData pd = plugin.getFileManager().loadPlayer(mem);
                        if (pd != null) {
                            pd.setClanTag(null);
                            pd.setRole("MEMBER");
                            try { plugin.getFileManager().savePlayer(mem, pd); } catch (IOException ignored) { /* continue */ }
                        }
                    }
                    plugin.getFileManager().deleteClan(dissolvedTag);
                    String broadcastMsg = cm.getMessage("clan-dissolved").replace("%tag%", dissolvedTag);
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendMessage(broadcastMsg);
                    }
                } else if (args.length >= 2 && args[1].equalsIgnoreCase("deny")) {
                    pendingDeletes.remove(playerUUID);
                    player.sendMessage(cm.getMessage("delete-denied"));
                } else {
                    pendingDeletes.put(playerUUID, System.currentTimeMillis());
                    player.sendMessage(cm.getPrefix() + "Der [" + disbandClan.getTag() + "] Clan wird aufgel\u00f6st wenn du best\u00e4tigst.");
                    player.sendMessage(cm.translateColors("&cBist du sicher das du den Clan l\u00f6schen willst?"));
                    player.sendMessage(
                            Component.text("[Accept]")
                                    .color(TextColor.color(0x55FF55))
                                    .clickEvent(ClickEvent.runCommand(confirmCmd))
                    );
                    player.sendMessage(
                            Component.text("[Deny]")
                                    .color(TextColor.color(0xFF5555))
                                    .clickEvent(ClickEvent.runCommand(denyCmd))
                    );
                }
                break;
            }

            case "leave": {
                ClanData leaveClan = getPlayerClan(playerUUID);
                if (leaveClan == null) {
                    player.sendMessage(cm.getMessage("no-clan"));
                    return true;
                }
                if (leaveClan.getLeader().equals(playerUUID)) {
                    if (leaveClan.getMembers().size() > 1) {
                        UUID newLeader = null;
                        if (!leaveClan.getModerators().isEmpty()) {
                            newLeader = leaveClan.getModerators().get(0);
                            leaveClan.getModerators().remove(0);
                        } else {
                            for (UUID mem : leaveClan.getMembers()) {
                                if (!mem.equals(playerUUID)) { newLeader = mem; break; }
                            }
                        }
                        if (newLeader != null) {
                            leaveClan.setLeader(newLeader);
                            PlayerData nlData = plugin.getFileManager().loadPlayer(newLeader);
                            if (nlData != null) {
                                nlData.setRole("LEADER");
                                try { plugin.getFileManager().savePlayer(newLeader, nlData); } catch (IOException ignored) { /* continue */ }
                            }
                        }
                    } else {
                        plugin.getFileManager().deleteClan(leaveClan.getTag());
                    }
                }
                leaveClan.getMembers().remove(playerUUID);
                leaveClan.getModerators().remove(playerUUID);
                leaveClan.addLog(player.getName() + " hat den Clan verlassen.");
                PlayerData leaveData = plugin.getFileManager().loadPlayer(playerUUID);
                if (leaveData != null) {
                    leaveData.setClanTag(null);
                    leaveData.setRole("MEMBER");
                    try {
                        plugin.getFileManager().savePlayer(playerUUID, leaveData);
                        plugin.getFileManager().saveClan(leaveClan);
                    } catch (IOException ignored) { /* continue */ }
                }
                player.sendMessage(cm.getMessage("left-clan"));
                break;
            }

            case "kick": {
                if (args.length < 2) {
                    player.sendMessage(cm.getPrefix() + "Verwendung: /clan kick <spieler>");
                    return true;
                }
                ClanData kickClan = getPlayerClan(playerUUID);
                if (kickClan == null) {
                    player.sendMessage(cm.getMessage("no-clan"));
                    return true;
                }
                PlayerData kickerData = plugin.getFileManager().loadPlayer(playerUUID);
                if (kickerData == null || (!"LEADER".equals(kickerData.getRole()) && !"MOD".equals(kickerData.getRole()))) {
                    player.sendMessage(cm.getMessage("no-permission"));
                    return true;
                }
                Player kickTarget = Bukkit.getPlayer(args[1]);
                if (kickTarget == null) {
                    player.sendMessage(cm.getMessage("player-not-found"));
                    return true;
                }
                if (kickTarget.getUniqueId().equals(playerUUID)) {
                    player.sendMessage(cm.getPrefix() + "Du kannst dich nicht selbst kicken.");
                    return true;
                }
                if (kickClan.getLeader().equals(kickTarget.getUniqueId())) {
                    player.sendMessage(cm.getMessage("no-permission"));
                    return true;
                }
                if (!kickClan.getMembers().contains(kickTarget.getUniqueId())) {
                    player.sendMessage(cm.getPrefix() + "Spieler ist nicht im Clan.");
                    return true;
                }
                kickClan.getMembers().remove(kickTarget.getUniqueId());
                kickClan.getModerators().remove(kickTarget.getUniqueId());
                kickClan.addLog(kickTarget.getName() + " wurde von " + player.getName() + " gekickt.");
                PlayerData kickTargetData = plugin.getFileManager().loadPlayer(kickTarget.getUniqueId());
                if (kickTargetData != null) {
                    kickTargetData.setClanTag(null);
                    kickTargetData.setRole("MEMBER");
                    try {
                        plugin.getFileManager().savePlayer(kickTarget.getUniqueId(), kickTargetData);
                        plugin.getFileManager().saveClan(kickClan);
                    } catch (IOException ignored) { /* continue */ }
                }
                player.sendMessage(cm.getMessage("kicked").replace("%player%", kickTarget.getName()));
                kickTarget.sendMessage(cm.getMessage("kicked"));
                break;
            }

            case "promote": {
                if (args.length < 2) {
                    player.sendMessage(cm.getPrefix() + "Verwendung: /clan promote <spieler>");
                    return true;
                }
                ClanData promoteClan = getPlayerClan(playerUUID);
                if (promoteClan == null) {
                    player.sendMessage(cm.getMessage("no-clan"));
                    return true;
                }
                if (!promoteClan.getLeader().equals(playerUUID)) {
                    player.sendMessage(cm.getMessage("no-permission"));
                    return true;
                }
                Player promoteTarget = Bukkit.getPlayer(args[1]);
                if (promoteTarget == null) {
                    player.sendMessage(cm.getMessage("player-not-found"));
                    return true;
                }
                if (!promoteClan.getMembers().contains(promoteTarget.getUniqueId())) {
                    player.sendMessage(cm.getPrefix() + "Spieler ist nicht im Clan.");
                    return true;
                }
                PlayerData promoteData = plugin.getFileManager().loadPlayer(promoteTarget.getUniqueId());
                if (promoteData == null) {
                    player.sendMessage(cm.getPrefix() + "Spielerdaten nicht gefunden.");
                    return true;
                }
                if ("MEMBER".equals(promoteData.getRole())) {
                    promoteData.setRole("MOD");
                    if (!promoteClan.getModerators().contains(promoteTarget.getUniqueId())) {
                        promoteClan.getModerators().add(promoteTarget.getUniqueId());
                    }
                    promoteClan.addLog(promoteTarget.getName() + " wurde zum MOD bef\u00f6rdert.");
                    try {
                        plugin.getFileManager().savePlayer(promoteTarget.getUniqueId(), promoteData);
                        plugin.getFileManager().saveClan(promoteClan);
                        player.sendMessage(cm.getMessage("promoted").replace("%player%", promoteTarget.getName()));
                    } catch (IOException e) {
                        player.sendMessage(cm.getPrefix() + "Fehler beim Speichern.");
                    }
                } else {
                    // Already MOD: use /clan leader <player> for leadership transfer
                    player.sendMessage(cm.getMessage("promote-only-mod"));
                }
                break;
            }

            case "demote": {
                ClanData demoteClan = getPlayerClan(playerUUID);
                if (demoteClan == null) {
                    player.sendMessage(cm.getMessage("no-clan"));
                    return true;
                }
                PlayerData actorData = plugin.getFileManager().loadPlayer(playerUUID);
                if (actorData == null) {
                    player.sendMessage(cm.getPrefix() + "Spielerdaten nicht gefunden.");
                    return true;
                }
                if (args.length < 2) {
                    if ("MOD".equals(actorData.getRole())) {
                        actorData.setRole("MEMBER");
                        demoteClan.getModerators().remove(playerUUID);
                        demoteClan.addLog(player.getName() + " hat sich selbst zum MEMBER degradiert.");
                        try {
                            plugin.getFileManager().savePlayer(playerUUID, actorData);
                            plugin.getFileManager().saveClan(demoteClan);
                            player.sendMessage(cm.getMessage("demoted").replace("%player%", player.getName()));
                        } catch (IOException e) {
                            player.sendMessage(cm.getPrefix() + "Fehler beim Speichern.");
                        }
                        return true;
                    }
                    player.sendMessage(cm.getPrefix() + "Verwendung: /clan demote <spieler>");
                    return true;
                }
                String targetName = args[1];
                if (targetName.equalsIgnoreCase(player.getName()) && "MOD".equals(actorData.getRole())) {
                    actorData.setRole("MEMBER");
                    demoteClan.getModerators().remove(playerUUID);
                    demoteClan.addLog(player.getName() + " hat sich selbst zum MEMBER degradiert.");
                    try {
                        plugin.getFileManager().savePlayer(playerUUID, actorData);
                        plugin.getFileManager().saveClan(demoteClan);
                        player.sendMessage(cm.getMessage("demoted").replace("%player%", player.getName()));
                    } catch (IOException e) {
                        player.sendMessage(cm.getPrefix() + "Fehler beim Speichern.");
                    }
                    return true;
                }
                if (!demoteClan.getLeader().equals(playerUUID)) {
                    player.sendMessage(cm.getMessage("no-permission"));
                    return true;
                }
                Player demoteTarget = Bukkit.getPlayer(targetName);
                if (demoteTarget == null) {
                    player.sendMessage(cm.getMessage("player-not-found"));
                    return true;
                }
                if (!demoteClan.getMembers().contains(demoteTarget.getUniqueId())) {
                    player.sendMessage(cm.getPrefix() + "Spieler ist nicht im Clan.");
                    return true;
                }
                PlayerData demoteData = plugin.getFileManager().loadPlayer(demoteTarget.getUniqueId());
                if (demoteData == null) {
                    player.sendMessage(cm.getPrefix() + "Spielerdaten nicht gefunden.");
                    return true;
                }
                if ("MOD".equals(demoteData.getRole())) {
                    demoteData.setRole("MEMBER");
                    demoteClan.getModerators().remove(demoteTarget.getUniqueId());
                    demoteClan.addLog(demoteTarget.getName() + " wurde zum MEMBER degradiert.");
                    try {
                        plugin.getFileManager().savePlayer(demoteTarget.getUniqueId(), demoteData);
                        plugin.getFileManager().saveClan(demoteClan);
                        player.sendMessage(cm.getMessage("demoted").replace("%player%", demoteTarget.getName()));
                    } catch (IOException e) {
                        player.sendMessage(cm.getPrefix() + "Fehler beim Speichern.");
                    }
                } else {
                    player.sendMessage(cm.getPrefix() + "Spieler kann nicht degradiert werden.");
                }
                break;
            }

            case "leader": {
                if (args.length < 2) {
                    player.sendMessage(cm.getPrefix() + "Verwendung: /clan leader <spieler>");
                    return true;
                }
                ClanData leaderClan = getPlayerClan(playerUUID);
                if (leaderClan == null) {
                    player.sendMessage(cm.getMessage("no-clan"));
                    return true;
                }
                if (!leaderClan.getLeader().equals(playerUUID)) {
                    player.sendMessage(cm.getMessage("no-permission"));
                    return true;
                }
                if (args[1].equalsIgnoreCase("confirm")) {
                    String pendingTarget = pendingLeaderTransfers.remove(playerUUID);
                    if (pendingTarget == null) {
                        player.sendMessage(cm.getMessage("clan-deleted-cancelled"));
                        return true;
                    }
                    Player newLeaderPlayer = Bukkit.getPlayer(pendingTarget);
                    if (newLeaderPlayer == null) {
                        player.sendMessage(cm.getMessage("player-not-found"));
                        return true;
                    }
                    ClanData freshClan = getPlayerClan(playerUUID);
                    if (freshClan == null || !freshClan.getMembers().contains(newLeaderPlayer.getUniqueId())) {
                        player.sendMessage(cm.getPrefix() + "Spieler ist nicht im Clan.");
                        return true;
                    }
                    freshClan.getModerators().remove(newLeaderPlayer.getUniqueId());
                    if (!freshClan.getModerators().contains(playerUUID)) {
                        freshClan.getModerators().add(playerUUID);
                    }
                    freshClan.setLeader(newLeaderPlayer.getUniqueId());
                    freshClan.addLog(newLeaderPlayer.getName() + " ist jetzt der neue Leader.");
                    PlayerData oldLeaderData = plugin.getFileManager().loadPlayer(playerUUID);
                    if (oldLeaderData != null) oldLeaderData.setRole("MOD");
                    PlayerData newLeaderData = plugin.getFileManager().loadPlayer(newLeaderPlayer.getUniqueId());
                    if (newLeaderData != null) newLeaderData.setRole("LEADER");
                    try {
                        if (oldLeaderData != null) plugin.getFileManager().savePlayer(playerUUID, oldLeaderData);
                        if (newLeaderData != null) plugin.getFileManager().savePlayer(newLeaderPlayer.getUniqueId(), newLeaderData);
                        plugin.getFileManager().saveClan(freshClan);
                        player.sendMessage(cm.getPrefix() + "Du bist jetzt MOD. " + newLeaderPlayer.getName() + " ist der neue Leader.");
                        newLeaderPlayer.sendMessage(cm.getPrefix() + "Du bist jetzt der neue Leader des Clans [" + freshClan.getTag() + "]!");
                    } catch (IOException e) {
                        player.sendMessage(cm.getPrefix() + "Fehler beim Speichern.");
                    }
                    return true;
                }
                if (args[1].equalsIgnoreCase("deny")) {
                    pendingLeaderTransfers.remove(playerUUID);
                    player.sendMessage(cm.getMessage("delete-denied"));
                    return true;
                }
                Player transferTarget = Bukkit.getPlayer(args[1]);
                if (transferTarget == null) {
                    player.sendMessage(cm.getMessage("player-not-found"));
                    return true;
                }
                if (!leaderClan.getMembers().contains(transferTarget.getUniqueId())) {
                    player.sendMessage(cm.getPrefix() + "Spieler ist nicht im Clan.");
                    return true;
                }
                if (transferTarget.getUniqueId().equals(playerUUID)) {
                    player.sendMessage(cm.getPrefix() + "Du bist bereits der Leader.");
                    return true;
                }
                pendingLeaderTransfers.put(playerUUID, transferTarget.getName());
                player.sendMessage(cm.getPrefix() + "M\u00f6chtest du die F\u00fchrung an " + transferTarget.getName() + " \u00fcbergeben?");
                player.sendMessage(
                        Component.text("[Accept]")
                                .color(TextColor.color(0x55FF55))
                                .clickEvent(ClickEvent.runCommand("/clan leader confirm"))
                );
                player.sendMessage(
                        Component.text("[Deny]")
                                .color(TextColor.color(0xFF5555))
                                .clickEvent(ClickEvent.runCommand("/clan leader deny"))
                );
                break;
            }

            case "invite": {
                if (args.length < 2) {
                    player.sendMessage(cm.getPrefix() + "Verwendung: /clan invite <spieler>");
                    return true;
                }
                ClanData inviteClan = getPlayerClan(playerUUID);
                if (inviteClan == null) {
                    player.sendMessage(cm.getMessage("no-clan"));
                    return true;
                }
                PlayerData inviterData = plugin.getFileManager().loadPlayer(playerUUID);
                if (inviterData == null || (!"LEADER".equals(inviterData.getRole()) && !"MOD".equals(inviterData.getRole()))) {
                    player.sendMessage(cm.getMessage("no-permission"));
                    return true;
                }
                Long lastInvite = inviteCooldowns.get(playerUUID);
                if (lastInvite != null && System.currentTimeMillis() - lastInvite < INVITE_COOLDOWN_MS) {
                    long remSec = (INVITE_COOLDOWN_MS - (System.currentTimeMillis() - lastInvite)) / 1000 + 1;
                    player.sendMessage(cm.getPrefix() + "Bitte warte noch " + remSec + " Sekunden.");
                    return true;
                }
                if (inviteClan.getMembers().size() >= cm.getMaxMembers()) {
                    player.sendMessage(cm.getMessage("clan-full"));
                    return true;
                }
                Player invTarget = Bukkit.getPlayer(args[1]);
                if (invTarget == null) {
                    player.sendMessage(cm.getMessage("player-not-found"));
                    return true;
                }
                PlayerData invTargetData = plugin.getFileManager().loadPlayer(invTarget.getUniqueId());
                if (invTargetData == null) invTargetData = new PlayerData(invTarget.getName());
                if (invTargetData.getClanTag() != null) {
                    player.sendMessage(cm.getMessage("target-in-clan-inviter").replace("%player%", invTarget.getName()));
                    invTarget.sendMessage(cm.getMessage("target-in-clan-notify"));
                    return true;
                }
                if (!invTargetData.isInvitesEnabled()) {
                    player.sendMessage(cm.getMessage("invitations-disabled"));
                    return true;
                }
                List<InviteData> existingInvites = plugin.getFileManager().loadAllInvites(invTarget.getUniqueId());
                if (!existingInvites.isEmpty()) {
                    player.sendMessage(cm.getPrefix() + invTarget.getName() + " hat bereits eine offene Einladung.");
                    return true;
                }
                InviteData invite = new InviteData(inviteClan.getTag());
                try {
                    plugin.getFileManager().saveInvite(invTarget.getUniqueId(), invite);
                    inviteCooldowns.put(playerUUID, System.currentTimeMillis());
                    player.sendMessage(cm.getMessage("invitation-sent").replace("%player%", invTarget.getName()));
                    invTarget.sendMessage(
                            cm.getMessage("invitation-received")
                                    .replace("%inviter%", player.getName())
                                    .replace("%tag%", inviteClan.getTag())
                    );
                    invTarget.sendMessage(
                            Component.text("[Accept]")
                                    .color(TextColor.color(0x55FF55))
                                    .clickEvent(ClickEvent.runCommand("/clan join " + inviteClan.getTag()))
                                    .append(Component.text(" / ").color(TextColor.color(0xAAAAAA)))
                                    .append(Component.text("[Deny]")
                                            .color(TextColor.color(0xFF5555))
                                            .clickEvent(ClickEvent.runCommand("/clan deny " + inviteClan.getTag())))
                    );
                } catch (IOException e) {
                    player.sendMessage(cm.getPrefix() + "Fehler beim Speichern.");
                }
                break;
            }

            case "accept": {
                if (args.length < 2) {
                    player.sendMessage(cm.getPrefix() + "Verwendung: /clan accept <tag>");
                    return true;
                }
                String acceptTag = args[1];
                List<InviteData> acceptInvites = plugin.getFileManager().loadAllInvites(playerUUID);
                InviteData acceptInv = null;
                for (InviteData i : acceptInvites) {
                    if (acceptTag.equals(i.getFromClan())) { acceptInv = i; break; }
                }
                if (acceptInv == null) {
                    player.sendMessage(cm.getPrefix() + "Keine Einladung gefunden.");
                    return true;
                }
                ClanData acceptClan = plugin.getFileManager().loadClan(acceptTag);
                if (acceptClan == null) {
                    player.sendMessage(cm.getMessage("clan-not-found"));
                    return true;
                }
                PlayerData acceptPd = plugin.getFileManager().loadPlayer(playerUUID);
                if (acceptPd == null) acceptPd = new PlayerData(player.getName());
                if (acceptPd.getClanTag() != null) {
                    player.sendMessage(cm.getMessage("already-in-clan"));
                    return true;
                }
                acceptClan.getMembers().add(playerUUID);
                acceptClan.addLog(player.getName() + " ist dem Clan beigetreten.");
                acceptPd.setClanTag(acceptTag);
                acceptPd.setRole("MEMBER");
                try {
                    plugin.getFileManager().saveClan(acceptClan);
                    plugin.getFileManager().savePlayer(playerUUID, acceptPd);
                    plugin.getFileManager().deleteSpecificInvite(playerUUID, acceptTag);
                    player.sendMessage(cm.getMessage("invitation-accepted").replace("%tag%", acceptTag));
                } catch (IOException e) {
                    player.sendMessage(cm.getPrefix() + "Fehler beim Speichern.");
                }
                break;
            }

            case "deny": {
                if (args.length < 2) {
                    player.sendMessage(cm.getPrefix() + "Verwendung: /clan deny <tag>");
                    return true;
                }
                String denyTag = args[1];
                List<InviteData> denyInvites = plugin.getFileManager().loadAllInvites(playerUUID);
                boolean foundDeny = false;
                for (InviteData i : denyInvites) {
                    if (denyTag.equals(i.getFromClan())) { foundDeny = true; break; }
                }
                if (!foundDeny) {
                    player.sendMessage(cm.getPrefix() + "Keine Einladung gefunden.");
                    return true;
                }
                try {
                    plugin.getFileManager().deleteSpecificInvite(playerUUID, denyTag);
                } catch (IOException ignored) { /* continue */ }
                player.sendMessage(cm.getMessage("invitation-denied"));
                break;
            }

            case "join": {
                if (args.length < 2) {
                    player.sendMessage(cm.getPrefix() + "Verwendung: /clan join <tag>");
                    return true;
                }
                String joinTag = args[1];
                List<InviteData> joinInvites = plugin.getFileManager().loadAllInvites(playerUUID);
                InviteData joinInv = null;
                for (InviteData i : joinInvites) {
                    if (joinTag.equals(i.getFromClan())) { joinInv = i; break; }
                }
                if (joinInv == null) {
                    player.sendMessage(cm.getMessage("join-not-invited"));
                    return true;
                }
                ClanData joinClan = plugin.getFileManager().loadClan(joinTag);
                if (joinClan == null) {
                    player.sendMessage(cm.getMessage("clan-not-found"));
                    return true;
                }
                PlayerData joinPd = plugin.getFileManager().loadPlayer(playerUUID);
                if (joinPd == null) joinPd = new PlayerData(player.getName());
                if (joinPd.getClanTag() != null) {
                    if ("LEADER".equals(joinPd.getRole())) {
                        player.sendMessage(cm.getMessage("leader-cannot-join"));
                        return true;
                    }
                    ClanData currentClan = plugin.getFileManager().loadClan(joinPd.getClanTag());
                    if (currentClan != null) {
                        currentClan.getMembers().remove(playerUUID);
                        currentClan.getModerators().remove(playerUUID);
                        try { plugin.getFileManager().saveClan(currentClan); } catch (IOException ignored) { /* continue */ }
                    }
                }
                joinClan.getMembers().add(playerUUID);
                joinClan.addLog(player.getName() + " ist dem Clan beigetreten.");
                joinPd.setClanTag(joinTag);
                joinPd.setRole("MEMBER");
                try {
                    plugin.getFileManager().saveClan(joinClan);
                    plugin.getFileManager().savePlayer(playerUUID, joinPd);
                    plugin.getFileManager().deleteSpecificInvite(playerUUID, joinTag);
                    player.sendMessage(cm.getMessage("invitation-accepted").replace("%tag%", joinTag));
                } catch (IOException e) {
                    player.sendMessage(cm.getPrefix() + "Fehler beim Speichern.");
                }
                break;
            }

            case "request": {
                if (args.length < 2) {
                    player.sendMessage(cm.getPrefix() + "Verwendung: /clan request <tag>");
                    return true;
                }
                String reqTag = args[1];
                if (reqTag.equalsIgnoreCase("confirm")) {
                    String pendingReqTag = pendingLeaderRequests.remove(playerUUID);
                    if (pendingReqTag == null) {
                        player.sendMessage(cm.getMessage("clan-deleted-cancelled"));
                        return true;
                    }
                    ClanData leaderOldClan = getPlayerClan(playerUUID);
                    if (leaderOldClan != null) {
                        String oldTag = leaderOldClan.getTag();
                        for (UUID mem : new ArrayList<>(leaderOldClan.getMembers())) {
                            PlayerData pd = plugin.getFileManager().loadPlayer(mem);
                            if (pd != null) {
                                pd.setClanTag(null);
                                pd.setRole("MEMBER");
                                try { plugin.getFileManager().savePlayer(mem, pd); } catch (IOException ignored) { /* continue */ }
                            }
                        }
                        plugin.getFileManager().deleteClan(oldTag);
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.sendMessage(cm.getMessage("clan-dissolved").replace("%tag%", oldTag));
                        }
                    }
                    ClanData targetReqClan = plugin.getFileManager().loadClan(pendingReqTag);
                    if (targetReqClan == null) {
                        player.sendMessage(cm.getMessage("clan-not-found"));
                        return true;
                    }
                    if (targetReqClan.getPendingRequests().contains(playerUUID)) {
                        player.sendMessage(cm.getMessage("request-already-sent"));
                        return true;
                    }
                    targetReqClan.getPendingRequests().add(playerUUID);
                    try {
                        plugin.getFileManager().saveClan(targetReqClan);
                        player.sendMessage(cm.getMessage("request-sent").replace("%tag%", pendingReqTag));
                        notifyLeaderOfRequest(targetReqClan, player, cm);
                    } catch (IOException e) {
                        player.sendMessage(cm.getPrefix() + "Fehler beim Speichern.");
                    }
                    return true;
                }
                if (reqTag.equalsIgnoreCase("deny")) {
                    pendingLeaderRequests.remove(playerUUID);
                    player.sendMessage(cm.getMessage("delete-denied"));
                    return true;
                }
                ClanData reqClan = plugin.getFileManager().loadClan(reqTag);
                if (reqClan == null) {
                    player.sendMessage(cm.getMessage("clan-not-found"));
                    return true;
                }
                PlayerData reqPd = plugin.getFileManager().loadPlayer(playerUUID);
                if (reqPd == null) reqPd = new PlayerData(player.getName());
                if (reqTag.equals(reqPd.getClanTag())) {
                    player.sendMessage(cm.getMessage("already-in-clan"));
                    return true;
                }
                if (reqPd.getClanTag() != null && "LEADER".equals(reqPd.getRole())) {
                    pendingLeaderRequests.put(playerUUID, reqTag);
                    player.sendMessage(cm.getMessage("leader-request-confirm"));
                    player.sendMessage(
                            Component.text("[Accept]")
                                    .color(TextColor.color(0x55FF55))
                                    .clickEvent(ClickEvent.runCommand("/clan request confirm"))
                    );
                    player.sendMessage(
                            Component.text("[Deny]")
                                    .color(TextColor.color(0xFF5555))
                                    .clickEvent(ClickEvent.runCommand("/clan request deny"))
                    );
                    return true;
                }
                if (reqPd.getClanTag() != null) {
                    player.sendMessage(cm.getMessage("already-in-clan"));
                    return true;
                }
                if (reqClan.getPendingRequests().contains(playerUUID)) {
                    player.sendMessage(cm.getMessage("request-already-sent"));
                    return true;
                }
                reqClan.getPendingRequests().add(playerUUID);
                try {
                    plugin.getFileManager().saveClan(reqClan);
                    player.sendMessage(cm.getMessage("request-sent").replace("%tag%", reqTag));
                    notifyLeaderOfRequest(reqClan, player, cm);
                } catch (IOException e) {
                    player.sendMessage(cm.getPrefix() + "Fehler beim Speichern.");
                }
                break;
            }

            case "requests": {
                ClanData reqsClan = getPlayerClan(playerUUID);
                if (reqsClan == null) {
                    player.sendMessage(cm.getMessage("no-clan"));
                    return true;
                }
                if (!reqsClan.getLeader().equals(playerUUID)) {
                    player.sendMessage(cm.getMessage("requests-only-leader"));
                    return true;
                }
                List<UUID> pendingReqs = reqsClan.getPendingRequests();
                if (pendingReqs.isEmpty()) {
                    player.sendMessage(cm.getMessage("requests-empty"));
                    return true;
                }
                player.sendMessage(cm.getPrefix() + "Ausstehende Beitrittsanfragen:");
                for (UUID reqUUID : new ArrayList<>(pendingReqs)) {
                    String reqName = Bukkit.getOfflinePlayer(reqUUID).getName();
                    if (reqName == null) reqName = reqUUID.toString().substring(0, 8);
                    player.sendMessage(
                            Component.text(cm.getPrefix() + reqName + " ")
                                    .append(Component.text("[Accept]")
                                            .color(TextColor.color(0x55FF55))
                                            .clickEvent(ClickEvent.runCommand("/clan accept-request " + reqUUID)))
                    );
                }
                break;
            }

            case "accept-request": {
                if (args.length < 2) {
                    player.sendMessage(cm.getPrefix() + "Verwendung: /clan accept-request <uuid>");
                    return true;
                }
                ClanData arClan = getPlayerClan(playerUUID);
                if (arClan == null) {
                    player.sendMessage(cm.getMessage("no-clan"));
                    return true;
                }
                if (!arClan.getLeader().equals(playerUUID)) {
                    player.sendMessage(cm.getMessage("no-permission"));
                    return true;
                }
                UUID arUUID;
                try {
                    arUUID = UUID.fromString(args[1]);
                } catch (IllegalArgumentException e) {
                    player.sendMessage(cm.getPrefix() + "Ung\u00fcltige UUID.");
                    return true;
                }
                if (!arClan.getPendingRequests().contains(arUUID)) {
                    player.sendMessage(cm.getPrefix() + "Keine Anfrage von diesem Spieler gefunden.");
                    return true;
                }
                if (arClan.getMembers().size() >= cm.getMaxMembers()) {
                    player.sendMessage(cm.getMessage("clan-full"));
                    return true;
                }
                arClan.getPendingRequests().remove(arUUID);
                arClan.getMembers().add(arUUID);
                String arPlayerName = Bukkit.getOfflinePlayer(arUUID).getName();
                if (arPlayerName == null) arPlayerName = arUUID.toString().substring(0, 8);
                arClan.addLog(arPlayerName + " ist dem Clan beigetreten (Request).");
                PlayerData arPd = plugin.getFileManager().loadPlayer(arUUID);
                if (arPd == null) arPd = new PlayerData(arPlayerName);
                arPd.setClanTag(arClan.getTag());
                arPd.setRole("MEMBER");
                try {
                    plugin.getFileManager().saveClan(arClan);
                    plugin.getFileManager().savePlayer(arUUID, arPd);
                    player.sendMessage(cm.getPrefix() + arPlayerName + " wurde dem Clan hinzugef\u00fcgt.");
                    Player arOnline = Bukkit.getPlayer(arUUID);
                    if (arOnline != null) {
                        arOnline.sendMessage(cm.getMessage("invitation-accepted").replace("%tag%", arClan.getTag()));
                    }
                } catch (IOException e) {
                    player.sendMessage(cm.getPrefix() + "Fehler beim Speichern.");
                }
                break;
            }

            case "deny-request": {
                if (args.length < 2) {
                    player.sendMessage(cm.getPrefix() + "Verwendung: /clan deny-request <uuid>");
                    return true;
                }
                ClanData drClan = getPlayerClan(playerUUID);
                if (drClan == null) {
                    player.sendMessage(cm.getMessage("no-clan"));
                    return true;
                }
                if (!drClan.getLeader().equals(playerUUID)) {
                    player.sendMessage(cm.getMessage("no-permission"));
                    return true;
                }
                UUID drUUID;
                try {
                    drUUID = UUID.fromString(args[1]);
                } catch (IllegalArgumentException e) {
                    player.sendMessage(cm.getPrefix() + "Ung\u00fcltige UUID.");
                    return true;
                }
                if (!drClan.getPendingRequests().contains(drUUID)) {
                    player.sendMessage(cm.getPrefix() + "Keine Anfrage von diesem Spieler gefunden.");
                    return true;
                }
                drClan.getPendingRequests().remove(drUUID);
                try {
                    plugin.getFileManager().saveClan(drClan);
                    player.sendMessage(cm.getPrefix() + "Anfrage abgelehnt.");
                    Player drOnline = Bukkit.getPlayer(drUUID);
                    if (drOnline != null) {
                        drOnline.sendMessage(cm.getPrefix() + "Deine Beitrittsanfrage wurde abgelehnt.");
                    }
                } catch (IOException e) {
                    player.sendMessage(cm.getPrefix() + "Fehler beim Speichern.");
                }
                break;
            }

            case "rename": {
                if (args.length < 2) {
                    player.sendMessage(cm.getPrefix() + "Verwendung: /clan rename <neuerTag>");
                    return true;
                }
                ClanData renameClan = getPlayerClan(playerUUID);
                if (renameClan == null) {
                    player.sendMessage(cm.getMessage("no-clan"));
                    return true;
                }
                if (!renameClan.getLeader().equals(playerUUID)) {
                    player.sendMessage(cm.getMessage("no-permission"));
                    return true;
                }
                String newTag = args[1];
                boolean isVipRename = player.hasPermission("clan.vip");
                TagValidator.ValidationResult renameResult = new TagValidator(plugin).validate(newTag, isVipRename);
                if (!renameResult.isValid()) {
                    player.sendMessage(renameResult.getErrorMessage());
                    return true;
                }
                if (plugin.getFileManager().loadClan(newTag) != null) {
                    player.sendMessage(cm.getMessage("clan-exists"));
                    return true;
                }
                for (UUID mem : renameClan.getMembers()) {
                    PlayerData md = plugin.getFileManager().loadPlayer(mem);
                    if (md != null) {
                        md.setClanTag(newTag);
                        try { plugin.getFileManager().savePlayer(mem, md); } catch (IOException ignored) { /* continue */ }
                    }
                }
                plugin.getFileManager().deleteClan(renameClan.getTag());
                renameClan.setTag(newTag);
                try {
                    plugin.getFileManager().saveClan(renameClan);
                    player.sendMessage(cm.getMessage("renamed").replace("%tag%", newTag));
                } catch (IOException e) {
                    player.sendMessage(cm.getPrefix() + "Fehler beim Speichern.");
                }
                break;
            }

            case "info": {
                ClanData infoClan = getPlayerClan(playerUUID);
                if (infoClan == null) {
                    player.sendMessage(cm.getMessage("no-clan"));
                    return true;
                }
                String prefix = cm.getPrefix();
                player.sendMessage(cm.getMessage("clan-info-header"));
                player.sendMessage(prefix + "Tag: " + infoClan.getTag());
                player.sendMessage(prefix + "Punkte: " + infoClan.getPoints());
                player.sendMessage(prefix + "Rang: " + infoClan.getRank());
                player.sendMessage(prefix + "Mitglieder: " + infoClan.getMembers().size() + "/" + cm.getMaxMembers());
                player.sendMessage(prefix + "Erstelldatum: " + infoClan.getCreated());
                player.sendMessage(prefix + "Online-Zeit: " + String.format("%.1f", infoClan.getOnlineTime()) + "h");
                player.sendMessage(prefix + "Leader: " + Bukkit.getOfflinePlayer(infoClan.getLeader()).getName());
                String modsStr = infoClan.getModerators().isEmpty() ? "Keine"
                        : infoClan.getModerators().stream()
                        .map(u -> Bukkit.getOfflinePlayer(u).getName())
                        .filter(Objects::nonNull)
                        .reduce((a, b) -> a + ", " + b).orElse("Keine");
                player.sendMessage(prefix + "Moderatoren: " + modsStr);
                player.sendMessage(prefix + "Mitgliederliste:");
                for (UUID mem : infoClan.getMembers()) {
                    String memName = Bukkit.getOfflinePlayer(mem).getName();
                    PlayerData md = plugin.getFileManager().loadPlayer(mem);
                    String role = md != null ? md.getRole() : "MEMBER";
                    String online = Bukkit.getPlayer(mem) != null ? "online" : "offline";
                    double time = md != null ? md.getOnlineTime() : 0.0;
                    player.sendMessage(prefix + "  - " + memName + " (" + role + ") " + online + " (" + String.format("%.1f", time) + "h)");
                }
                break;
            }

            case "toggle": {
                boolean toggled = plugin.toggleInvitation(playerUUID);
                player.sendMessage(toggled ? cm.getMessage("toggle-off") : cm.getMessage("toggle-on"));
                PlayerData ptData = plugin.getFileManager().loadPlayer(playerUUID);
                if (ptData != null) {
                    ptData.setInvitesEnabled(!toggled);
                    try { plugin.getFileManager().savePlayer(playerUUID, ptData); } catch (IOException ignored) { /* continue */ }
                }
                break;
            }

            case "stats": {
                ClanData statsClan = getPlayerClan(playerUUID);
                if (statsClan == null) {
                    player.sendMessage(cm.getMessage("no-clan"));
                    return true;
                }
                player.sendMessage(cm.getPrefix() + "Clan: " + statsClan.getTag()
                        + " | Punkte: " + statsClan.getPoints()
                        + " | Rang: " + statsClan.getRank()
                        + " | Mitglieder: " + statsClan.getMembers().size());
                break;
            }

            case "ranking": {
                Map<String, ClanData> allClans = plugin.getFileManager().loadAllClans();
                List<String> toDelete = new ArrayList<>();
                for (Map.Entry<String, ClanData> entry : allClans.entrySet()) {
                    ClanData cd = entry.getValue();
                    if (cd.getMembers().isEmpty() || cd.getLeader() == null) {
                        toDelete.add(entry.getKey());
                    }
                }
                for (String delTag : toDelete) {
                    ClanData deadClan = allClans.remove(delTag);
                    if (deadClan != null) {
                        for (UUID mem : new ArrayList<>(deadClan.getMembers())) {
                            PlayerData pd = plugin.getFileManager().loadPlayer(mem);
                            if (pd != null) {
                                pd.setClanTag(null);
                                pd.setRole("MEMBER");
                                try { plugin.getFileManager().savePlayer(mem, pd); } catch (IOException ignored) { /* continue */ }
                            }
                        }
                        plugin.getFileManager().deleteClan(delTag);
                    }
                }
                List<Map.Entry<String, ClanData>> sorted = new ArrayList<>(allClans.entrySet());
                sorted.sort((a, b) -> Integer.compare(b.getValue().getPoints(), a.getValue().getPoints()));
                player.sendMessage(cm.getPrefix() + "Clan-Ranking:");
                int rankNum = 1;
                for (Map.Entry<String, ClanData> entry : sorted) {
                    ClanData cd = entry.getValue();
                    String leaderName = Bukkit.getOfflinePlayer(cd.getLeader()).getName();
                    if (leaderName == null) leaderName = "?";
                    player.sendMessage("#" + rankNum + ". [" + cd.getTag() + "] Leader: " + leaderName
                            + " | Members: " + cd.getMembers().size()
                            + " | Punkte: " + cd.getPoints()
                            + " [" + cd.getRank() + "]");
                    rankNum++;
                    if (rankNum > 10) break;
                }
                break;
            }

            case "chest": {
                ClanData chestClan = getPlayerClan(playerUUID);
                if (chestClan == null) {
                    player.sendMessage(cm.getMessage("no-clan"));
                    return true;
                }
                if (!chestClan.getLeader().equals(playerUUID)) {
                    ClanChestPermission permission = chestClan.getChestPermission(playerUUID);
                    if (permission != ClanChestPermission.EXECUTE) {
                        player.sendMessage(cm.getMessage("leader-disallow"));
                        return true;
                    }
                }
                Inventory chest = clanChests.get(chestClan.getTag());
                if (chest == null) {
                    chest = Bukkit.createInventory(null, 27, "Clan-Kiste: " + chestClan.getTag());
                    clanChests.put(chestClan.getTag(), chest);
                }
                player.openInventory(chest);
                break;
            }

            case "settings": {
                ClanData settingsClan = getPlayerClan(playerUUID);
                if (settingsClan == null) {
                    player.sendMessage(cm.getMessage("no-clan"));
                    return true;
                }
                if (!settingsClan.getLeader().equals(playerUUID)) {
                    player.sendMessage(cm.getMessage("not-clan-leader"));
                    return true;
                }
                plugin.getClanSettingsListener().openGui(player, settingsClan);
                break;
            }

            case "spawn": {
                ClanData spawnClan = getPlayerClan(playerUUID);
                if (spawnClan == null) {
                    player.sendMessage(cm.getMessage("no-clan"));
                    return true;
                }
                if (spawnClan.getSpawn() == null) {
                    player.sendMessage(cm.getPrefix() + "Clan-Spawn nicht gesetzt.");
                    return true;
                }
                long now = System.currentTimeMillis();
                if (spawnCooldowns.containsKey(playerUUID) && now < spawnCooldowns.get(playerUUID)) {
                    long remaining = (spawnCooldowns.get(playerUUID) - now) / 1000 + 1;
                    player.sendActionBar(cm.translateColors("&#FF0000Clan-Spawn Cooldown: " + remaining + "s"));
                    return true;
                }
                player.teleport(spawnClan.getSpawn());
                player.sendMessage(cm.getPrefix() + "Teleportiert zum Clan-Spawn.");
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
                        player.sendActionBar(cm.translateColors("&#FF0000Clan-Spawn Cooldown: " + rem + "s"));
                    }
                }, 0L, 20L).getTaskId();
                spawnTaskIds.put(playerUUID, taskId);
                break;
            }

            case "setspawn": {
                ClanData ssClan = getPlayerClan(playerUUID);
                if (ssClan == null) {
                    player.sendMessage(cm.getMessage("no-clan"));
                    return true;
                }
                if (!ssClan.getLeader().equals(playerUUID)) {
                    player.sendMessage(cm.getMessage("no-permission"));
                    return true;
                }
                ssClan.setSpawn(player.getLocation());
                try {
                    plugin.getFileManager().saveClan(ssClan);
                    player.sendMessage(cm.getPrefix() + "Clan-Spawn gesetzt.");
                } catch (IOException e) {
                    player.sendMessage(cm.getPrefix() + "Fehler beim Speichern.");
                }
                break;
            }

            case "logs": {
                ClanData logClan = getPlayerClan(playerUUID);
                if (logClan == null) {
                    player.sendMessage(cm.getMessage("no-clan"));
                    return true;
                }
                List<String> logEntries = logClan.getLogs();
                if (logEntries.isEmpty()) {
                    player.sendMessage(cm.getPrefix() + "Keine Log-Eintr\u00e4ge vorhanden.");
                    return true;
                }
                ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
                BookMeta bookMeta = (BookMeta) book.getItemMeta();
                if (bookMeta == null) break;
                bookMeta.setTitle("Clan Logs");
                bookMeta.setAuthor(logClan.getTag());
                List<String> allLogs = new ArrayList<>(logEntries);
                Collections.reverse(allLogs);
                List<Component> pages = new ArrayList<>();
                int linesPerPage = 14;
                int maxPages = 50;
                int lineIdx = 0;
                int pageCount = 0;
                while (lineIdx < allLogs.size() && pageCount < maxPages) {
                    StringBuilder page = new StringBuilder();
                    for (int l = 0; l < linesPerPage && lineIdx < allLogs.size(); l++, lineIdx++) {
                        page.append(allLogs.get(lineIdx)).append("\n");
                    }
                    pages.add(Component.text(page.toString()));
                    pageCount++;
                }
                bookMeta.pages(pages);
                book.setItemMeta(bookMeta);
                player.openBook(book);
                break;
            }

            case "skills": {
                Inventory skillsGui = Bukkit.createInventory(null, 54, "Clan Skills");
                ItemStack grayPane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta grayMeta = grayPane.getItemMeta();
                if (grayMeta != null) { grayMeta.setDisplayName(" "); grayPane.setItemMeta(grayMeta); }
                ItemStack greenPane = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
                ItemMeta greenMeta = greenPane.getItemMeta();
                if (greenMeta != null) { greenMeta.setDisplayName(" "); greenPane.setItemMeta(greenMeta); }
                for (int col = 0; col < 9; col++) {
                    skillsGui.setItem(col, grayPane.clone());
                    skillsGui.setItem(45 + col, grayPane.clone());
                }
                for (int row = 1; row <= 4; row++) {
                    for (int col = 0; col < 9; col++) {
                        skillsGui.setItem(row * 9 + col, greenPane.clone());
                    }
                }
                player.openInventory(skillsGui);
                break;
            }

            case "war": {
                handleWarCommand(player, playerUUID, args, cm);
                break;
            }

            case "force": {
                if (!player.hasPermission("clan.admin")) {
                    player.sendMessage(cm.getMessage("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(cm.getPrefix() + "Verwendung: /clan force kick <spieler> | /clan force delete <tag>");
                    return true;
                }
                if (args[1].equalsIgnoreCase("kick") || args[1].equalsIgnoreCase("leave")) {
                    if (args.length < 3) {
                        player.sendMessage(cm.getPrefix() + "Verwendung: /clan force kick <spieler>");
                        return true;
                    }
                    String fkArg = args[2];
                    if (fkArg.equalsIgnoreCase("confirm")) {
                        String pendingTarget = pendingForceKicks.remove(playerUUID);
                        if (pendingTarget == null) {
                            player.sendMessage(cm.getMessage("clan-deleted-cancelled"));
                            return true;
                        }
                        Player forceTarget = Bukkit.getPlayer(pendingTarget);
                        if (forceTarget == null) {
                            player.sendMessage(cm.getMessage("player-not-found"));
                            return true;
                        }
                        ClanData ftClan = getPlayerClan(forceTarget.getUniqueId());
                        if (ftClan == null) {
                            player.sendMessage(cm.getMessage("force-leave-no-clan"));
                            return true;
                        }
                        ftClan.getMembers().remove(forceTarget.getUniqueId());
                        ftClan.getModerators().remove(forceTarget.getUniqueId());
                        PlayerData ftData = plugin.getFileManager().loadPlayer(forceTarget.getUniqueId());
                        if (ftData != null) {
                            ftData.setClanTag(null);
                            ftData.setRole("MEMBER");
                            try {
                                plugin.getFileManager().savePlayer(forceTarget.getUniqueId(), ftData);
                                plugin.getFileManager().saveClan(ftClan);
                            } catch (IOException ignored) { /* continue */ }
                        }
                        player.sendMessage(cm.getMessage("force-leave-success").replace("%player%", forceTarget.getName()));
                        forceTarget.sendMessage(cm.getMessage("left-clan"));
                    } else if (fkArg.equalsIgnoreCase("deny")) {
                        pendingForceKicks.remove(playerUUID);
                        player.sendMessage(cm.getMessage("delete-denied"));
                    } else {
                        Player forceTarget = Bukkit.getPlayer(fkArg);
                        if (forceTarget == null) {
                            player.sendMessage(cm.getMessage("player-not-found"));
                            return true;
                        }
                        ClanData ftClan = getPlayerClan(forceTarget.getUniqueId());
                        if (ftClan == null) {
                            player.sendMessage(cm.getMessage("force-leave-no-clan"));
                            return true;
                        }
                        pendingForceKicks.put(playerUUID, fkArg);
                        player.sendMessage(
                                Component.text(cm.getMessage("force-kick-confirm").replace("%player%", forceTarget.getName()) + " ")
                                        .append(Component.text("[accept]")
                                                .color(TextColor.color(0x55FF55))
                                                .clickEvent(ClickEvent.runCommand("/clan force kick confirm")))
                                        .append(Component.text(" / ").color(TextColor.color(0xAAAAAA)))
                                        .append(Component.text("[Deny]")
                                                .color(TextColor.color(0xFF5555))
                                                .clickEvent(ClickEvent.runCommand("/clan force kick deny")))
                        );
                    }
                } else if (args[1].equalsIgnoreCase("delete")) {
                    if (args.length < 3) {
                        player.sendMessage(cm.getPrefix() + "Verwendung: /clan force delete <tag>");
                        return true;
                    }
                    String fdArg = args[2];
                    if (fdArg.equalsIgnoreCase("confirm")) {
                        String pendingTag = pendingForceDeletes.remove(playerUUID);
                        if (pendingTag == null) {
                            player.sendMessage(cm.getMessage("clan-deleted-cancelled"));
                            return true;
                        }
                        ClanData fdClan = plugin.getFileManager().loadClan(pendingTag);
                        if (fdClan == null) {
                            player.sendMessage(cm.getMessage("clan-not-found"));
                            return true;
                        }
                        for (UUID mem : new ArrayList<>(fdClan.getMembers())) {
                            PlayerData pd = plugin.getFileManager().loadPlayer(mem);
                            if (pd != null) {
                                pd.setClanTag(null);
                                pd.setRole("MEMBER");
                                try { plugin.getFileManager().savePlayer(mem, pd); } catch (IOException ignored) { /* continue */ }
                            }
                        }
                        plugin.getFileManager().deleteClan(pendingTag);
                        player.sendMessage(cm.getMessage("clan-deleted"));
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.sendMessage(cm.getMessage("clan-dissolved").replace("%tag%", pendingTag));
                        }
                    } else if (fdArg.equalsIgnoreCase("deny")) {
                        pendingForceDeletes.remove(playerUUID);
                        player.sendMessage(cm.getMessage("delete-denied"));
                    } else {
                        ClanData fdClan = plugin.getFileManager().loadClan(fdArg);
                        if (fdClan == null) {
                            player.sendMessage(cm.getMessage("clan-not-found"));
                            return true;
                        }
                        pendingForceDeletes.put(playerUUID, fdArg);
                        player.sendMessage(
                                Component.text(cm.getMessage("force-delete-confirm").replace("%tag%", fdArg) + " ")
                                        .append(Component.text("[accept]")
                                                .color(TextColor.color(0x55FF55))
                                                .clickEvent(ClickEvent.runCommand("/clan force delete confirm")))
                                        .append(Component.text(" / ").color(TextColor.color(0xAAAAAA)))
                                        .append(Component.text("[Deny]")
                                                .color(TextColor.color(0xFF5555))
                                                .clickEvent(ClickEvent.runCommand("/clan force delete deny")))
                        );
                    }
                } else {
                    player.sendMessage(cm.getPrefix() + "Verwendung: /clan force kick <spieler> | /clan force delete <tag>");
                }
                break;
            }

            case "admin": {
                if (!player.hasPermission("clan.admin")) {
                    player.sendMessage(cm.getMessage("no-permission"));
                    return true;
                }
                player.sendMessage(cm.translateColors(cm.getPrefix() + "&cAdmin-Befehle:"));
                player.sendMessage(cm.translateColors(cm.getPrefix() + "/clan force kick <spieler> &7- Spieler aus Clan entfernen"));
                player.sendMessage(cm.translateColors(cm.getPrefix() + "/clan force delete <tag> &7- Clan aufl\u00f6sen"));
                player.sendMessage(cm.translateColors(cm.getPrefix() + "/clan war setarena a/b &7- Kriegs-Arena setzen"));
                break;
            }

            default: {
                player.sendMessage(cm.getPrefix() + "Unbekannter Subcommand. Nutze /clan f\u00fcr Hilfe.");
                break;
            }
        }

        return true;
    }

    // --- War sub-command handler ---

    private void handleWarCommand(Player player, UUID playerUUID, String[] args, ConfigManager cm) {
        WarManager warManager = plugin.getWarManager();

        if (args.length < 2) {
            player.sendMessage(cm.getPrefix() + "Verwendung: /clan war <info|<clanTag>|accept|deny|setarena>");
            return;
        }

        String warSub = args[1].toLowerCase();

        switch (warSub) {

            case "info": {
                player.sendMessage(cm.getPrefix() + "Krieg-Info:");
                player.sendMessage(cm.getPrefix() + "Kosten: " + cm.getWarCost() + " Punkte");
                player.sendMessage(cm.getPrefix() + "Cooldown: " + cm.getWarCooldownMinutes() + " Minuten");
                ClanData myClan = getPlayerClan(playerUUID);
                if (myClan != null && warManager.isAtWar(myClan.getTag())) {
                    player.sendMessage(cm.getPrefix() + "Dein Clan ist im Krieg!");
                } else {
                    player.sendMessage(cm.getPrefix() + "Dein Clan ist im Frieden.");
                }
                break;
            }

            case "accept": {
                if (args.length < 3) {
                    player.sendMessage(cm.getPrefix() + "Verwendung: /clan war accept <challengerTag>");
                    return;
                }
                String challengerTag = args[2];
                ClanData myClan = getPlayerClan(playerUUID);
                if (myClan == null) { player.sendMessage(cm.getMessage("no-clan")); return; }
                if (!myClan.getLeader().equals(playerUUID)) { player.sendMessage(cm.getMessage("no-permission")); return; }
                WarManager.WarRequest warReq = warManager.getWarRequestByChallenger(challengerTag);
                if (warReq == null || !warReq.getTargetTag().equals(myClan.getTag())) {
                    player.sendMessage(cm.getPrefix() + "Keine Kriegsanfrage von " + challengerTag + " gefunden.");
                    return;
                }
                warManager.removeWarRequest(challengerTag);
                warManager.setWarCooldown(warReq.getChallengerLeaderUUID());
                Player challengerLeader = Bukkit.getPlayer(warReq.getChallengerLeaderUUID());
                if (challengerLeader != null) {
                    challengerLeader.sendMessage(cm.getMessage("war-accepted-notice"));
                    challengerLeader.sendMessage(cm.getMessage("war-teleport-notice"));
                }
                ClanData challengerClan = plugin.getFileManager().loadClan(challengerTag);
                if (challengerClan != null && challengerLeader != null) {
                    plugin.getWarTeamSelectionListener().openGui(challengerLeader, challengerTag, challengerClan.getMembers());
                }
                plugin.getWarTeamSelectionListener().openGui(player, myClan.getTag(), myClan.getMembers());
                player.sendMessage(cm.getMessage("war-accepted-notice"));
                Location spawnA = arenaSpawns.get("a");
                Location spawnB = arenaSpawns.get("b");
                if (spawnA == null || spawnB == null) {
                    player.sendMessage(cm.getPrefix() + "Krieg-Arena nicht gesetzt! Nutze /clan war setarena a/b");
                    return;
                }
                List<UUID> teamA = challengerClan != null ? new ArrayList<>(challengerClan.getMembers()) : new ArrayList<>();
                List<UUID> teamB = new ArrayList<>(myClan.getMembers());
                warManager.startWar(challengerTag, teamA, myClan.getTag(), teamB, spawnA, spawnB);
                break;
            }

            case "deny": {
                if (args.length < 3) {
                    player.sendMessage(cm.getPrefix() + "Verwendung: /clan war deny <challengerTag>");
                    return;
                }
                String challengerTag = args[2];
                ClanData myClan = getPlayerClan(playerUUID);
                if (myClan == null) { player.sendMessage(cm.getMessage("no-clan")); return; }
                if (!myClan.getLeader().equals(playerUUID)) { player.sendMessage(cm.getMessage("no-permission")); return; }
                WarManager.WarRequest warReq = warManager.getWarRequestByChallenger(challengerTag);
                if (warReq == null || !warReq.getTargetTag().equals(myClan.getTag())) {
                    player.sendMessage(cm.getPrefix() + "Keine Kriegsanfrage von " + challengerTag + " gefunden.");
                    return;
                }
                warManager.removeWarRequest(challengerTag);
                player.sendMessage(cm.getMessage("war-denied-self"));
                Player challengerLeader = Bukkit.getPlayer(warReq.getChallengerLeaderUUID());
                if (challengerLeader != null) {
                    challengerLeader.sendMessage(cm.getMessage("war-denied-challenger"));
                }
                break;
            }

            case "setarena": {
                if (!player.hasPermission("clan.admin")) {
                    player.sendMessage(cm.getMessage("no-permission"));
                    return;
                }
                if (args.length < 3 || (!args[2].equalsIgnoreCase("a") && !args[2].equalsIgnoreCase("b"))) {
                    player.sendMessage(cm.getPrefix() + "Verwendung: /clan war setarena <a|b>");
                    return;
                }
                String spawnKey = args[2].toLowerCase();
                arenaSpawns.put(spawnKey, player.getLocation());
                player.sendMessage(cm.getPrefix() + "Arena-Spawn " + spawnKey.toUpperCase() + " gesetzt.");
                break;
            }

            default: {
                String targetTag = args[1];
                ClanData myClan = getPlayerClan(playerUUID);
                if (myClan == null) { player.sendMessage(cm.getMessage("no-clan")); return; }
                if (!myClan.getLeader().equals(playerUUID)) { player.sendMessage(cm.getMessage("no-permission")); return; }
                if (targetTag.equalsIgnoreCase(myClan.getTag())) {
                    player.sendMessage(cm.getPrefix() + "Du kannst deinen eigenen Clan nicht herausfordern.");
                    return;
                }
                ClanData targetClan = plugin.getFileManager().loadClan(targetTag);
                if (targetClan == null) { player.sendMessage(cm.getMessage("clan-not-found")); return; }
                if (warManager.isAtWar(myClan.getTag())) {
                    player.sendMessage(cm.getPrefix() + "Dein Clan ist bereits im Krieg.");
                    return;
                }
                if (warManager.isAtWar(targetTag)) {
                    player.sendMessage(cm.getPrefix() + "Der Ziel-Clan ist bereits im Krieg.");
                    return;
                }
                if (warManager.isOnWarCooldown(playerUUID)) {
                    long remMin = warManager.getRemainingCooldownMinutes(playerUUID);
                    player.sendMessage(cm.getPrefix() + "Kriegs-Cooldown: noch " + remMin + " Minuten.");
                    return;
                }
                if (warManager.getWarRequestByChallenger(myClan.getTag()) != null) {
                    player.sendMessage(cm.getPrefix() + "Du hast bereits eine offene Kriegsanfrage.");
                    return;
                }
                int cost = cm.getWarCost();
                if (myClan.getPoints() < cost) {
                    player.sendMessage(cm.getPrefix() + "Du brauchst mindestens " + cost + " Punkte, um einen Krieg zu starten.");
                    return;
                }
                myClan.setPoints(myClan.getPoints() - cost);
                try { plugin.getFileManager().saveClan(myClan); } catch (IOException ignored) { /* continue */ }
                warManager.addWarRequest(myClan.getTag(), targetTag, playerUUID);
                player.sendMessage(cm.getMessage("war-request-sent").replace("%tag%", targetTag));
                Player targetLeader = Bukkit.getPlayer(targetClan.getLeader());
                if (targetLeader != null) {
                    targetLeader.sendMessage(
                            Component.text(cm.getMessage("war-request-received").replace("%player%", player.getName()) + " ")
                                    .append(Component.text("[Accept]")
                                            .color(TextColor.color(0x55FF55))
                                            .clickEvent(ClickEvent.runCommand("/clan war accept " + myClan.getTag())))
                                    .append(Component.text(" / ").color(TextColor.color(0xAAAAAA)))
                                    .append(Component.text("[Deny]")
                                            .color(TextColor.color(0xFF5555))
                                            .clickEvent(ClickEvent.runCommand("/clan war deny " + myClan.getTag())))
                    );
                }
                break;
            }
        }
    }

    // --- Helper methods ---

    private void notifyLeaderOfRequest(ClanData clan, Player requester, ConfigManager cm) {
        Player leaderOnline = Bukkit.getPlayer(clan.getLeader());
        if (leaderOnline != null) {
            leaderOnline.sendMessage(
                    Component.text(cm.getMessage("request-received").replace("%player%", requester.getName()) + " ")
                            .append(Component.text("[zeig Notification]")
                                    .color(TextColor.color(0x55FFFF))
                                    .clickEvent(ClickEvent.runCommand("/clan requests")))
            );
        }
    }

    private void sendHelp(Player player, ConfigManager cm) {
        ClanData clan = getPlayerClan(player.getUniqueId());
        UUID playerUUID = player.getUniqueId();
        player.sendMessage(cm.translateColors(cm.getPrefix() + "/clan create <tag> &7- Clan erstellen"));
        player.sendMessage(cm.translateColors(cm.getPrefix() + "/clan delete &7- Clan aufl\u00f6sen"));
        player.sendMessage(cm.translateColors(cm.getPrefix() + "/clan invite <spieler> &7- Spieler einladen"));
        player.sendMessage(cm.translateColors(cm.getPrefix() + "/clan accept <tag> &7- Einladung annehmen"));
        player.sendMessage(cm.translateColors(cm.getPrefix() + "/clan deny <tag> &7- Einladung ablehnen"));
        player.sendMessage(cm.translateColors(cm.getPrefix() + "/clan join <tag> &7- Clan beitreten (via Einladung)"));
        player.sendMessage(cm.translateColors(cm.getPrefix() + "/clan leave &7- Clan verlassen"));
        player.sendMessage(cm.translateColors(cm.getPrefix() + "/clan kick <spieler> &7- Spieler kicken"));
        player.sendMessage(cm.translateColors(cm.getPrefix() + "/clan promote <spieler> &7- Spieler bef\u00f6rdern"));
        player.sendMessage(cm.translateColors(cm.getPrefix() + "/clan demote [spieler] &7- Spieler degradieren"));
        player.sendMessage(cm.translateColors(cm.getPrefix() + "/clan leader <spieler> &7- F\u00fchrung \u00fcbergeben"));
        player.sendMessage(cm.translateColors(cm.getPrefix() + "/clan rename <neuerTag> &7- Clan umbenennen"));
        player.sendMessage(cm.translateColors(cm.getPrefix() + "/clan info &7- Clan-Info"));
        player.sendMessage(cm.translateColors(cm.getPrefix() + "/clan toggle &7- Einladungen ein-/ausschalten"));
        player.sendMessage(cm.translateColors(cm.getPrefix() + "/clan stats &7- Clan-Stats"));
        player.sendMessage(cm.translateColors(cm.getPrefix() + "/clan ranking &7- Clan-Ranking"));
        if (clan == null || clan.getLeader().equals(playerUUID) || clan.getChestPermission(playerUUID) != ClanChestPermission.DENY) {
            player.sendMessage(cm.translateColors(cm.getPrefix() + "/clan chest &7- Clan-Kiste \u00f6ffnen"));
        }
        player.sendMessage(cm.translateColors(cm.getPrefix() + "/clan spawn &7- Zum Clan-Spawn teleportieren"));
        player.sendMessage(cm.translateColors(cm.getPrefix() + "/clan setspawn &7- Clan-Spawn setzen"));
        player.sendMessage(cm.translateColors(cm.getPrefix() + "/clan request <tag> &7- Beitrittsanfrage senden"));
        player.sendMessage(cm.translateColors(cm.getPrefix() + "/clan requests &7- Beitrittsanfragen einsehen (Leader)"));
        player.sendMessage(cm.translateColors(cm.getPrefix() + "/clan logs &7- Clan-Logs anzeigen"));
        player.sendMessage(cm.translateColors(cm.getPrefix() + "/clan skills &7- Clan-Skills \u00f6ffnen"));
        if (clan != null && clan.getLeader().equals(playerUUID)) {
            player.sendMessage(cm.translateColors(cm.getPrefix() + "/clan settings &7- Clan-Einstellungen \u00f6ffnen"));
        }
        player.sendMessage(cm.translateColors(cm.getPrefix() + "/clan war <tag> &7- Clan zum Krieg herausfordern"));
        if (player.hasPermission("clan.admin")) {
            player.sendMessage(cm.translateColors(cm.getPrefix() + "&7Admin: &f/clan admin &7f\u00fcr Admin-Befehle"));
        }
    }

    private ClanData getPlayerClan(UUID playerUUID) {
        PlayerData p = plugin.getFileManager().loadPlayer(playerUUID);
        if (p == null || p.getClanTag() == null) return null;
        return plugin.getFileManager().loadClan(p.getClanTag());
    }

    // --- Tab completion ---

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return null;
        Player player = (Player) sender;
        UUID playerUUID = player.getUniqueId();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList(
                    "create", "disband", "delete", "invite", "accept", "deny", "join", "leave",
                    "kick", "promote", "demote", "leader", "rename", "info", "toggle", "stats",
                    "ranking", "chest", "spawn", "setspawn", "request", "requests",
                    "accept-request", "deny-request", "logs", "skills", "settings", "war"
            ));
            ClanData clan = getPlayerClan(playerUUID);
            if (clan == null || !clan.getLeader().equals(playerUUID)) {
                subs.remove("settings");
            }
            if (clan != null && !clan.getLeader().equals(playerUUID)
                    && clan.getChestPermission(playerUUID) == ClanChestPermission.DENY) {
                subs.remove("chest");
            }
            if (player.hasPermission("clan.admin")) {
                subs.add("force");
                subs.add("admin");
            }
            String partial = args[0].toLowerCase();
            subs.removeIf(s -> !s.startsWith(partial));
            return subs;
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            ClanData clan = getPlayerClan(playerUUID);
            switch (sub) {
                case "kick":
                case "promote":
                case "demote":
                case "leader": {
                    if (clan != null) {
                        List<String> names = new ArrayList<>();
                        for (UUID mem : clan.getMembers()) {
                            String name = Bukkit.getOfflinePlayer(mem).getName();
                            if (name != null) names.add(name);
                        }
                        return names;
                    }
                    return null;
                }
                case "request": {
                    return new ArrayList<>(plugin.getFileManager().loadAllClans().keySet());
                }
                case "war": {
                    List<String> warOpts = new ArrayList<>(Arrays.asList("info", "accept", "deny", "setarena"));
                    warOpts.addAll(plugin.getFileManager().loadAllClans().keySet());
                    return warOpts;
                }
                case "force":
                    return Arrays.asList("kick", "delete");
                default:
                    return null;
            }
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            String sub2 = args[1].toLowerCase();
            if ("war".equals(sub) && ("accept".equals(sub2) || "deny".equals(sub2))) {
                WarManager wm = plugin.getWarManager();
                List<String> challenges = new ArrayList<>();
                ClanData myClan = getPlayerClan(playerUUID);
                if (myClan != null) {
                    for (String ct : plugin.getFileManager().loadAllClans().keySet()) {
                        WarManager.WarRequest req = wm.getWarRequestByChallenger(ct);
                        if (req != null && req.getTargetTag().equals(myClan.getTag())) {
                            challenges.add(ct);
                        }
                    }
                }
                return challenges;
            }
        }

        return null;
    }
}
