package org.perq.clan;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.time.Duration;
import java.util.*;

/**
 * Manages the clan war system: pending war requests, active wars, team selection, confirmations.
 */
public class WarManager {

    public enum WarState {
        REQUESTED,
        SELECTING_TEAMS,
        WAITING_INVITES,
        WAITING_READY,
        TELEPORT_CONFIRM,
        COUNTDOWN,
        ACTIVE,
        ENDED,
        CANCELLED
    }

    public enum InviteStatus {
        INVITED,
        ACCEPTED,
        DECLINED
    }

    public enum TeleportStatus {
        PENDING,
        JOINED,
        DECLINED
    }

    private final Clan plugin;

    /** Challenger clan tag → WarRequest */
    private final Map<String, WarRequest> pendingWarRequests = new HashMap<>();

    /** Clan tag → ActiveWar */
    private final Map<String, ActiveWar> activeWars = new HashMap<>();

    /** Leader UUID → millisecond timestamp of last war initiation */
    private final Map<UUID, Long> warCooldowns = new HashMap<>();

    public WarManager(Clan plugin) {
        this.plugin = plugin;
    }

    // ── Cooldown helpers ─────────────────────────────────────────────────────

    public boolean isOnWarCooldown(UUID leaderUUID) {
        Long last = warCooldowns.get(leaderUUID);
        if (last == null) return false;
        long cooldownMs = plugin.getConfigManager().getWarCooldownMinutes() * 60_000L;
        return System.currentTimeMillis() - last < cooldownMs;
    }

    public long getRemainingCooldownMinutes(UUID leaderUUID) {
        Long last = warCooldowns.get(leaderUUID);
        if (last == null) return 0;
        long cooldownMs = plugin.getConfigManager().getWarCooldownMinutes() * 60_000L;
        long remaining = cooldownMs - (System.currentTimeMillis() - last);
        return remaining > 0 ? (remaining / 60_000L) + 1 : 0;
    }

    public void setWarCooldown(UUID leaderUUID) {
        warCooldowns.put(leaderUUID, System.currentTimeMillis());
    }

    // ── War Request helpers ──────────────────────────────────────────────────

    public void addWarRequest(String challengerTag, String targetTag, UUID challengerLeaderUUID) {
        WarRequest request = new WarRequest(challengerTag, targetTag, challengerLeaderUUID);
        pendingWarRequests.put(challengerTag, request);
        scheduleWarRequestTimeout(request);
    }

    public WarRequest getWarRequestByTarget(String targetTag) {
        for (WarRequest req : pendingWarRequests.values()) {
            if (req.getTargetTag().equalsIgnoreCase(targetTag)) return req;
        }
        return null;
    }

    public WarRequest getWarRequestByChallenger(String challengerTag) {
        return pendingWarRequests.get(challengerTag);
    }

    public void removeWarRequest(String challengerTag) {
        WarRequest req = pendingWarRequests.remove(challengerTag);
        if (req != null && req.getTimeoutTask() != null) {
            req.getTimeoutTask().cancel();
        }
    }

    private void scheduleWarRequestTimeout(WarRequest request) {
        int timeoutSeconds = plugin.getConfigManager().getWarRequestTimeoutSeconds();
        if (timeoutSeconds <= 0) return;
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> expireWarRequest(request.getChallengerTag()),
                timeoutSeconds * 20L);
        request.setTimeoutTask(task);
    }

    private void expireWarRequest(String challengerTag) {
        WarRequest request = pendingWarRequests.remove(challengerTag);
        if (request == null) return;
        ConfigManager cm = plugin.getConfigManager();
        Player challengerLeader = Bukkit.getPlayer(request.getChallengerLeaderUUID());
        if (challengerLeader != null) {
            challengerLeader.sendMessage(cm.getMessage("war-request-timeout-challenger"));
        }
        ClanData targetClan = plugin.getFileManager().loadClan(request.getTargetTag());
        if (targetClan != null) {
            Player targetLeader = Bukkit.getPlayer(targetClan.getLeader());
            if (targetLeader != null) {
                targetLeader.sendMessage(cm.getMessage("war-request-timeout-target"));
            }
        }
    }

    // ── Active War helpers ───────────────────────────────────────────────────

    public boolean isAtWar(String clanTag) {
        return activeWars.containsKey(clanTag);
    }

    public ActiveWar getActiveWar(String clanTag) {
        return activeWars.get(clanTag);
    }

    public ActiveWar getActiveWarByPlayer(UUID playerUUID) {
        String clanTag = getPlayerClanTag(playerUUID);
        if (clanTag == null) return null;
        return activeWars.get(clanTag);
    }

    public boolean isPlayerInActiveWar(UUID playerUUID) {
        ActiveWar war = getActiveWarByPlayer(playerUUID);
        return war != null && (war.getState() == WarState.COUNTDOWN || war.getState() == WarState.ACTIVE);
    }

    public boolean canSelectTeams(ActiveWar war) {
        return war.getState() == WarState.SELECTING_TEAMS || war.getState() == WarState.WAITING_INVITES;
    }

    /**
     * Starts a war preparation between two clans. Players must confirm before teleporting.
     */
    public ActiveWar startWar(String clanTagA, List<UUID> membersA, String clanTagB, List<UUID> membersB,
                              Location spawnA, Location spawnB) {
        ClanData clanA = plugin.getFileManager().loadClan(clanTagA);
        ClanData clanB = plugin.getFileManager().loadClan(clanTagB);
        UUID leaderA = clanA != null ? clanA.getLeader() : null;
        UUID leaderB = clanB != null ? clanB.getLeader() : null;
        ActiveWar war = new ActiveWar(clanTagA, leaderA, membersA, clanTagB, leaderB, membersB, spawnA, spawnB);
        war.setState(WarState.SELECTING_TEAMS);
        activeWars.put(clanTagA, war);
        activeWars.put(clanTagB, war);
        return war;
    }

    public void selectFighter(ActiveWar war, String clanTag, UUID member) {
        if (war == null || !canSelectTeams(war)) return;
        if (!war.isMemberOfClan(clanTag, member)) return;
        if (war.isSelected(member)) return;
        war.addSelected(clanTag, member);
        war.setInviteStatus(member, InviteStatus.INVITED);
        war.setState(WarState.WAITING_INVITES);
        scheduleInviteTimeout(war, member);
        sendInvitePrompt(war, member);
        plugin.getWarTeamSelectionListener().refreshWar(war);
    }

    public void deselectFighter(ActiveWar war, String clanTag, UUID member) {
        if (war == null || !canSelectTeams(war)) return;
        if (!war.isMemberOfClan(clanTag, member)) return;
        war.removeSelected(member);
        clearParticipantState(war, member);
        if (war.getState() == WarState.WAITING_INVITES && !war.hasSelectedPlayers()) {
            war.setState(WarState.SELECTING_TEAMS);
        }
        plugin.getWarTeamSelectionListener().refreshWar(war);
    }

    public void handleInviteResponse(UUID playerUUID, boolean accepted) {
        ActiveWar war = getActiveWarByPlayer(playerUUID);
        if (war == null || !war.isSelected(playerUUID)) return;
        cancelInviteTimeout(war, playerUUID);
        if (accepted) {
            war.setInviteStatus(playerUUID, InviteStatus.ACCEPTED);
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
        } else {
            war.setInviteStatus(playerUUID, InviteStatus.DECLINED);
            war.removeReady(playerUUID);
        }
        plugin.getWarTeamSelectionListener().refreshWar(war);
        checkInvitePhaseComplete(war);
    }

    public void handleReadyResponse(UUID playerUUID, boolean ready) {
        ActiveWar war = getActiveWarByPlayer(playerUUID);
        if (war == null || war.getInviteStatus(playerUUID) != InviteStatus.ACCEPTED) return;
        cancelReadyTimeout(war, playerUUID);
        if (ready) {
            war.addReady(playerUUID);
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            }
        } else {
            war.setInviteStatus(playerUUID, InviteStatus.DECLINED);
            war.removeReady(playerUUID);
        }
        plugin.getWarTeamSelectionListener().refreshWar(war);
        checkReadyPhaseComplete(war);
    }

    public void handleTeleportResponse(UUID playerUUID, boolean join) {
        ActiveWar war = getActiveWarByPlayer(playerUUID);
        if (war == null || war.getState() != WarState.TELEPORT_CONFIRM) return;
        if (!war.isReady(playerUUID)) return;
        cancelTeleportTimeout(war, playerUUID);
        if (join) {
            war.setTeleportStatus(playerUUID, TeleportStatus.JOINED);
        } else {
            war.setTeleportStatus(playerUUID, TeleportStatus.DECLINED);
            war.setInviteStatus(playerUUID, InviteStatus.DECLINED);
            war.removeReady(playerUUID);
        }
        plugin.getWarTeamSelectionListener().refreshWar(war);
        checkTeleportPhaseComplete(war);
    }

    public void handlePlayerDeath(Player victim, Player killer) {
        ActiveWar war = getActiveWarByPlayer(victim.getUniqueId());
        if (war == null || war.getState() != WarState.ACTIVE) return;
        if (!war.isActivePlayer(victim.getUniqueId())) return;
        war.markDead(victim.getUniqueId(), victim.getLocation());
        if (killer != null && war.isActivePlayer(killer.getUniqueId())
                && war.isOpposingTeams(killer.getUniqueId(), victim.getUniqueId())) {
            war.incrementKills(killer.getUniqueId());
        }
        updateScoreboard(war);
        checkForWinner(war);
    }

    public void handleDisconnect(UUID playerUUID) {
        ActiveWar war = getActiveWarByPlayer(playerUUID);
        if (war == null) return;
        if (war.getState() == WarState.ACTIVE || war.getState() == WarState.COUNTDOWN) {
            if (war.isActivePlayer(playerUUID)) {
                war.markDead(playerUUID, null);
                updateScoreboard(war);
                checkForWinner(war);
            }
            return;
        }
        if (war.isSelected(playerUUID)) {
            war.setInviteStatus(playerUUID, InviteStatus.DECLINED);
            war.removeReady(playerUUID);
            cancelInviteTimeout(war, playerUUID);
            cancelReadyTimeout(war, playerUUID);
            cancelTeleportTimeout(war, playerUUID);
            plugin.getWarTeamSelectionListener().refreshWar(war);
            checkInvitePhaseComplete(war);
            checkReadyPhaseComplete(war);
            checkTeleportPhaseComplete(war);
        }
    }

    public void endWar(String clanTagA, String clanTagB, String winnerTag) {
        ActiveWar war = activeWars.remove(clanTagA);
        activeWars.remove(clanTagB);
        if (war != null) {
            war.setState(WarState.ENDED);
            showOutcomeTitles(war, winnerTag);
            war.cancelTasks();
            hideScoreboard(war);
            teleportBack(war);
            plugin.getWarTeamSelectionListener().closeSessionsForWar(war);
        }
        int delta = 100;
        String loserTag = winnerTag.equals(clanTagA) ? clanTagB : clanTagA;
        adjustPoints(winnerTag, delta);
        adjustPoints(loserTag, -delta);
        notifyClan(clanTagA, winnerTag.equals(clanTagA));
        notifyClan(clanTagB, winnerTag.equals(clanTagB));
    }

    // ── Invite/Ready/Teleport phases ─────────────────────────────────────────

    private void scheduleInviteTimeout(ActiveWar war, UUID playerUUID) {
        int timeoutSeconds = plugin.getConfigManager().getWarInviteTimeoutSeconds();
        if (timeoutSeconds <= 0) return;
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (war.getInviteStatus(playerUUID) == InviteStatus.INVITED) {
                war.setInviteStatus(playerUUID, InviteStatus.DECLINED);
                plugin.getWarTeamSelectionListener().refreshWar(war);
                checkInvitePhaseComplete(war);
            }
        }, timeoutSeconds * 20L);
        war.setInviteTimeout(playerUUID, task);
    }

    private void cancelInviteTimeout(ActiveWar war, UUID playerUUID) {
        BukkitTask task = war.removeInviteTimeout(playerUUID);
        if (task != null) task.cancel();
    }

    private void scheduleReadyTimeout(ActiveWar war, UUID playerUUID) {
        int timeoutSeconds = plugin.getConfigManager().getWarReadyTimeoutSeconds();
        if (timeoutSeconds <= 0) return;
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (war.getInviteStatus(playerUUID) == InviteStatus.ACCEPTED && !war.isReady(playerUUID)) {
                war.setInviteStatus(playerUUID, InviteStatus.DECLINED);
                war.removeReady(playerUUID);
                plugin.getWarTeamSelectionListener().refreshWar(war);
                checkReadyPhaseComplete(war);
            }
        }, timeoutSeconds * 20L);
        war.setReadyTimeout(playerUUID, task);
    }

    private void cancelReadyTimeout(ActiveWar war, UUID playerUUID) {
        BukkitTask task = war.removeReadyTimeout(playerUUID);
        if (task != null) task.cancel();
    }

    private void scheduleTeleportTimeout(ActiveWar war, UUID playerUUID) {
        int timeoutSeconds = plugin.getConfigManager().getWarTeleportTimeoutSeconds();
        if (timeoutSeconds <= 0) return;
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (war.getTeleportStatus(playerUUID) == TeleportStatus.PENDING) {
                war.setTeleportStatus(playerUUID, TeleportStatus.DECLINED);
                war.setInviteStatus(playerUUID, InviteStatus.DECLINED);
                war.removeReady(playerUUID);
                plugin.getWarTeamSelectionListener().refreshWar(war);
                checkTeleportPhaseComplete(war);
            }
        }, timeoutSeconds * 20L);
        war.setTeleportTimeout(playerUUID, task);
    }

    private void cancelTeleportTimeout(ActiveWar war, UUID playerUUID) {
        BukkitTask task = war.removeTeleportTimeout(playerUUID);
        if (task != null) task.cancel();
    }

    private void checkInvitePhaseComplete(ActiveWar war) {
        if (war.getState() != WarState.WAITING_INVITES) return;
        if (war.hasPendingInvites()) return;
        if (!war.hasAcceptedPlayers()) {
            cancelWar(war, plugin.getConfigManager().getMessage("war-cancelled-no-players"));
            return;
        }
        startReadyPhase(war);
    }

    private void startReadyPhase(ActiveWar war) {
        war.setState(WarState.WAITING_READY);
        for (UUID uuid : war.getAcceptedPlayers()) {
            sendReadyPrompt(war, uuid);
            scheduleReadyTimeout(war, uuid);
        }
    }

    private void checkReadyPhaseComplete(ActiveWar war) {
        if (war.getState() != WarState.WAITING_READY) return;
        if (!war.areAllAcceptedReady()) return;
        if (!war.hasReadyPlayers()) {
            cancelWar(war, plugin.getConfigManager().getMessage("war-cancelled-no-ready"));
            return;
        }
        startTeleportPhase(war);
    }

    private void startTeleportPhase(ActiveWar war) {
        war.setState(WarState.TELEPORT_CONFIRM);
        for (UUID uuid : war.getReadyPlayers()) {
            war.setTeleportStatus(uuid, TeleportStatus.PENDING);
            sendTeleportPrompt(war, uuid);
            scheduleTeleportTimeout(war, uuid);
        }
    }

    private void checkTeleportPhaseComplete(ActiveWar war) {
        if (war.getState() != WarState.TELEPORT_CONFIRM) return;
        if (war.hasPendingTeleports()) return;
        if (!war.hasJoinedPlayers()) {
            cancelWar(war, plugin.getConfigManager().getMessage("war-cancelled-no-players"));
            return;
        }
        teleportAndCountdown(war);
    }

    private void teleportAndCountdown(ActiveWar war) {
        war.setState(WarState.COUNTDOWN);
        for (UUID uuid : war.getJoinedPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            war.rememberReturnLocation(uuid, player.getLocation());
            Location target = war.isInTeamA(uuid) ? war.getSpawnA() : war.getSpawnB();
            if (target != null) {
                player.teleport(target);
            }
        }
        startCountdown(war);
    }

    private void startCountdown(ActiveWar war) {
        int totalSeconds = plugin.getConfigManager().getWarStartCountdownSeconds();
        war.setRemainingSeconds(totalSeconds);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int rem = war.getRemainingSeconds();
            if (rem <= 0) {
                war.getCountdownTask().cancel();
                startCombat(war);
                return;
            }
            for (UUID uuid : war.getJoinedPlayers()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) continue;
                player.sendTitle("§c" + rem, plugin.getConfigManager().getMessage("war-countdown-subtitle"), 0, 20, 0);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
            }
            int next = rem - 1;
            war.setRemainingSeconds(next);
            if (next <= 0) {
                war.getCountdownTask().cancel();
                startCombat(war);
            }
        }, 20L, 20L);
        war.setCountdownTask(task);
    }

    private void startCombat(ActiveWar war) {
        war.setState(WarState.ACTIVE);
        war.setWarStartMillis(System.currentTimeMillis());
        for (UUID uuid : war.getJoinedPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendTitle("⚔ WAR STARTET!", plugin.getConfigManager().getMessage("war-start-subtitle"), 0, 40, 10);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                war.addActivePlayer(uuid);
            }
        }
        startScoreboard(war);
    }

    private void checkForWinner(ActiveWar war) {
        if (!war.hasAlivePlayers()) {
            cancelWar(war, plugin.getConfigManager().getMessage("war-cancelled-no-players"));
            return;
        }
        if (war.getAliveCountA() == 0) {
            endWar(war.getClanTagA(), war.getClanTagB(), war.getClanTagB());
        } else if (war.getAliveCountB() == 0) {
            endWar(war.getClanTagA(), war.getClanTagB(), war.getClanTagA());
        }
    }

    private void cancelWar(ActiveWar war, String message) {
        activeWars.remove(war.getClanTagA());
        activeWars.remove(war.getClanTagB());
        war.setState(WarState.CANCELLED);
        war.cancelTasks();
        hideScoreboard(war);
        teleportBack(war);
        plugin.getWarTeamSelectionListener().closeSessionsForWar(war);
        notifyClanMessage(war.getClanTagA(), message);
        notifyClanMessage(war.getClanTagB(), message);
    }

    private void clearParticipantState(ActiveWar war, UUID member) {
        cancelInviteTimeout(war, member);
        cancelReadyTimeout(war, member);
        cancelTeleportTimeout(war, member);
        war.removeReady(member);
        war.clearInviteStatus(member);
        war.clearTeleportStatus(member);
    }

    // ── Scoreboard ───────────────────────────────────────────────────────────

    private void startScoreboard(ActiveWar war) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective("clanwar", "dummy", Component.text("Clan War"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        war.setScoreboard(board);
        war.setScoreboardObjective(objective);
        updateScoreboard(war);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> updateScoreboard(war), 20L, 20L);
        war.setScoreboardTask(task);
        for (UUID uuid : war.getJoinedPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setScoreboard(board);
            }
        }
    }

    private void updateScoreboard(ActiveWar war) {
        Scoreboard board = war.getScoreboard();
        Objective objective = war.getScoreboardObjective();
        if (board == null || objective == null) return;
        for (String entry : board.getEntries()) {
            board.resetScores(entry);
        }
        List<String> lines = new ArrayList<>();
        lines.add("§7" + war.getClanTagA() + " §fvs §7" + war.getClanTagB());
        lines.add("§1 ");
        lines.add("§aAlive " + war.getClanTagA() + ": §f" + war.getAliveCountA());
        lines.add("§bAlive " + war.getClanTagB() + ": §f" + war.getAliveCountB());
        lines.add("§2 ");
        lines.add("§cKills " + war.getClanTagA() + ": §f" + war.getKillsA());
        lines.add("§dKills " + war.getClanTagB() + ": §f" + war.getKillsB());
        lines.add("§3 ");
        int elapsed = war.getWarStartMillis() > 0
                ? (int) Duration.ofMillis(System.currentTimeMillis() - war.getWarStartMillis()).getSeconds()
                : 0;
        lines.add("§eTime: §f" + formatTime(elapsed));
        int score = lines.size();
        for (String line : lines) {
            objective.getScore(line).setScore(score--);
        }
    }

    private void hideScoreboard(ActiveWar war) {
        if (war.getScoreboardTask() != null) {
            war.getScoreboardTask().cancel();
        }
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard main = manager != null ? manager.getMainScoreboard() : null;
        Set<UUID> participants = new HashSet<>(war.getJoinedPlayers());
        participants.addAll(war.getActivePlayers());
        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && main != null) {
                player.setScoreboard(main);
            }
        }
    }

    // ── Messaging ────────────────────────────────────────────────────────────

    private void sendInvitePrompt(ActiveWar war, UUID playerUUID) {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null) return;
        String opponent = war.getOpponentTag(playerUUID);
        String question = plugin.getConfigManager().getMessage("war-player-invite")
                .replace("%tag%", opponent != null ? opponent : "?");
        sendDecisionMessage(player, question, "TEILNEHMEN", "ABLEHNEN",
                "/clan war invite-accept", "/clan war invite-deny");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
    }

    private void sendReadyPrompt(ActiveWar war, UUID playerUUID) {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null) return;
        String question = plugin.getConfigManager().getMessage("war-ready-question");
        sendDecisionMessage(player, question, "BEREIT", "NICHT BEREIT",
                "/clan war ready", "/clan war not-ready");
    }

    private void sendTeleportPrompt(ActiveWar war, UUID playerUUID) {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null) return;
        String question = plugin.getConfigManager().getMessage("war-teleport-question");
        sendDecisionMessage(player, question, "JOIN WAR", "NICHT TEILNEHMEN",
                "/clan war teleport-accept", "/clan war teleport-deny");
    }

    private void sendDecisionMessage(Player player, String question, String acceptLabel, String denyLabel,
                                     String acceptCommand, String denyCommand) {
        player.sendMessage(question);
        Component buttons = Component.text("[✔ " + acceptLabel + "]")
                .color(TextColor.color(0x55FF55))
                .clickEvent(ClickEvent.runCommand(acceptCommand))
                .append(Component.text("   ").color(TextColor.color(0xAAAAAA)))
                .append(Component.text("[✖ " + denyLabel + "]")
                        .color(TextColor.color(0xFF5555))
                        .clickEvent(ClickEvent.runCommand(denyCommand)));
        player.sendMessage(buttons);
    }

    private void notifyClan(String clanTag, boolean winner) {
        String msg = winner
                ? plugin.getConfigManager().getMessage("war-won")
                : plugin.getConfigManager().getMessage("war-lost");
        notifyClanMessage(clanTag, msg);
    }

    private void showOutcomeTitles(ActiveWar war, String winnerTag) {
        for (UUID uuid : war.getReturnLocations().keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            boolean winner = war.isInTeamA(uuid) ? winnerTag.equals(war.getClanTagA()) : winnerTag.equals(war.getClanTagB());
            if (winner) {
                player.sendTitle("🏆 DU HAST GEWONNEN!", "", 0, 60, 10);
            } else {
                player.sendTitle("💀 DU HAST VERLOREN!", "", 0, 60, 10);
            }
        }
    }

    private void notifyClanMessage(String clanTag, String message) {
        ClanData clan = plugin.getFileManager().loadClan(clanTag);
        if (clan == null) return;
        for (UUID member : clan.getMembers()) {
            Player player = Bukkit.getPlayer(member);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    // ── Util ─────────────────────────────────────────────────────────────────

    private void adjustPoints(String clanTag, int delta) {
        ClanData clan = plugin.getFileManager().loadClan(clanTag);
        if (clan == null) return;
        int newPts = Math.max(0, clan.getPoints() + delta);
        clan.setPoints(newPts);
        clan.setRank(plugin.getConfigManager().getRankForPoints(newPts));
        try { plugin.getFileManager().saveClan(clan); } catch (Exception ignored) {}
    }

    private void teleportBack(ActiveWar war) {
        for (Map.Entry<UUID, Location> entry : war.getReturnLocations().entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.teleport(entry.getValue());
                player.setGameMode(GameMode.SURVIVAL);
            }
        }
    }

    private String getPlayerClanTag(UUID playerUUID) {
        PlayerData p = plugin.getFileManager().loadPlayer(playerUUID);
        return (p == null) ? null : p.getClanTag();
    }

    private static String formatTime(int totalSeconds) {
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        return String.format("%d:%02d", m, s);
    }

    // ── Inner classes ─────────────────────────────────────────────────────────

    public static class WarRequest {
        private final String challengerTag;
        private final String targetTag;
        private final UUID challengerLeaderUUID;
        private final long createdAt;
        private BukkitTask timeoutTask;

        public WarRequest(String challengerTag, String targetTag, UUID challengerLeaderUUID) {
            this.challengerTag = challengerTag;
            this.targetTag = targetTag;
            this.challengerLeaderUUID = challengerLeaderUUID;
            this.createdAt = System.currentTimeMillis();
        }

        public String getChallengerTag() { return challengerTag; }
        public String getTargetTag() { return targetTag; }
        public UUID getChallengerLeaderUUID() { return challengerLeaderUUID; }
        public long getCreatedAt() { return createdAt; }
        public BukkitTask getTimeoutTask() { return timeoutTask; }
        public void setTimeoutTask(BukkitTask timeoutTask) { this.timeoutTask = timeoutTask; }
    }

    public static class ActiveWar {
        private final String clanTagA;
        private final UUID leaderA;
        private final List<UUID> membersA;
        private final String clanTagB;
        private final UUID leaderB;
        private final List<UUID> membersB;
        private final Location spawnA;
        private final Location spawnB;
        private final Set<UUID> selectedA = new HashSet<>();
        private final Set<UUID> selectedB = new HashSet<>();
        private final Map<UUID, InviteStatus> inviteStatus = new HashMap<>();
        private final Set<UUID> readyPlayers = new HashSet<>();
        private final Map<UUID, TeleportStatus> teleportStatus = new HashMap<>();
        private final Set<UUID> activePlayers = new HashSet<>();
        private final Set<UUID> deadPlayers = new HashSet<>();
        private final Map<UUID, Location> returnLocations = new HashMap<>();
        private final Map<UUID, Location> deathLocations = new HashMap<>();
        private final Map<UUID, BukkitTask> inviteTimeouts = new HashMap<>();
        private final Map<UUID, BukkitTask> readyTimeouts = new HashMap<>();
        private final Map<UUID, BukkitTask> teleportTimeouts = new HashMap<>();
        private int remainingSeconds;
        private int killsA;
        private int killsB;
        private WarState state;
        private long warStartMillis;
        private BukkitTask countdownTask;
        private BukkitTask scoreboardTask;
        private Scoreboard scoreboard;
        private Objective scoreboardObjective;

        public ActiveWar(String clanTagA, UUID leaderA, List<UUID> membersA, String clanTagB, UUID leaderB,
                         List<UUID> membersB, Location spawnA, Location spawnB) {
            this.clanTagA = clanTagA;
            this.leaderA = leaderA;
            this.membersA = new ArrayList<>(membersA);
            this.clanTagB = clanTagB;
            this.leaderB = leaderB;
            this.membersB = new ArrayList<>(membersB);
            this.spawnA = spawnA;
            this.spawnB = spawnB;
        }

        public String getClanTagA() { return clanTagA; }
        public UUID getLeaderA() { return leaderA; }
        public List<UUID> getMembersA() { return new ArrayList<>(membersA); }
        public String getClanTagB() { return clanTagB; }
        public UUID getLeaderB() { return leaderB; }
        public List<UUID> getMembersB() { return new ArrayList<>(membersB); }
        public Location getSpawnA() { return spawnA; }
        public Location getSpawnB() { return spawnB; }
        public WarState getState() { return state; }
        public void setState(WarState state) { this.state = state; }
        public int getRemainingSeconds() { return remainingSeconds; }
        public void setRemainingSeconds(int s) { this.remainingSeconds = s; }
        public BukkitTask getCountdownTask() { return countdownTask; }
        public void setCountdownTask(BukkitTask task) { this.countdownTask = task; }
        public long getWarStartMillis() { return warStartMillis; }
        public void setWarStartMillis(long warStartMillis) { this.warStartMillis = warStartMillis; }
        public Scoreboard getScoreboard() { return scoreboard; }
        public void setScoreboard(Scoreboard scoreboard) { this.scoreboard = scoreboard; }
        public Objective getScoreboardObjective() { return scoreboardObjective; }
        public void setScoreboardObjective(Objective objective) { this.scoreboardObjective = objective; }
        public BukkitTask getScoreboardTask() { return scoreboardTask; }
        public void setScoreboardTask(BukkitTask task) { this.scoreboardTask = task; }

        public boolean isMemberOfClan(String clanTag, UUID uuid) {
            if (clanTagA.equalsIgnoreCase(clanTag)) return membersA.contains(uuid);
            if (clanTagB.equalsIgnoreCase(clanTag)) return membersB.contains(uuid);
            return false;
        }

        public boolean isSelected(UUID uuid) {
            return selectedA.contains(uuid) || selectedB.contains(uuid);
        }

        public boolean hasSelectedPlayers() {
            return !selectedA.isEmpty() || !selectedB.isEmpty();
        }

        public void addSelected(String clanTag, UUID uuid) {
            if (clanTagA.equalsIgnoreCase(clanTag)) {
                selectedA.add(uuid);
            } else if (clanTagB.equalsIgnoreCase(clanTag)) {
                selectedB.add(uuid);
            }
        }

        public void removeSelected(UUID uuid) {
            selectedA.remove(uuid);
            selectedB.remove(uuid);
        }

        public List<UUID> getSelectedForClan(String clanTag) {
            if (clanTagA.equalsIgnoreCase(clanTag)) return new ArrayList<>(selectedA);
            if (clanTagB.equalsIgnoreCase(clanTag)) return new ArrayList<>(selectedB);
            return new ArrayList<>();
        }

        public List<UUID> getNotSelectedForClan(String clanTag) {
            List<UUID> members = clanTagA.equalsIgnoreCase(clanTag) ? membersA : membersB;
            List<UUID> result = new ArrayList<>();
            for (UUID uuid : members) {
                if (!isSelected(uuid)) {
                    result.add(uuid);
                }
            }
            return result;
        }

        public boolean isInTeamA(UUID uuid) { return selectedA.contains(uuid); }
        public boolean isInTeamB(UUID uuid) { return selectedB.contains(uuid); }

        public boolean isOpposingTeams(UUID a, UUID b) {
            return (isInTeamA(a) && isInTeamB(b)) || (isInTeamB(a) && isInTeamA(b));
        }

        public String getOpponentTag(UUID uuid) {
            if (selectedA.contains(uuid) || membersA.contains(uuid)) return clanTagB;
            if (selectedB.contains(uuid) || membersB.contains(uuid)) return clanTagA;
            return null;
        }

        public InviteStatus getInviteStatus(UUID uuid) {
            return inviteStatus.getOrDefault(uuid, InviteStatus.INVITED);
        }

        public void setInviteStatus(UUID uuid, InviteStatus status) {
            inviteStatus.put(uuid, status);
        }

        public void clearInviteStatus(UUID uuid) {
            inviteStatus.remove(uuid);
        }

        public Set<UUID> getAcceptedPlayers() {
            Set<UUID> result = new HashSet<>();
            for (Map.Entry<UUID, InviteStatus> entry : inviteStatus.entrySet()) {
                if (entry.getValue() == InviteStatus.ACCEPTED) {
                    result.add(entry.getKey());
                }
            }
            return result;
        }

        public boolean hasAcceptedPlayers() {
            boolean hasA = false;
            boolean hasB = false;
            for (Map.Entry<UUID, InviteStatus> entry : inviteStatus.entrySet()) {
                if (entry.getValue() != InviteStatus.ACCEPTED) continue;
                if (selectedA.contains(entry.getKey())) hasA = true;
                if (selectedB.contains(entry.getKey())) hasB = true;
            }
            return hasA && hasB;
        }

        public boolean hasPendingInvites() {
            return inviteStatus.values().stream().anyMatch(status -> status == InviteStatus.INVITED);
        }

        public void addReady(UUID uuid) { readyPlayers.add(uuid); }
        public void removeReady(UUID uuid) { readyPlayers.remove(uuid); }
        public boolean isReady(UUID uuid) { return readyPlayers.contains(uuid); }
        public Set<UUID> getReadyPlayers() { return new HashSet<>(readyPlayers); }

        public boolean areAllAcceptedReady() {
            for (UUID uuid : getAcceptedPlayers()) {
                if (!readyPlayers.contains(uuid)) return false;
            }
            return true;
        }

        public boolean hasReadyPlayers() {
            boolean hasA = false;
            boolean hasB = false;
            for (UUID uuid : readyPlayers) {
                if (selectedA.contains(uuid)) hasA = true;
                if (selectedB.contains(uuid)) hasB = true;
            }
            return hasA && hasB;
        }

        public void setTeleportStatus(UUID uuid, TeleportStatus status) {
            teleportStatus.put(uuid, status);
        }

        public TeleportStatus getTeleportStatus(UUID uuid) {
            return teleportStatus.getOrDefault(uuid, TeleportStatus.PENDING);
        }

        public void clearTeleportStatus(UUID uuid) {
            teleportStatus.remove(uuid);
        }

        public boolean hasPendingTeleports() {
            for (UUID uuid : readyPlayers) {
                if (teleportStatus.getOrDefault(uuid, TeleportStatus.PENDING) == TeleportStatus.PENDING) {
                    return true;
                }
            }
            return false;
        }

        public Set<UUID> getJoinedPlayers() {
            Set<UUID> result = new HashSet<>();
            for (UUID uuid : readyPlayers) {
                if (teleportStatus.get(uuid) == TeleportStatus.JOINED) {
                    result.add(uuid);
                }
            }
            return result;
        }

        public boolean hasJoinedPlayers() {
            boolean hasA = false;
            boolean hasB = false;
            for (UUID uuid : getJoinedPlayers()) {
                if (selectedA.contains(uuid)) hasA = true;
                if (selectedB.contains(uuid)) hasB = true;
            }
            return hasA && hasB;
        }

        public void addActivePlayer(UUID uuid) {
            activePlayers.add(uuid);
        }

        public boolean isActivePlayer(UUID uuid) {
            return activePlayers.contains(uuid);
        }

        public boolean isJoinedPlayer(UUID uuid) {
            return getJoinedPlayers().contains(uuid) || activePlayers.contains(uuid);
        }

        public Set<UUID> getActivePlayers() { return new HashSet<>(activePlayers); }

        public void markDead(UUID uuid, Location deathLocation) {
            activePlayers.remove(uuid);
            deadPlayers.add(uuid);
            if (deathLocation != null) {
                deathLocations.put(uuid, deathLocation);
            }
        }

        public boolean isDead(UUID uuid) {
            return deadPlayers.contains(uuid);
        }

        public Location getDeathLocation(UUID uuid) {
            return deathLocations.get(uuid);
        }

        public int getAliveCountA() {
            int count = 0;
            for (UUID uuid : activePlayers) {
                if (selectedA.contains(uuid)) count++;
            }
            return count;
        }

        public int getAliveCountB() {
            int count = 0;
            for (UUID uuid : activePlayers) {
                if (selectedB.contains(uuid)) count++;
            }
            return count;
        }

        public boolean hasAlivePlayers() {
            return !activePlayers.isEmpty();
        }

        public void incrementKills(UUID killer) {
            if (selectedA.contains(killer)) {
                killsA++;
            } else if (selectedB.contains(killer)) {
                killsB++;
            }
        }

        public int getKillsA() { return killsA; }
        public int getKillsB() { return killsB; }

        public void rememberReturnLocation(UUID uuid, Location location) {
            if (location != null) {
                returnLocations.put(uuid, location);
            }
        }

        public Location getReturnLocation(UUID uuid) {
            return returnLocations.get(uuid);
        }

        public Map<UUID, Location> getReturnLocations() {
            return new HashMap<>(returnLocations);
        }

        public void setInviteTimeout(UUID uuid, BukkitTask task) {
            inviteTimeouts.put(uuid, task);
        }

        public BukkitTask removeInviteTimeout(UUID uuid) {
            return inviteTimeouts.remove(uuid);
        }

        public void setReadyTimeout(UUID uuid, BukkitTask task) {
            readyTimeouts.put(uuid, task);
        }

        public BukkitTask removeReadyTimeout(UUID uuid) {
            return readyTimeouts.remove(uuid);
        }

        public void setTeleportTimeout(UUID uuid, BukkitTask task) {
            teleportTimeouts.put(uuid, task);
        }

        public BukkitTask removeTeleportTimeout(UUID uuid) {
            return teleportTimeouts.remove(uuid);
        }

        public void cancelTasks() {
            if (countdownTask != null) countdownTask.cancel();
            if (scoreboardTask != null) scoreboardTask.cancel();
            for (BukkitTask task : inviteTimeouts.values()) {
                task.cancel();
            }
            for (BukkitTask task : readyTimeouts.values()) {
                task.cancel();
            }
            for (BukkitTask task : teleportTimeouts.values()) {
                task.cancel();
            }
            inviteTimeouts.clear();
            readyTimeouts.clear();
            teleportTimeouts.clear();
        }
    }
}
