package org.perq.clan;

public enum ClanAccessPermission {
    VIEW,
    EXECUTE,
    DENY;

    public static ClanAccessPermission fromString(String value) {
        if (value == null) return defaultPermission();
        try {
            return ClanAccessPermission.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return defaultPermission();
        }
    }

    public ClanAccessPermission next() {
        switch (this) {
            case VIEW:
                return EXECUTE;
            case EXECUTE:
                return DENY;
            default:
                return VIEW;
        }
    }

    public static ClanAccessPermission defaultPermission() {
        return EXECUTE;
    }

    public static ClanAccessPermission leaderDefault() {
        return EXECUTE;
    }
}
