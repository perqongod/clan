package org.perq.clan;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
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
import java.util.stream.Collectors;

public class ClanCommand implements CommandExecutor, TabCompleter {

    private final Clan plugin;
    private final Map<String, Inventory> clanChests = new HashMap<>();
    private final Map<UUID, Long> spawnCooldowns = new HashMap<>();
    private final Map<UUID, Integer> spawnTaskIds = new HashMap<>();
    /** Stores UUIDs of players who initiated /clan disband|delete but not yet confirmed. */
    private final Map<UUID, Long> pendingDeletes = new HashMap<>();
    /** Admin UUIDs who initiated /clan leave but not yet confirmed. */
    private final Map<UUID, Long> pendingAdminLeaves = new HashMap<>();
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
    private static final long DELETE_CONFIRM_TIMEOUT_MS = 30_000L;
    private static final long INVITE_COOLDOWN_MS = 10_000L;
    private static final double RENAME_COOLDOWN_HOURS = 72.0;
    private static final long RENAME_COOLDOWN_MS = (long) (RENAME_COOLDOWN_HOURS * 3_600_000L);
    private static final int SPAWN_PARTICLE_COUNT = 60;
    private static final double SPAWN_PARTICLE_OFFSET_X = 0.6;
    private static final double SPAWN_PARTICLE_OFFSET_Y = 0.8;
    private static final double SPAWN_PARTICLE_OFFSET_Z = 0.6;
    private static final double SPAWN_PARTICLE_EXTRA = 0.1;
    private static final int CHEST_SIZE = 27;

    private static final Set<String> SUBCOMMANDS = new HashSet<>(Arrays.asList(
            "create", "delete", "invite", "accept", "deny", "join", "leave",
            "kick", "promote", "demote", "leader", "rename", "info", "help", "toggle", "stats",
            "ranking", "rally", "chest", "spawn", "setspawn", "delspawn", "request", "requests",
            "accept-request", "deny-request", "logs", "skills", "quest", "war", "force", "admin",
            "points", "reload"
    ));

    public ClanCommand(Clan plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().formatPlain("This command can only be used by players."));
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
            String format = cm.getClanChatFormat()
                    .replace("%player%", cm.formatPlain(player.getName()))
                    .replace("%message%", cm.formatPlain(chatMessage));
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
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Usage: /clan create <tag>"));
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
                newClan.addLog(player.getName() + " founded the clan.");
                try {
                    plugin.getFileManager().saveClan(newClan);
                    pData.setClanTag(tag);
                    pData.setRole("LEADER");
                    plugin.getFileManager().savePlayer(playerUUID, pData);
                    player.sendMessage(cm.getMessage("clan-created")
                            .replace("%tag%", cm.translateColors(tag)));
                } catch (Exception e) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Error saving."));
                }
                break;
            }

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
                String confirmCmd = "/clan delete confirm";
                String denyCmd = "/clan delete deny";
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
                            try { plugin.getFileManager().savePlayer(mem, pd); } catch (Exception ignored) { /* continue */ }
                        }
                    }
                    plugin.getFileManager().deleteClan(dissolvedTag);
                    String broadcastMsg = cm.getMessage("clan-dissolved")
                            .replace("%tag%", cm.translateColors(dissolvedTag));
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendMessage(broadcastMsg);
                    }
                } else if (args.length >= 2 && args[1].equalsIgnoreCase("deny")) {
                    pendingDeletes.remove(playerUUID);
                    player.sendMessage(cm.getMessage("delete-denied"));
                } else {
                    pendingDeletes.put(playerUUID, System.currentTimeMillis());
                    player.sendMessage(cm.getMessage("delete-pending")
                            .replace("%tag%", cm.translateColors(disbandClan.getTag())));
                    player.sendMessage(
                            Component.text(cm.getMessage("delete-confirm") + " ")
                                    .append(Component.text(cm.formatPlain("[Accept]"))
                                            .clickEvent(ClickEvent.runCommand(confirmCmd)))
                                    .append(Component.text(" / "))
                                    .append(Component.text(cm.formatPlain("[Deny]"))
                                            .clickEvent(ClickEvent.runCommand(denyCmd)))
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
                boolean isLeader = leaveClan.getLeader().equals(playerUUID);
                boolean isSoloLeader = isLeader && leaveClan.getMembers().size() <= 1;
                if (isSoloLeader) {
                    String confirmCmd = "/clan leave confirm";
                    String denyCmd = "/clan leave deny";
                    if (args.length >= 2 && args[1].equalsIgnoreCase("confirm")) {
                        Long pending = pendingAdminLeaves.get(playerUUID);
                        if (pending == null || System.currentTimeMillis() - pending > DELETE_CONFIRM_TIMEOUT_MS) {
                            player.sendMessage(cm.getMessage("clan-deleted-cancelled"));
                            return true;
                        }
                        pendingAdminLeaves.remove(playerUUID);
                        String dissolvedTag = leaveClan.getTag();
                        for (UUID mem : new ArrayList<>(leaveClan.getMembers())) {
                            PlayerData pd = plugin.getFileManager().loadPlayer(mem);
                            if (pd != null) {
                                pd.setClanTag(null);
                                pd.setRole("MEMBER");
                                try { plugin.getFileManager().savePlayer(mem, pd); } catch (Exception ignored) { /* continue */ }
                            }
                        }
                        plugin.getFileManager().deleteClan(dissolvedTag);
                        String broadcastMsg = cm.getMessage("clan-dissolved")
                                .replace("%tag%", cm.translateColors(dissolvedTag));
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.sendMessage(broadcastMsg);
                        }
                        return true;
                    } else if (args.length >= 2 && args[1].equalsIgnoreCase("deny")) {
                        pendingAdminLeaves.remove(playerUUID);
                        player.sendMessage(cm.getMessage("delete-denied"));
                        return true;
                    } else {
                        pendingAdminLeaves.put(playerUUID, System.currentTimeMillis());
                        player.sendMessage(cm.getMessage("delete-pending")
                                .replace("%tag%", cm.translateColors(leaveClan.getTag())));
                        player.sendMessage(
                                Component.text(cm.getMessage("delete-confirm") + " ")
                                        .append(Component.text(cm.formatPlain("[Accept]"))
                                                .clickEvent(ClickEvent.runCommand(confirmCmd)))
                                        .append(Component.text(" / "))
                                        .append(Component.text(cm.formatPlain("[Deny]"))
                                                .clickEvent(ClickEvent.runCommand(denyCmd)))
                        );
                        return true;
                    }
                }
                if (player.hasPermission("clan.admin")) {
                    String confirmCmd = "/clan leave confirm";
                    String denyCmd = "/clan leave deny";
                    if (args.length >= 2 && args[1].equalsIgnoreCase("confirm")) {
                        Long pending = pendingAdminLeaves.get(playerUUID);
                        if (pending == null || System.currentTimeMillis() - pending > DELETE_CONFIRM_TIMEOUT_MS) {
                            player.sendMessage(cm.getMessage("clan-deleted-cancelled"));
                            return true;
                        }
                        pendingAdminLeaves.remove(playerUUID);
                    } else if (args.length >= 2 && args[1].equalsIgnoreCase("deny")) {
                        pendingAdminLeaves.remove(playerUUID);
                        player.sendMessage(cm.getMessage("delete-denied"));
                        return true;
                    } else {
                        pendingAdminLeaves.put(playerUUID, System.currentTimeMillis());
                        player.sendMessage(cm.getMessage("delete-pending")
                                .replace("%tag%", cm.translateColors(leaveClan.getTag())));
                        player.sendMessage(
                                Component.text(cm.getMessage("delete-confirm") + " ")
                                        .append(Component.text(cm.formatPlain("[Accept]"))
                                                .clickEvent(ClickEvent.runCommand(confirmCmd)))
                                        .append(Component.text(" / "))
                                        .append(Component.text(cm.formatPlain("[Deny]"))
                                                .clickEvent(ClickEvent.runCommand(denyCmd)))
                        );
                        return true;
                    }
                }
                if (isLeader) {
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
                                try { plugin.getFileManager().savePlayer(newLeader, nlData); } catch (Exception ignored) { /* continue */ }
                            }
                        }
                    } else {
                        plugin.getFileManager().deleteClan(leaveClan.getTag());
                    }
                }
                leaveClan.getMembers().remove(playerUUID);
                leaveClan.getModerators().remove(playerUUID);
                leaveClan.setChestPermission(playerUUID, null);
                leaveClan.setFriendlyFirePermission(playerUUID, null);
                leaveClan.setSkillsPermission(playerUUID, null);
                leaveClan.setSpawnPermission(playerUUID, null);
                leaveClan.addLog(player.getName() + " left the clan.");
                PlayerData leaveData = plugin.getFileManager().loadPlayer(playerUUID);
                if (leaveData != null) {
                    leaveData.setClanTag(null);
                    leaveData.setRole("MEMBER");
                    try {
                        plugin.getFileManager().savePlayer(playerUUID, leaveData);
                        plugin.getFileManager().saveClan(leaveClan);
                    } catch (Exception ignored) { /* continue */ }
                }
                player.sendMessage(cm.getMessage("left-clan"));
                break;
            }

            case "kick": {
                if (args.length < 2) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Usage: /clan kick <player>"));
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
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "You cannot kick yourself."));
                    return true;
                }
                if (kickClan.getLeader().equals(kickTarget.getUniqueId())) {
                    player.sendMessage(cm.getMessage("no-permission"));
                    return true;
                }
                if (!kickClan.getMembers().contains(kickTarget.getUniqueId())) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Player is not in the clan."));
                    return true;
                }
                kickClan.getMembers().remove(kickTarget.getUniqueId());
                kickClan.getModerators().remove(kickTarget.getUniqueId());
                kickClan.setChestPermission(kickTarget.getUniqueId(), null);
                kickClan.setFriendlyFirePermission(kickTarget.getUniqueId(), null);
                kickClan.setSkillsPermission(kickTarget.getUniqueId(), null);
                kickClan.setSpawnPermission(kickTarget.getUniqueId(), null);
                kickClan.addLog(kickTarget.getName() + " was kicked by " + player.getName() + ".");
                PlayerData kickTargetData = plugin.getFileManager().loadPlayer(kickTarget.getUniqueId());
                if (kickTargetData != null) {
                    kickTargetData.setClanTag(null);
                    kickTargetData.setRole("MEMBER");
                    try {
                        plugin.getFileManager().savePlayer(kickTarget.getUniqueId(), kickTargetData);
                        plugin.getFileManager().saveClan(kickClan);
                    } catch (Exception ignored) { /* continue */ }
                }
                player.sendMessage(cm.getMessage("kicked").replace("%player%", cm.formatPlain(kickTarget.getName())));
                kickTarget.sendMessage(cm.getMessage("kicked"));
                break;
            }

            case "promote": {
                if (args.length < 2) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Usage: /clan promote <player>"));
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
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Player is not in the clan."));
                    return true;
                }
                PlayerData promoteData = plugin.getFileManager().loadPlayer(promoteTarget.getUniqueId());
                if (promoteData == null) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Player data not found."));
                    return true;
                }
                if ("MEMBER".equals(promoteData.getRole())) {
                    promoteData.setRole("MOD");
                    if (!promoteClan.getModerators().contains(promoteTarget.getUniqueId())) {
                        promoteClan.getModerators().add(promoteTarget.getUniqueId());
                    }
                    promoteClan.addLog(promoteTarget.getName() + " was promoted to MOD.");
                    try {
                        plugin.getFileManager().savePlayer(promoteTarget.getUniqueId(), promoteData);
                        plugin.getFileManager().saveClan(promoteClan);
                        player.sendMessage(cm.getMessage("promoted").replace("%player%", cm.formatPlain(promoteTarget.getName())));
                    } catch (Exception e) {
                        player.sendMessage(cm.formatPlain(cm.getPrefix() + "Error saving."));
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
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Player data not found."));
                    return true;
                }
                if (args.length < 2) {
                    if ("MOD".equals(actorData.getRole())) {
                        actorData.setRole("MEMBER");
                        demoteClan.getModerators().remove(playerUUID);
                        demoteClan.addLog(player.getName() + " demoted themselves to MEMBER.");
                        try {
                            plugin.getFileManager().savePlayer(playerUUID, actorData);
                            plugin.getFileManager().saveClan(demoteClan);
                            player.sendMessage(cm.getMessage("demoted").replace("%player%", cm.formatPlain(player.getName())));
                        } catch (Exception e) {
                            player.sendMessage(cm.formatPlain(cm.getPrefix() + "Error saving."));
                        }
                        return true;
                    }
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Usage: /clan demote <player>"));
                    return true;
                }
                if (!demoteClan.getLeader().equals(playerUUID)) {
                    player.sendMessage(cm.getMessage("no-permission"));
                    return true;
                }
                Player demoteTarget = Bukkit.getPlayer(args[1]);
                if (demoteTarget == null) {
                    player.sendMessage(cm.getMessage("player-not-found"));
                    return true;
                }
                if (demoteTarget.getUniqueId().equals(playerUUID)) {
                    player.sendMessage(cm.translateColors("&cYou cannot demote yourself."));
                    return true;
                }
                if (!demoteClan.getMembers().contains(demoteTarget.getUniqueId())) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Player is not in the clan."));
                    return true;
                }
                PlayerData demoteData = plugin.getFileManager().loadPlayer(demoteTarget.getUniqueId());
                if (demoteData == null) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Player data not found."));
                    return true;
                }
                if ("MOD".equals(demoteData.getRole())) {
                    demoteData.setRole("MEMBER");
                    demoteClan.getModerators().remove(demoteTarget.getUniqueId());
                    demoteClan.addLog(demoteTarget.getName() + " was demoted to MEMBER.");
                    try {
                        plugin.getFileManager().savePlayer(demoteTarget.getUniqueId(), demoteData);
                        plugin.getFileManager().saveClan(demoteClan);
                        player.sendMessage(cm.getMessage("demoted").replace("%player%", cm.formatPlain(demoteTarget.getName())));
                    } catch (Exception e) {
                        player.sendMessage(cm.formatPlain(cm.getPrefix() + "Error saving."));
                    }
                } else {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Player cannot be demoted."));
                }
                break;
            }

            case "leader": {
                if (args.length < 2) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Usage: /clan leader <player>"));
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
                        player.sendMessage(cm.formatPlain(cm.getPrefix() + "Player is not in the clan."));
                        return true;
                    }
                    freshClan.getModerators().remove(newLeaderPlayer.getUniqueId());
                    if (!freshClan.getModerators().contains(playerUUID)) {
                        freshClan.getModerators().add(playerUUID);
                    }
                    freshClan.setLeader(newLeaderPlayer.getUniqueId());
                    freshClan.addLog(newLeaderPlayer.getName() + " is now the new leader.");
                    PlayerData oldLeaderData = plugin.getFileManager().loadPlayer(playerUUID);
                    if (oldLeaderData != null) oldLeaderData.setRole("MOD");
                    PlayerData newLeaderData = plugin.getFileManager().loadPlayer(newLeaderPlayer.getUniqueId());
                    if (newLeaderData != null) newLeaderData.setRole("LEADER");
                    try {
                        if (oldLeaderData != null) plugin.getFileManager().savePlayer(playerUUID, oldLeaderData);
                        if (newLeaderData != null) plugin.getFileManager().savePlayer(newLeaderPlayer.getUniqueId(), newLeaderData);
                        plugin.getFileManager().saveClan(freshClan);
                        player.sendMessage(cm.formatPlain(cm.getPrefix() + "You are now MOD. " + newLeaderPlayer.getName() + " is the new leader."));
                        newLeaderPlayer.sendMessage(cm.formatPlain(cm.getPrefix() + "You are now the new leader of clan ["
                                + cm.translateColors(freshClan.getTag()) + "]!"));
                    } catch (Exception e) {
                        player.sendMessage(cm.formatPlain(cm.getPrefix() + "Error saving."));
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
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Player is not in the clan."));
                    return true;
                }
                if (transferTarget.getUniqueId().equals(playerUUID)) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "You are already the leader."));
                    return true;
                }
                pendingLeaderTransfers.put(playerUUID, transferTarget.getName());
                player.sendMessage(cm.formatPlain(cm.getPrefix() + "Do you want to transfer leadership to " + transferTarget.getName() + "?"));
                player.sendMessage(
                        Component.text(cm.formatPlain("[Accept]"))
                                .clickEvent(ClickEvent.runCommand("/clan leader confirm"))
                );
                player.sendMessage(
                        Component.text(cm.formatPlain("[Deny]"))
                                .clickEvent(ClickEvent.runCommand("/clan leader deny"))
                );
                break;
            }

            case "invite": {
                if (args.length < 2) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Usage: /clan invite <player>"));
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
                    player.sendMessage(cm.getMessage("invite-cooldown")
                            .replace("%seconds%", String.valueOf(remSec)));
                    return true;
                }
                int inviteMaxMembers = getMaxMembers(inviteClan, cm);
                if (inviteClan.getMembers().size() >= inviteMaxMembers) {
                    player.sendMessage(cm.getMessage("clan-full").replace("%max%", String.valueOf(inviteMaxMembers)));
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
                    player.sendMessage(cm.getMessage("target-in-clan-inviter").replace("%player%", cm.formatPlain(invTarget.getName())));
                    invTarget.sendMessage(cm.getMessage("target-in-clan-notify"));
                    return true;
                }
                if (!invTargetData.isInvitesEnabled()) {
                    player.sendMessage(cm.getMessage("invitations-disabled"));
                    return true;
                }
                List<InviteData> existingInvites = plugin.getFileManager().loadAllInvites(invTarget.getUniqueId());
                boolean alreadyInvited = false;
                for (InviteData existingInvite : existingInvites) {
                    if (existingInvite.getFromClan() != null
                            && existingInvite.getFromClan().equalsIgnoreCase(inviteClan.getTag())) {
                        alreadyInvited = true;
                        break;
                    }
                }
                if (alreadyInvited) {
                    player.sendMessage(cm.getMessage("invite-max-open"));
                    return true;
                }
                InviteData invite = new InviteData(inviteClan.getTag());
                try {
                    plugin.getFileManager().saveInvite(invTarget.getUniqueId(), invite);
                    inviteCooldowns.put(playerUUID, System.currentTimeMillis());
                    player.sendMessage(cm.getMessage("invitation-sent").replace("%player%", cm.formatPlain(invTarget.getName())));
                    invTarget.sendMessage(
                            cm.getMessage("invitation-received")
                                    .replace("%inviter%", cm.formatPlain(player.getName()))
                                    .replace("%tag%", cm.translateColors(inviteClan.getTag()))
                    );
                    invTarget.sendMessage(
                            Component.text(cm.formatPlain("[Accept]"))
                                    .clickEvent(ClickEvent.runCommand("/clan join " + inviteClan.getTag()))
                                    .append(Component.text(" / "))
                                    .append(Component.text(cm.formatPlain("[Deny]"))
                                            .clickEvent(ClickEvent.runCommand("/clan deny " + inviteClan.getTag())))
                    );
                } catch (Exception e) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Error saving."));
                }
                break;
            }

            case "accept": {
                if (args.length < 2) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Usage: /clan accept <tag>"));
                    return true;
                }
                String acceptTag = args[1];
                List<InviteData> acceptInvites = plugin.getFileManager().loadAllInvites(playerUUID);
                InviteData acceptInv = null;
                for (InviteData i : acceptInvites) {
                    if (acceptTag.equals(i.getFromClan())) { acceptInv = i; break; }
                }
                if (acceptInv == null) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "No invitation found."));
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
                acceptClan.addLog(player.getName() + " joined the clan.");
                acceptPd.setClanTag(acceptTag);
                acceptPd.setRole("MEMBER");
                try {
                    plugin.getFileManager().saveClan(acceptClan);
                    plugin.getFileManager().savePlayer(playerUUID, acceptPd);
                    plugin.getFileManager().deleteSpecificInvite(playerUUID, acceptTag);
                    player.sendMessage(cm.getMessage("invitation-accepted")
                            .replace("%tag%", cm.translateColors(acceptTag)));
                } catch (Exception e) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Error saving."));
                }
                break;
            }

            case "deny": {
                if (args.length < 2) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Usage: /clan deny <tag>"));
                    return true;
                }
                String denyTag = args[1];
                List<InviteData> denyInvites = plugin.getFileManager().loadAllInvites(playerUUID);
                boolean foundDeny = false;
                for (InviteData i : denyInvites) {
                    if (denyTag.equals(i.getFromClan())) { foundDeny = true; break; }
                }
                if (!foundDeny) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "No invitation found."));
                    return true;
                }
                try {
                    plugin.getFileManager().deleteSpecificInvite(playerUUID, denyTag);
                } catch (Exception ignored) { /* continue */ }
                player.sendMessage(cm.getMessage("invitation-denied"));
                break;
            }

            case "join": {
                if (args.length < 2) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Usage: /clan join <tag>"));
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
                        try { plugin.getFileManager().saveClan(currentClan); } catch (Exception ignored) { /* continue */ }
                    }
                }
                joinClan.getMembers().add(playerUUID);
                joinClan.addLog(player.getName() + " joined the clan.");
                joinPd.setClanTag(joinTag);
                joinPd.setRole("MEMBER");
                try {
                    plugin.getFileManager().saveClan(joinClan);
                    plugin.getFileManager().savePlayer(playerUUID, joinPd);
                    plugin.getFileManager().deleteSpecificInvite(playerUUID, joinTag);
                    player.sendMessage(cm.getMessage("invitation-accepted")
                            .replace("%tag%", cm.translateColors(joinTag)));
                } catch (Exception e) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Error saving."));
                }
                break;
            }

            case "request": {
                if (args.length < 2) {
                    ClanData listClan = getPlayerClan(playerUUID);
                    if (listClan != null && listClan.getLeader().equals(playerUUID)) {
                        sendRequestList(player, cm);
                    } else {
                        player.sendMessage(cm.formatPlain(cm.getPrefix() + "Usage: /clan request <tag>"));
                    }
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
                                try { plugin.getFileManager().savePlayer(mem, pd); } catch (Exception ignored) { /* continue */ }
                            }
                        }
                        plugin.getFileManager().deleteClan(oldTag);
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.sendMessage(cm.getMessage("clan-dissolved")
                                    .replace("%tag%", cm.translateColors(oldTag)));
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
                        player.sendMessage(cm.getMessage("request-sent")
                                .replace("%tag%", cm.translateColors(pendingReqTag)));
                        notifyLeaderOfRequest(targetReqClan, player, cm);
                    } catch (Exception e) {
                        player.sendMessage(cm.formatPlain(cm.getPrefix() + "Error saving."));
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
                if (reqPd.getClanTag() != null) {
                    if (reqTag.equalsIgnoreCase(reqPd.getClanTag())) {
                        player.sendMessage(cm.getMessage("already-in-clan"));
                        return true;
                    }
                    ClanData currentClan = plugin.getFileManager().loadClan(reqPd.getClanTag());
                    if (currentClan != null && currentClan.getLeader().equals(playerUUID)) {
                        pendingLeaderRequests.put(playerUUID, reqTag);
                        player.sendMessage(cm.getMessage("leader-request-confirm"));
                        player.sendMessage(
                                Component.text(cm.formatPlain("[Accept]"))
                                        .clickEvent(ClickEvent.runCommand("/clan request confirm"))
                        );
                        player.sendMessage(
                                Component.text(cm.formatPlain("[Deny]"))
                                        .clickEvent(ClickEvent.runCommand("/clan request deny"))
                        );
                        return true;
                    }
                }
                if (reqClan.getPendingRequests().contains(playerUUID)) {
                    player.sendMessage(cm.getMessage("request-already-sent"));
                    return true;
                }
                reqClan.getPendingRequests().add(playerUUID);
                try {
                    plugin.getFileManager().saveClan(reqClan);
                    player.sendMessage(cm.getMessage("request-sent")
                            .replace("%tag%", cm.translateColors(reqTag)));
                    notifyLeaderOfRequest(reqClan, player, cm);
                } catch (Exception e) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Error saving."));
                }
                break;
            }

            case "requests": {
                sendRequestList(player, cm);
                break;
            }

            case "accept-request": {
                if (args.length < 2) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Usage: /clan accept-request <player>"));
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
                // Accept by player name or UUID
                UUID arUUID = null;
                try {
                    arUUID = UUID.fromString(args[1]);
                } catch (IllegalArgumentException e) {
                    // Try looking up by player name
                    for (UUID reqUUID : arClan.getPendingRequests()) {
                        String reqName = Bukkit.getOfflinePlayer(reqUUID).getName();
                        if (args[1].equalsIgnoreCase(reqName)) {
                            arUUID = reqUUID;
                            break;
                        }
                    }
                }
                if (arUUID == null) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "No request from that player found."));
                    return true;
                }
                if (!arClan.getPendingRequests().contains(arUUID)) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "No request from that player found."));
                    return true;
                }
                int requestMaxMembers = getMaxMembers(arClan, cm);
                if (arClan.getMembers().size() >= requestMaxMembers) {
                    player.sendMessage(cm.getMessage("clan-full").replace("%max%", String.valueOf(requestMaxMembers)));
                    return true;
                }
                String arPlayerName = Bukkit.getOfflinePlayer(arUUID).getName();
                if (arPlayerName == null) arPlayerName = arUUID.toString().substring(0, 8);
                PlayerData arPd = plugin.getFileManager().loadPlayer(arUUID);
                if (arPd == null) arPd = new PlayerData(arPlayerName);
                String previousTag = arPd.getClanTag();
                if (previousTag != null && !previousTag.equalsIgnoreCase(arClan.getTag())) {
                    ClanData previousClan = plugin.getFileManager().loadClan(previousTag);
                    if (previousClan != null) {
                        boolean isLeader = previousClan.getLeader().equals(arUUID);
                        boolean hasActiveMembership = isLeader
                                || previousClan.getMembers().contains(arUUID)
                                || previousClan.getModerators().contains(arUUID);
                        if (hasActiveMembership) {
                            Player arOnline = Bukkit.getPlayer(arUUID);
                            if (isLeader) {
                                player.sendMessage(cm.getMessage("request-leader-blocked")
                                        .replace("%player%", cm.formatPlain(arPlayerName)));
                            } else {
                                player.sendMessage(cm.getMessage("request-player-in-clan")
                                        .replace("%player%", cm.formatPlain(arPlayerName)));
                            }
                            if (arOnline != null) {
                                String messageKey = isLeader ? "leader-cannot-join" : "request-in-clan-player";
                                arOnline.sendMessage(cm.getMessage(messageKey));
                            }
                            return true;
                        }
                    }
                }
                arClan.getPendingRequests().remove(arUUID);
                arClan.getMembers().add(arUUID);
                arClan.addLog(arPlayerName + " joined the clan via request.");
                arPd.setClanTag(arClan.getTag());
                arPd.setRole("MEMBER");
                try {
                    plugin.getFileManager().saveClan(arClan);
                    plugin.getFileManager().savePlayer(arUUID, arPd);
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + arPlayerName + " has been added to the clan."));
                    Player arOnline = Bukkit.getPlayer(arUUID);
                    if (arOnline != null) {
                        arOnline.sendMessage(cm.getMessage("invitation-accepted")
                                .replace("%tag%", cm.translateColors(arClan.getTag())));
                    }
                } catch (Exception e) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Error saving."));
                }
                break;
            }

            case "deny-request": {
                if (args.length < 2) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Usage: /clan deny-request <player>"));
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
                // Accept by player name or UUID
                UUID drUUID = null;
                try {
                    drUUID = UUID.fromString(args[1]);
                } catch (IllegalArgumentException e) {
                    for (UUID reqUUID : drClan.getPendingRequests()) {
                        String reqName = Bukkit.getOfflinePlayer(reqUUID).getName();
                        if (args[1].equalsIgnoreCase(reqName)) {
                            drUUID = reqUUID;
                            break;
                        }
                    }
                }
                if (drUUID == null || !drClan.getPendingRequests().contains(drUUID)) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "No request from that player found."));
                    return true;
                }
                drClan.getPendingRequests().remove(drUUID);
                try {
                    plugin.getFileManager().saveClan(drClan);
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Request denied."));
                    Player drOnline = Bukkit.getPlayer(drUUID);
                    if (drOnline != null) {
                        drOnline.sendMessage(cm.formatPlain(cm.getPrefix() + "Your join request was denied."));
                    }
                } catch (Exception e) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Error saving."));
                }
                break;
            }

            case "rename": {
                if (args.length < 2) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Usage: /clan rename <newTag>"));
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
                if (!ClanSkillProgress.hasRename(renameClan.getSkillPoints())) {
                    player.sendMessage(cm.getMessage("skills-locked-rename")
                            .replace("%required%", String.valueOf(ClanSkillProgress.getRenameUnlockPoints())));
                    return true;
                }
                double hoursRemaining = getRenameCooldownHoursRemaining(renameClan);
                if (hoursRemaining > 0) {
                    player.sendMessage(cm.getMessage("rename-too-soon")
                            .replace("%hours%", String.format("%.1f", hoursRemaining))
                            .replace("%required%", String.valueOf((int) RENAME_COOLDOWN_HOURS)));
                    return true;
                }
                String newTag = cm.normalizeTag(args[1]);
                boolean allowColors = player.hasPermission("clan.vip")
                        || ClanSkillProgress.hasRename(renameClan.getSkillPoints());
                TagValidator.ValidationResult renameResult = new TagValidator(plugin).validate(newTag, allowColors);
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
                        try { plugin.getFileManager().savePlayer(mem, md); } catch (Exception ignored) { /* continue */ }
                    }
                }
                plugin.getFileManager().deleteClan(renameClan.getTag());
                renameClan.setTag(newTag);
                renameClan.setLastRenameAt(System.currentTimeMillis());
                try {
                    plugin.getFileManager().saveClan(renameClan);
                    player.sendMessage(cm.getMessage("renamed")
                            .replace("%tag%", cm.translateColors(newTag)));
                } catch (Exception e) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Error saving."));
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
                player.sendMessage(cm.formatPlain(prefix + "Tag: " + cm.translateColors(infoClan.getTag())));
                player.sendMessage(cm.formatPlain(prefix + "Points: " + infoClan.getPoints()));
                player.sendMessage(cm.formatPlain(prefix + "Rank: " + infoClan.getRank()));
                player.sendMessage(cm.formatPlain(prefix + "Members: " + infoClan.getMembers().size() + "/" + getMaxMembers(infoClan, cm)));
                player.sendMessage(cm.formatPlain(prefix + "Created: " + infoClan.getCreated()));
                player.sendMessage(cm.getMessage("clan-info-playtime")
                        .replace("%hours%", String.format("%.1f", infoClan.getOnlineTime())));
                player.sendMessage(cm.formatPlain(prefix + "Leader: " + Bukkit.getOfflinePlayer(infoClan.getLeader()).getName()));
                String modsStr = infoClan.getModerators().isEmpty() ? "None"
                        : infoClan.getModerators().stream()
                        .map(u -> Bukkit.getOfflinePlayer(u).getName())
                        .filter(Objects::nonNull)
                        .reduce((a, b) -> a + ", " + b).orElse("None");
                player.sendMessage(cm.formatPlain(prefix + "Moderators: " + modsStr));
                player.sendMessage(cm.formatPlain(prefix + "Member list:"));
                for (UUID mem : infoClan.getMembers()) {
                    String memName = Bukkit.getOfflinePlayer(mem).getName();
                    PlayerData md = plugin.getFileManager().loadPlayer(mem);
                    String role = md != null ? md.getRole() : "MEMBER";
                    String online = Bukkit.getPlayer(mem) != null ? "online" : "offline";
                    double time = md != null ? md.getOnlineTime() : 0.0;
                    player.sendMessage(cm.formatPlain(prefix + "  - " + memName + " (" + role + ") " + online + " (" + String.format("%.1f", time) + "h)"));
                }
                break;
            }

            case "help": {
                openHelpBook(player, cm);
                break;
            }

            case "reload": {
                if (!player.hasPermission("clan.admin")) {
                    player.sendMessage(cm.getMessage("no-permission"));
                    return true;
                }
                plugin.getConfigManager().reload();
                player.sendMessage(cm.getMessage("config-reloaded"));
                return true;
            }

            case "toggle": {
                boolean invitesDisabled = plugin.toggleInvitation(player);
                player.sendMessage(invitesDisabled ? cm.getMessage("toggle-off") : cm.getMessage("toggle-on"));
                break;
            }

            case "stats": {
                ClanData statsClan;
                if (args.length < 2) {
                    statsClan = getPlayerClan(playerUUID);
                    if (statsClan == null) {
                        player.sendMessage(cm.getMessage("no-clan"));
                        return true;
                    }
                } else {
                    statsClan = plugin.getFileManager().loadClan(args[1]);
                    if (statsClan == null) {
                        player.sendMessage(cm.getMessage("clan-not-found"));
                        return true;
                    }
                }
                plugin.getClanStatsListener().openGui(player, statsClan);
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
                                try { plugin.getFileManager().savePlayer(mem, pd); } catch (Exception ignored) { /* continue */ }
                            }
                        }
                        plugin.getFileManager().deleteClan(delTag);
                    }
                }
                List<Map.Entry<String, ClanData>> sorted = new ArrayList<>(allClans.entrySet());
                sorted.sort((a, b) -> Integer.compare(b.getValue().getPoints(), a.getValue().getPoints()));
                player.sendMessage(cm.formatPlain(cm.getPrefix() + "ᴄʟᴀɴ ʀᴀɴᴋɪɴɢ:"));
                int rankNum = 1;
                for (Map.Entry<String, ClanData> entry : sorted) {
                    ClanData cd = entry.getValue();
                    String leaderName = Bukkit.getOfflinePlayer(cd.getLeader()).getName();
                    if (leaderName == null) leaderName = "?";
                    player.sendMessage(cm.formatPlain("#" + rankNum + ". [" + cm.translateColors(cd.getTag()) + "] Leader: " + leaderName
                            + " | Members: " + cd.getMembers().size()
                            + " | Points: " + cd.getPoints()
                            + " [" + cd.getRank() + "]"));
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
                if (!ClanSkillProgress.hasChest(chestClan.getSkillPoints())) {
                    player.sendMessage(cm.getMessage("skills-locked-chest")
                            .replace("%required%", String.valueOf(ClanSkillProgress.getChestUnlockPoints())));
                    return true;
                }
                // Handle chest invite subcommand
                if (args.length >= 2 && args[1].equalsIgnoreCase("invite")) {
                    PlayerData chestInviterData = plugin.getFileManager().loadPlayer(playerUUID);
                    if (chestInviterData == null
                            || (!"LEADER".equals(chestInviterData.getRole()) && !"MOD".equals(chestInviterData.getRole()))) {
                        player.sendMessage(cm.getMessage("no-permission"));
                        return true;
                    }
                    if (args.length < 3) {
                        player.sendMessage(cm.formatPlain(cm.getPrefix() + "Usage: /clan chest invite <player>"));
                        return true;
                    }
                    Player chestInviteTarget = Bukkit.getPlayer(args[2]);
                    if (chestInviteTarget == null) {
                        player.sendMessage(cm.getMessage("player-not-found"));
                        return true;
                    }
                    if (!chestClan.getMembers().contains(chestInviteTarget.getUniqueId())) {
                        player.sendMessage(cm.formatPlain(cm.getPrefix() + "Player is not in your clan."));
                        return true;
                    }
                    chestClan.setChestPermission(chestInviteTarget.getUniqueId(), ClanChestPermission.EXECUTE);
                    try {
                        plugin.getFileManager().saveClan(chestClan);
                        player.sendMessage(cm.formatPlain(cm.getPrefix() + chestInviteTarget.getName() + " now has access to the clan chest."));
                        chestInviteTarget.sendMessage(cm.formatPlain(cm.getPrefix() + "You have been granted access to the clan chest."));
                    } catch (Exception e) {
                        player.sendMessage(cm.formatPlain(cm.getPrefix() + "Error saving."));
                    }
                    return true;
                }
                boolean isLeader = chestClan.getLeader().equals(playerUUID);
                boolean wantsSet = args.length >= 2 && "set".equalsIgnoreCase(args[1]);
                if (wantsSet && !isLeader) {
                    player.sendMessage(cm.getMessage("not-clan-leader"));
                    return true;
                }
                if (isLeader && wantsSet) {
                    chestClan.setChestLocation(player.getLocation());
                    try {
                        plugin.getFileManager().saveClan(chestClan);
                        if (wantsSet) {
                            player.sendMessage(cm.getMessage("chest-set"));
                        }
                    } catch (Exception e) {
                        player.sendMessage(cm.formatPlain(cm.getPrefix() + "Error saving."));
                    }
                }
                // All clan members can open the chest (VIEW = see only, EXECUTE = interact)
                int chestSize = CHEST_SIZE;
                Inventory chest = clanChests.get(chestClan.getTag());
                if (chest == null || chest.getSize() != chestSize) {
                    ClanChestHolder chestHolder = new ClanChestHolder(chestClan.getTag());
                    chest = Bukkit.createInventory(chestHolder, chestSize,
                            cm.translateColors("Clan Chest: " + chestClan.getTag()));
                    chestHolder.setInventory(chest);
                    ItemStack[] contents = chestClan.getChestContents();
                    if (contents.length != chestSize) {
                        contents = Arrays.copyOf(contents, chestSize);
                    }
                    chest.setContents(contents);
                    clanChests.put(chestClan.getTag(), chest);
                }
                player.openInventory(chest);
                break;
            }

            case "rally": {
                ClanData rallyClan = getPlayerClan(playerUUID);
                if (rallyClan == null) {
                    player.sendMessage(cm.getMessage("no-clan"));
                    return true;
                }
                if (!rallyClan.getLeader().equals(playerUUID)) {
                    player.sendMessage(cm.getMessage("not-clan-leader"));
                    return true;
                }
                String rallyCaller = cm.formatPlain(player.getName());
                String rallyMessage = cm.getMessage("rally-called")
                        .replace("%player%", rallyCaller);
                for (UUID mem : rallyClan.getMembers()) {
                    Player member = Bukkit.getPlayer(mem);
                    if (member != null) {
                        member.sendMessage(rallyMessage);
                    }
                }
                break;
            }

            case "spawn": {
                ClanData spawnClan = getPlayerClan(playerUUID);
                if (spawnClan == null) {
                    player.sendMessage(cm.getMessage("no-clan"));
                    return true;
                }
                ClanAccessPermission spawnPermission = getEffectiveSpawnPermission(spawnClan, playerUUID);
                // ClanAccessPermission has VIEW/EXECUTE/DENY; allow VIEW/EXECUTE, only block explicit DENY.
                if (spawnPermission == ClanAccessPermission.DENY) {
                    player.sendMessage(cm.getMessage("no-permission"));
                    return true;
                }
                if (!ClanSkillProgress.hasSpawn(spawnClan.getSkillPoints())) {
                    player.sendMessage(cm.getMessage("skills-locked-spawn")
                            .replace("%required%", String.valueOf(ClanSkillProgress.getSpawnUnlockPoints())));
                    return true;
                }
                if (!isValidSpawnLocation(spawnClan.getSpawn())) {
                    player.sendMessage(cm.getMessage("spawn-not-set"));
                    return true;
                }
                long now = System.currentTimeMillis();
                Long cooldownEnd = spawnCooldowns.get(playerUUID);
                if (cooldownEnd != null && now < cooldownEnd) {
                    long remaining = (cooldownEnd - now) / 1000 + 1;
                    player.sendActionBar(cm.translateColors("&#FF0000ᴄʟᴀɴ ꜱᴘᴀᴡɴ cooldown: " + remaining + "s"));
                    return true;
                }
                // Cancel any existing pending teleport task
                Integer existingTask = spawnTaskIds.get(playerUUID);
                if (existingTask != null) {
                    plugin.getServer().getScheduler().cancelTask(existingTask);
                    spawnTaskIds.remove(playerUUID);
                }
                player.sendMessage(cm.getMessage("spawn-teleporting"));
                int[] ticksLeft = {3};
                int taskId = plugin.getServer().getScheduler().runTaskTimer(plugin, new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!player.isOnline()) {
                            spawnTaskIds.remove(playerUUID);
                            cancel();
                            return;
                        }
                        if (ticksLeft[0] > 0) {
                            player.sendActionBar(cm.translateColors("&#FFFF00Teleporting in " + ticksLeft[0] + "s..."));
                            ticksLeft[0]--;
                        } else {
                            ClanData refreshed = plugin.getFileManager().loadClan(spawnClan.getTag());
                            if (refreshed != null && !ClanSkillProgress.hasSpawn(refreshed.getSkillPoints())) {
                                spawnTaskIds.remove(playerUUID);
                                cancel();
                                player.sendMessage(cm.getMessage("skills-locked-spawn")
                                        .replace("%required%", String.valueOf(ClanSkillProgress.getSpawnUnlockPoints())));
                                return;
                            }
                            Location target = resolveSpawnLocation(refreshed, spawnClan);
                            if (target == null) {
                                spawnTaskIds.remove(playerUUID);
                                cancel();
                                player.sendMessage(cm.getMessage("spawn-not-set"));
                                return;
                            }
                            player.teleport(target);
                            player.sendMessage(cm.getMessage("spawn-teleported"));
                            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                            player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation(),
                                    SPAWN_PARTICLE_COUNT,
                                    SPAWN_PARTICLE_OFFSET_X,
                                    SPAWN_PARTICLE_OFFSET_Y,
                                    SPAWN_PARTICLE_OFFSET_Z,
                                    SPAWN_PARTICLE_EXTRA);
                            long cdEnd = System.currentTimeMillis() + 30_000L;
                            spawnCooldowns.put(playerUUID, cdEnd);
                            spawnTaskIds.remove(playerUUID);
                            cancel();
                            // Start cooldown action bar display task
                            plugin.getServer().getScheduler().runTaskTimer(plugin, new BukkitRunnable() {
                                @Override
                                public void run() {
                                    if (!player.isOnline()) { cancel(); return; }
                                    Long end = spawnCooldowns.get(playerUUID);
                                    long current = System.currentTimeMillis();
                                    if (end == null || current >= end) {
                                        spawnCooldowns.remove(playerUUID);
                                        cancel();
                                        return;
                                    }
                                    long rem = (end - current) / 1000 + 1;
                                    player.sendActionBar(cm.translateColors("&#FF0000ᴄʟᴀɴ ꜱᴘᴀᴡɴ cooldown: " + rem + "s"));
                                }
                            }, 0L, 20L);
                        }
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
                if (!ClanSkillProgress.hasSpawn(ssClan.getSkillPoints())) {
                    player.sendMessage(cm.getMessage("skills-locked-setspawn")
                            .replace("%required%", String.valueOf(ClanSkillProgress.getSpawnUnlockPoints())));
                    return true;
                }
                ssClan.setSpawn(player.getLocation());
                try {
                    plugin.getFileManager().saveClan(ssClan);
                    player.sendMessage(cm.getMessage("spawn-set"));
                } catch (Exception e) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Error saving."));
                }
                break;
            }

            case "delspawn": {
                ClanData dsClan = getPlayerClan(playerUUID);
                if (dsClan == null) {
                    player.sendMessage(cm.getMessage("no-clan"));
                    return true;
                }
                if (!dsClan.getLeader().equals(playerUUID)) {
                    player.sendMessage(cm.getMessage("no-permission"));
                    return true;
                }
                if (!ClanSkillProgress.hasSpawn(dsClan.getSkillPoints())) {
                    player.sendMessage(cm.getMessage("skills-locked-setspawn")
                            .replace("%required%", String.valueOf(ClanSkillProgress.getSpawnUnlockPoints())));
                    return true;
                }
                if (dsClan.getSpawn() == null) {
                    player.sendMessage(cm.getMessage("spawn-not-set"));
                    return true;
                }
                dsClan.setSpawn(null);
                try {
                    plugin.getFileManager().saveClan(dsClan);
                    player.sendMessage(cm.getMessage("spawn-deleted"));
                } catch (Exception e) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Error saving."));
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
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "No log entries available."));
                    return true;
                }
                ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
                BookMeta bookMeta = (BookMeta) book.getItemMeta();
                if (bookMeta == null) break;
                bookMeta.setTitle(cm.formatPlain("Clan Logs"));
                bookMeta.setAuthor(cm.translateColors(logClan.getTag()));
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
                        page.append(cm.formatPlain(allLogs.get(lineIdx))).append("\n");
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
                ClanData skillsClan = getPlayerClan(playerUUID);
                if (skillsClan == null) {
                    player.sendMessage(cm.getMessage("no-clan"));
                    return true;
                }
                if (getEffectiveSkillsPermission(skillsClan, playerUUID) != ClanAccessPermission.EXECUTE) {
                    player.sendMessage(cm.getMessage("no-permission"));
                    return true;
                }
                plugin.getClanSkillsListener().openGui(player, skillsClan);
                break;
            }

            case "quest": {
                ClanData questClan = getPlayerClan(playerUUID);
                if (questClan == null) {
                    player.sendMessage(cm.getMessage("no-clan"));
                    return true;
                }
                plugin.getClanQuestListener().openGui(player, questClan);
                break;
            }

            case "force": {
                if (!player.hasPermission("clan.admin")) {
                    player.sendMessage(cm.getMessage("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Usage: /clan force kick <player> | /clan force delete <tag>"));
                    return true;
                }
                if (args[1].equalsIgnoreCase("kick") || args[1].equalsIgnoreCase("leave")) {
                    if (args.length < 3) {
                        player.sendMessage(cm.formatPlain(cm.getPrefix() + "Usage: /clan force kick <player>"));
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
                        ftClan.setChestPermission(forceTarget.getUniqueId(), null);
                        ftClan.setFriendlyFirePermission(forceTarget.getUniqueId(), null);
                        ftClan.setSkillsPermission(forceTarget.getUniqueId(), null);
                        ftClan.setSpawnPermission(forceTarget.getUniqueId(), null);
                        PlayerData ftData = plugin.getFileManager().loadPlayer(forceTarget.getUniqueId());
                        if (ftData != null) {
                            ftData.setClanTag(null);
                            ftData.setRole("MEMBER");
                            try {
                                plugin.getFileManager().savePlayer(forceTarget.getUniqueId(), ftData);
                                plugin.getFileManager().saveClan(ftClan);
                            } catch (Exception ignored) { /* continue */ }
                        }
                        player.sendMessage(cm.getMessage("force-leave-success").replace("%player%", cm.formatPlain(forceTarget.getName())));
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
                                Component.text(cm.getMessage("force-kick-confirm").replace("%player%", cm.formatPlain(forceTarget.getName())) + " ")
                                        .append(Component.text(cm.formatPlain("[Accept]"))
                                                .clickEvent(ClickEvent.runCommand("/clan force kick confirm")))
                                        .append(Component.text(" / "))
                                        .append(Component.text(cm.formatPlain("[Deny]"))
                                                .clickEvent(ClickEvent.runCommand("/clan force kick deny")))
                        );
                    }
                } else if (args[1].equalsIgnoreCase("delete")) {
                    if (args.length < 3) {
                        player.sendMessage(cm.formatPlain(cm.getPrefix() + "Usage: /clan force delete <tag>"));
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
                                try { plugin.getFileManager().savePlayer(mem, pd); } catch (Exception ignored) { /* continue */ }
                            }
                        }
                        plugin.getFileManager().deleteClan(pendingTag);
                        player.sendMessage(cm.getMessage("clan-deleted"));
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.sendMessage(cm.getMessage("clan-dissolved")
                                    .replace("%tag%", cm.translateColors(pendingTag)));
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
                                Component.text(cm.getMessage("force-delete-confirm")
                                        .replace("%tag%", cm.translateColors(fdArg)) + " ")
                                        .append(Component.text(cm.formatPlain("[Accept]"))
                                                .clickEvent(ClickEvent.runCommand("/clan force delete confirm")))
                                        .append(Component.text(" / "))
                                        .append(Component.text(cm.formatPlain("[Deny]"))
                                                .clickEvent(ClickEvent.runCommand("/clan force delete deny")))
                        );
                    }
                } else {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Usage: /clan force kick <player> | /clan force delete <tag>"));
                }
                break;
            }

            case "admin": {
                if (!player.hasPermission("clan.admin")) {
                    player.sendMessage(cm.getMessage("no-permission"));
                    return true;
                }
                player.sendMessage(cm.translateColors(cm.getPrefix() + "&cAdmin commands:"));
                player.sendMessage(cm.translateColors(cm.getPrefix() + "/clan force kick <player> &7- Remove player from clan"));
                player.sendMessage(cm.translateColors(cm.getPrefix() + "/clan force delete <tag> &7- Disband clan"));
                player.sendMessage(cm.translateColors(cm.getPrefix() + "/clan points <add|remove|set> <player> <amount> &7- Manage clan points (OP)"));
                break;
            }

            case "points": {
                if (!player.isOp()) {
                    player.sendMessage(cm.getMessage("admin-points-no-op"));
                    return true;
                }
                if (args.length < 4) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Usage: /clan points <add|remove|set> <player> <amount>"));
                    return true;
                }
                String pointsSub = args[1].toLowerCase();
                if (!pointsSub.equals("add") && !pointsSub.equals("remove") && !pointsSub.equals("set")) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Usage: /clan points <add|remove|set> <player> <amount>"));
                    return true;
                }
                String targetName = args[2];
                int amount;
                try {
                    amount = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Amount must be a valid integer."));
                    return true;
                }
                if (amount < 0 || (amount == 0 && !pointsSub.equals("set"))) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Amount must be a positive integer."));
                    return true;
                }
                // Resolve target player (online first, then cached offline)
                UUID targetUUID = null;
                Player targetOnline = Bukkit.getPlayer(targetName);
                if (targetOnline != null) {
                    targetUUID = targetOnline.getUniqueId();
                } else {
                    @SuppressWarnings("deprecation")
                    org.bukkit.OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
                    if (offlineTarget.hasPlayedBefore()) {
                        targetUUID = offlineTarget.getUniqueId();
                    }
                }
                if (targetUUID == null) {
                    player.sendMessage(cm.getMessage("admin-points-not-found"));
                    return true;
                }
                PlayerData targetPData = plugin.getFileManager().loadPlayer(targetUUID);
                if (targetPData == null || targetPData.getClanTag() == null) {
                    player.sendMessage(cm.getMessage("admin-points-not-found"));
                    return true;
                }
                ClanData targetClan = plugin.getFileManager().loadClan(targetPData.getClanTag());
                if (targetClan == null) {
                    player.sendMessage(cm.getMessage("admin-points-not-found"));
                    return true;
                }
                int maxPoints = cm.getMaxPoints();
                int currentPoints = targetClan.getPoints();
                int newPoints;
                String successMsg;
                if (pointsSub.equals("add")) {
                    newPoints = Math.min(currentPoints + amount, maxPoints);
                    successMsg = cm.getMessage("admin-points-add")
                            .replace("%tag%", cm.translateColors(targetClan.getTag()))
                            .replace("%amount%", String.valueOf(amount))
                            .replace("%total%", String.valueOf(newPoints));
                } else if (pointsSub.equals("remove")) {
                    newPoints = Math.max(currentPoints - amount, cm.getMinPoints());
                    successMsg = cm.getMessage("admin-points-remove")
                            .replace("%tag%", cm.translateColors(targetClan.getTag()))
                            .replace("%amount%", String.valueOf(amount))
                            .replace("%total%", String.valueOf(newPoints));
                } else {
                    newPoints = Math.max(cm.getMinPoints(), Math.min(amount, maxPoints));
                    successMsg = cm.getMessage("admin-points-set")
                            .replace("%tag%", cm.translateColors(targetClan.getTag()))
                            .replace("%amount%", String.valueOf(newPoints));
                }
                targetClan.setPoints(newPoints);
                targetClan.setRank(cm.getRankForPoints(newPoints));
                targetClan.addLog("[ADMIN] " + player.getName() + " " + pointsSub + " " + amount
                        + " points (new total: " + newPoints + ").");
                try {
                    plugin.getFileManager().saveClan(targetClan);
                } catch (IOException e) {
                    player.sendMessage(cm.formatPlain(cm.getPrefix() + "Error saving clan data."));
                    return true;
                }
                player.sendMessage(successMsg);
                plugin.getLogger().info("[ClanPoints] " + player.getName() + " " + pointsSub + " "
                        + amount + " points for clan " + targetClan.getTag()
                        + " (new total: " + newPoints + ").");
                break;
            }

            default: {
                player.sendMessage(cm.formatPlain(cm.getPrefix() + "Unknown subcommand. Use /clan for help."));
                break;
            }
        }

        return true;
    }

    // --- Helper methods ---

    private double getRenameCooldownHoursRemaining(ClanData clan) {
        long lastRename = clan.getLastRenameAt();
        if (lastRename <= 0) return 0.0;
        long elapsed = System.currentTimeMillis() - lastRename;
        long remaining = RENAME_COOLDOWN_MS - elapsed;
        if (remaining <= 0) return 0.0;
        return remaining / 3_600_000.0;
    }

    private void notifyLeaderOfRequest(ClanData clan, Player requester, ConfigManager cm) {
        Player leaderOnline = Bukkit.getPlayer(clan.getLeader());
        if (leaderOnline != null) {
            leaderOnline.sendMessage(
                    Component.text(cm.getMessage("request-received").replace("%player%", cm.formatPlain(requester.getName())) + " ")
                            .append(Component.text(cm.formatPlain("[View requests]"))
                                    .clickEvent(ClickEvent.runCommand("/clan request")))
            );
        }
    }

    private void sendRequestList(Player player, ConfigManager cm) {
        UUID playerUUID = player.getUniqueId();
        ClanData reqsClan = getPlayerClan(playerUUID);
        if (reqsClan == null) {
            player.sendMessage(cm.getMessage("no-clan"));
            return;
        }
        if (!reqsClan.getLeader().equals(playerUUID)) {
            player.sendMessage(cm.getMessage("requests-only-leader"));
            return;
        }
        List<UUID> pendingReqs = reqsClan.getPendingRequests();
        if (pendingReqs.isEmpty()) {
            player.sendMessage(cm.getMessage("requests-empty"));
            return;
        }
        player.sendMessage(cm.formatPlain(cm.getPrefix() + "Pending join requests:"));
        for (UUID reqUUID : new ArrayList<>(pendingReqs)) {
            String reqName = Bukkit.getOfflinePlayer(reqUUID).getName();
            if (reqName == null) reqName = reqUUID.toString().substring(0, 8);
            player.sendMessage(
                    Component.text(cm.formatPlain(cm.getPrefix() + reqName + " "))
                            .append(Component.text(cm.formatPlain("[Accept]"))
                                    .clickEvent(ClickEvent.runCommand("/clan accept-request " + reqName)))
            );
        }
    }

    private void sendHelp(Player player, ConfigManager cm) {
        ClanData clan = getPlayerClan(player.getUniqueId());
        UUID playerUUID = player.getUniqueId();
        ClanAccessPermission skillsPermission = getEffectiveSkillsPermission(clan, playerUUID);
        ClanAccessPermission spawnPermission = getEffectiveSpawnPermission(clan, playerUUID);
        Map<String, Boolean> conditions = buildHelpConditions(clan, playerUUID, skillsPermission, spawnPermission, player);
        Map<String, String> placeholders = buildHelpPlaceholders(cm);

        for (String line : cm.getHelpLines()) {
            String formatted = formatHelpLine(line, conditions, placeholders);
            if (formatted != null) {
                player.sendMessage(cm.translateColors(formatted));
            }
        }
    }

    private Map<String, Boolean> buildHelpConditions(ClanData clan,
            UUID playerUUID,
            ClanAccessPermission skillsPermission,
            ClanAccessPermission spawnPermission,
            Player player) {
        Map<String, Boolean> conditions = new HashMap<>();
        conditions.put("%leader%", clan != null && clan.getLeader().equals(playerUUID));
        conditions.put("%chest%", clan == null || clan.getLeader().equals(playerUUID) || clan.getChestPermission(playerUUID) != ClanChestPermission.DENY);
        conditions.put("%spawn%", clan == null || spawnPermission != ClanAccessPermission.DENY);
        conditions.put("%skills%", clan == null || skillsPermission != ClanAccessPermission.DENY);
        conditions.put("%admin%", player.hasPermission("clan.admin"));
        return conditions;
    }

    private Map<String, String> buildHelpPlaceholders(ConfigManager cm) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%prefix%", cm.getPrefix());
        placeholders.put("%spawn_unlock%", String.valueOf(ClanSkillProgress.getSpawnUnlockPoints()));
        return placeholders;
    }

    private Location resolveSpawnLocation(ClanData refreshed, ClanData fallback) {
        Location refreshedSpawn = refreshed == null ? null : refreshed.getSpawn();
        if (isValidSpawnLocation(refreshedSpawn)) {
            return refreshedSpawn;
        }
        Location fallbackSpawn = fallback == null ? null : fallback.getSpawn();
        return isValidSpawnLocation(fallbackSpawn) ? fallbackSpawn : null;
    }

    private boolean isValidSpawnLocation(Location location) {
        return location != null && location.getWorld() != null;
    }

    private String formatHelpLine(String line, Map<String, Boolean> conditions, Map<String, String> placeholders) {
        if (line == null) return null;
        String formatted = line;
        for (Map.Entry<String, Boolean> entry : conditions.entrySet()) {
            String updated = formatted.replace(entry.getKey(), "");
            if (!updated.equals(formatted)) {
                if (!entry.getValue()) return null;
                formatted = updated;
            }
        }
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            formatted = formatted.replace(entry.getKey(), entry.getValue());
        }
        formatted = formatted.trim();
        return formatted.isEmpty() ? null : formatted;
    }

    private void openHelpBook(Player player, ConfigManager cm) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta bookMeta = (BookMeta) book.getItemMeta();
        if (bookMeta == null) {
            player.sendMessage(cm.formatPlain(cm.getPrefix() + "Unable to open the help book."));
            return;
        }
        bookMeta.setTitle(cm.getHelpBookTitle());
        bookMeta.setAuthor(cm.getHelpBookAuthor());
        List<String> rawPages = cm.getHelpBookPages();
        if (rawPages.isEmpty()) {
            player.sendMessage(cm.formatPlain(cm.getPrefix() + "Help book is empty."));
            return;
        }
        List<Component> pages = new ArrayList<>();
        LegacyComponentSerializer serializer = LegacyComponentSerializer.legacySection();
        for (String page : rawPages) {
            pages.add(serializer.deserialize(cm.translateColors(page)));
        }
        bookMeta.pages(pages);
        book.setItemMeta(bookMeta);
        player.openBook(book);
    }

    private ClanAccessPermission getEffectiveSkillsPermission(ClanData clan, UUID member) {
        if (clan == null || member == null) return ClanAccessPermission.defaultPermission();
        if (member.equals(clan.getLeader())) {
            return ClanAccessPermission.leaderDefault();
        }
        return clan.getSkillsPermission(member);
    }

    private ClanAccessPermission getEffectiveSpawnPermission(ClanData clan, UUID member) {
        if (clan == null || member == null) return ClanAccessPermission.defaultPermission();
        if (member.equals(clan.getLeader())) {
            return ClanAccessPermission.leaderDefault();
        }
        return clan.getSpawnPermission(member);
    }

    private int getMaxMembers(ClanData clan, ConfigManager cm) {
        if (clan == null) return cm.getMaxMembers();
        return cm.getMaxMembers() + ClanSkillProgress.getBonusMemberSlots(clan.getSkillPoints());
    }

    private ClanData getPlayerClan(UUID playerUUID) {
        PlayerData p = plugin.getFileManager().loadPlayer(playerUUID);
        if (p == null || p.getClanTag() == null) return null;
        return plugin.getFileManager().loadClan(p.getClanTag());
    }

    private List<String> getInviteTargets(ClanData clan, UUID playerUUID) {
        if (clan == null) return null;
        if (!playerUUID.equals(clan.getLeader()) && !clan.getModerators().contains(playerUUID)) return null;
        return Bukkit.getOnlinePlayers().stream()
                .filter(online -> !online.getUniqueId().equals(playerUUID))
                .filter(online -> !clan.getMembers().contains(online.getUniqueId()))
                .map(Player::getName)
                .collect(Collectors.toList());
    }

    private List<String> getInviteTags(UUID playerUUID) {
        List<InviteData> invites = plugin.getFileManager().loadAllInvites(playerUUID);
        return invites.stream()
                .map(InviteData::getFromClan)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<String> getPendingRequestNames(ClanData clan) {
        if (clan == null) return null;
        List<String> names = new ArrayList<>();
        for (UUID reqUUID : clan.getPendingRequests()) {
            String reqName = Bukkit.getOfflinePlayer(reqUUID).getName();
            if (reqName == null) {
                reqName = reqUUID.toString().substring(0, 8);
            }
            names.add(reqName);
        }
        return names;
    }

// --- Tab completion ---

@Override
public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    if (!(sender instanceof Player)) return null;
    Player player = (Player) sender;
    UUID playerUUID = player.getUniqueId();

    if (args.length == 1) {
        ClanData clan = getPlayerClan(playerUUID);

        if ("invite".equalsIgnoreCase(args[0])) {
            List<String> inviteTargets = getInviteTargets(clan, playerUUID);
            if (inviteTargets != null) {
                return inviteTargets;
            }
        }

        List<String> subs = new ArrayList<>(Arrays.asList(
            "create", "delete", "invite", "accept", "deny", "leave",
            "kick", "promote", "demote", "leader", "rename", "info", "help", "toggle", "stats",
            "ranking", "rally", "chest", "spawn", "setspawn", "delspawn", "request", "requests",
            "logs", "skills", "quest"
        ));

        if (clan == null || !ClanSkillProgress.hasChest(clan.getSkillPoints())) {
            subs.remove("chest");
        } else if (!clan.getLeader().equals(playerUUID)
                && clan.getChestPermission(playerUUID) == ClanChestPermission.DENY) {
            subs.remove("chest");
        }

        if (clan != null && getEffectiveSkillsPermission(clan, playerUUID) == ClanAccessPermission.DENY) {
            subs.remove("skills");
        }

        if (clan == null || !ClanSkillProgress.hasSpawn(clan.getSkillPoints())
                || getEffectiveSpawnPermission(clan, playerUUID) == ClanAccessPermission.DENY) {
            subs.remove("spawn");
            subs.remove("setspawn");
            subs.remove("delspawn");
        }

        if (player.hasPermission("clan.admin")) {
            subs.add("force");
            subs.add("admin");
            subs.add("reload");
        }

        if (player.isOp()) {
            subs.add("points");
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

            case "invite": {
                return getInviteTargets(clan, playerUUID);
            }

            case "accept":
            case "deny":
            case "join": {
                List<String> inviteTags = getInviteTags(playerUUID);
                return inviteTags.isEmpty() ? null : inviteTags;
            }

            case "accept-request":
            case "deny-request": {
                if (clan != null && clan.getLeader().equals(playerUUID)) {
                    return getPendingRequestNames(clan);
                }
                return null;
            }

            case "request": {
                Map<String, ClanData> clans = plugin.getFileManager().loadAllClans();
                if (clan == null) {
                    return new ArrayList<>(clans.keySet());
                }
                String clanTag = clan.getTag();
                return clans.keySet().stream()
                        .filter(tag -> !tag.equalsIgnoreCase(clanTag))
                        .collect(Collectors.toList());
            }

            case "stats":
                return new ArrayList<>(plugin.getFileManager().loadAllClans().keySet());

            case "force":
                return Arrays.asList("kick", "delete");

            case "points":
                if (player.isOp()) {
                    return Arrays.asList("add", "remove", "set");
                }
                break;

            default:
                break;
        }
        return null;
    }

    if (args.length == 3) {
        String sub = args[0].toLowerCase();
        String sub2 = args[1].toLowerCase();

        if ("force".equals(sub)) {
            if ("kick".equals(sub2) || "leave".equals(sub2)) {
                List<String> onlineNames = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    onlineNames.add(p.getName());
                }
                return onlineNames;
            }
            if ("delete".equals(sub2)) {
                return new ArrayList<>(plugin.getFileManager().loadAllClans().keySet());
            }
        }

        if ("points".equals(sub) && player.isOp()
                && ("add".equals(sub2) || "remove".equals(sub2) || "set".equals(sub2))) {

            List<String> onlineNames = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                onlineNames.add(p.getName());
            }
            return onlineNames;
        }
    }

    return null;
}
}
