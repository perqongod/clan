package org.perq.clan;

public enum ClanFriendlyFirePermission {
    ALLOW,
    DENY;

    public static ClanFriendlyFirePermission fromString(String value) {
        if (value == null) return ALLOW;
        try {
            return ClanFriendlyFirePermission.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ALLOW;
        }
    }

    public ClanFriendlyFirePermission next() {
        return this == ALLOW ? DENY : ALLOW;
    }

    public static ClanFriendlyFirePermission leaderDefault() {
        return ALLOW;
    }
}
