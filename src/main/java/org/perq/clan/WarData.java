package org.perq.clan;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class WarData {
    public enum Status {
        PENDING, RUNNING, FINISHED
    }

    private final UUID id;
    private final String clan1;
    private final String clan2;
    private final Set<UUID> teamA;
    private final Set<UUID> teamB;
    private Status status;
    private long startTime;
    private String winner;
    private final UUID targetLeader;

    public WarData(String clan1, String clan2, UUID targetLeader) {
        this.id = UUID.randomUUID();
        this.clan1 = clan1;
        this.clan2 = clan2;
        this.teamA = new HashSet<>();
        this.teamB = new HashSet<>();
        this.status = Status.PENDING;
        this.startTime = 0L;
        this.winner = null;
        this.targetLeader = targetLeader;
    }

    public UUID getId() { return id; }
    public String getClan1() { return clan1; }
    public String getClan2() { return clan2; }
    public Set<UUID> getTeamA() { return teamA; }
    public Set<UUID> getTeamB() { return teamB; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    public String getWinner() { return winner; }
    public void setWinner(String winner) { this.winner = winner; }
    public UUID getTargetLeader() { return targetLeader; }
}
