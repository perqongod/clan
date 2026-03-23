package org.perq.clan;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Manages the clan war system: pending war requests, active wars, team selection, bossbar timers.
 */
public class WarManager {

    private final Clan plugin;

    /** Challenger clan tag → WarRequest */
    private final Map<String, WarRequest> pendingWarRequests = new HashMap<>();

    /** Clan tag → ActiveWar */
    private final Map<String, ActiveWar> activeWars = new HashMap<>();

    /** Player UUID → clan-tag of player's war invite */
    private final Map<UUID, String> pendingPlayerWarInvites = new HashMap<>();

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
        pendingWarRequests.put(challengerTag, new WarRequest(challengerTag, targetTag, challengerLeaderUUID));
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
        pendingWarRequests.remove(challengerTag);
    }

    // ── Active War helpers ───────────────────────────────────────────────────

    public boolean isAtWar(String clanTag) {
        return activeWars.containsKey(clanTag);
    }

    public ActiveWar getActiveWar(String clanTag) {
        return activeWars.get(clanTag);
    }

    /**
     * Starts an active war between two clans. Starts the bossbar countdown and
     * teleports participating players to the arena after 10 minutes.
     */
    public void startWar(String clanTagA, List<UUID> teamA, String clanTagB, List<UUID> teamB,
                         Location spawnA, Location spawnB) {
        ActiveWar war = new ActiveWar(clanTagA, teamA, clanTagB, teamB, spawnA, spawnB);
        activeWars.put(clanTagA, war);
        activeWars.put(clanTagB, war);

        int totalSeconds = plugin.getConfigManager().getWarArenaCountdownSeconds();
        war.setRemainingSeconds(totalSeconds);

        // Bossbar for all participants
        BossBar bossBar = BossBar.bossBar(
                Component.text("§4TIMER: " + formatTime(totalSeconds)),
                1.0f,
                BossBar.Color.RED,
                BossBar.Overlay.PROGRESS
        );
        war.setBossBar(bossBar);

        // Show bossbar and start countdown
        showBossBarToTeam(teamA, bossBar);
        showBossBarToTeam(teamB, bossBar);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int rem = war.getRemainingSeconds() - 1;
            war.setRemainingSeconds(rem);
            float progress = totalSeconds > 0 ? Math.max(0f, (float) rem / totalSeconds) : 0f;
            bossBar.name(Component.text("§4TIMER: " + formatTime(rem)));
            bossBar.progress(progress);

            if (rem <= 0) {
                // Teleport everyone to arena
                for (UUID uid : teamA) {
                    Player p = Bukkit.getPlayer(uid);
                    if (p != null && spawnA != null) p.teleport(spawnA);
                }
                for (UUID uid : teamB) {
                    Player p = Bukkit.getPlayer(uid);
                    if (p != null && spawnB != null) p.teleport(spawnB);
                }
                war.getBukkitTask().cancel();
            }
        }, 20L, 20L);
        war.setBukkitTask(task);
    }

    private void showBossBarToTeam(List<UUID> team, BossBar bar) {
        for (UUID uid : team) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) p.showBossBar(bar);
        }
    }

    public void endWar(String clanTagA, String clanTagB, String winnerTag) {
        ActiveWar war = activeWars.remove(clanTagA);
        activeWars.remove(clanTagB);
        if (war != null) {
            if (war.getBukkitTask() != null) war.getBukkitTask().cancel();
            if (war.getBossBar() != null) {
                hideBossBarFromTeam(war.getTeamA(), war.getBossBar());
                hideBossBarFromTeam(war.getTeamB(), war.getBossBar());
            }
        }
        int delta = 100;
        String loserTag = winnerTag.equals(clanTagA) ? clanTagB : clanTagA;
        adjustPoints(winnerTag, delta);
        adjustPoints(loserTag, -delta);
    }

    private void hideBossBarFromTeam(List<UUID> team, BossBar bar) {
        for (UUID uid : team) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) p.hideBossBar(bar);
        }
    }

    private void adjustPoints(String clanTag, int delta) {
        ClanData clan = plugin.getFileManager().loadClan(clanTag);
        if (clan == null) return;
        int newPts = Math.max(0, clan.getPoints() + delta);
        clan.setPoints(newPts);
        try { plugin.getFileManager().saveClan(clan); } catch (Exception ignored) {}
    }

    // ── Player war invite helpers ────────────────────────────────────────────

    public void addPlayerWarInvite(UUID playerUUID, String clanTag) {
        pendingPlayerWarInvites.put(playerUUID, clanTag);
    }

    public String getPlayerWarInvite(UUID playerUUID) {
        return pendingPlayerWarInvites.get(playerUUID);
    }

    public void removePlayerWarInvite(UUID playerUUID) {
        pendingPlayerWarInvites.remove(playerUUID);
    }

    // ── Util ─────────────────────────────────────────────────────────────────

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
    }

    public static class ActiveWar {
        private final String clanTagA;
        private final List<UUID> teamA;
        private final String clanTagB;
        private final List<UUID> teamB;
        private final Location spawnA;
        private final Location spawnB;
        private int remainingSeconds;
        private BossBar bossBar;
        private BukkitTask bukkitTask;

        public ActiveWar(String clanTagA, List<UUID> teamA, String clanTagB, List<UUID> teamB,
                         Location spawnA, Location spawnB) {
            this.clanTagA = clanTagA;
            this.teamA = teamA;
            this.clanTagB = clanTagB;
            this.teamB = teamB;
            this.spawnA = spawnA;
            this.spawnB = spawnB;
        }

        public String getClanTagA() { return clanTagA; }
        public List<UUID> getTeamA() { return teamA; }
        public String getClanTagB() { return clanTagB; }
        public List<UUID> getTeamB() { return teamB; }
        public Location getSpawnA() { return spawnA; }
        public Location getSpawnB() { return spawnB; }

        public boolean isInTeamA(UUID uuid) { return teamA.contains(uuid); }
        public boolean isInTeamB(UUID uuid) { return teamB.contains(uuid); }

        /** Returns the opponent's clan tag for the given player. */
        public String getOpponentTag(UUID uuid) {
            if (teamA.contains(uuid)) return clanTagB;
            if (teamB.contains(uuid)) return clanTagA;
            return null;
        }

        public int getRemainingSeconds() { return remainingSeconds; }
        public void setRemainingSeconds(int s) { this.remainingSeconds = s; }

        public BossBar getBossBar() { return bossBar; }
        public void setBossBar(BossBar b) { this.bossBar = b; }

        public BukkitTask getBukkitTask() { return bukkitTask; }
        public void setBukkitTask(BukkitTask t) { this.bukkitTask = t; }
    }
}
