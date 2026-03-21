package org.perq.clan;

import java.util.UUID;

public class InvitationData {
    private int id;
    private int clanId;
    private UUID player;
    private UUID invitedBy;

    // Constructors, getters, setters
    public InvitationData() {}

    public InvitationData(int id, int clanId, UUID player, UUID invitedBy) {
        this.id = id;
        this.clanId = clanId;
        this.player = player;
        this.invitedBy = invitedBy;
    }

    // Getters and setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getClanId() { return clanId; }
    public void setClanId(int clanId) { this.clanId = clanId; }

    public UUID getPlayer() { return player; }
    public void setPlayer(UUID player) { this.player = player; }

    public UUID getInvitedBy() { return invitedBy; }
    public void setInvitedBy(UUID invitedBy) { this.invitedBy = invitedBy; }
}
