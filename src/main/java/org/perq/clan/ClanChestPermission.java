package org.perq.clan;

public enum ClanChestPermission {
    VIEW,
    EXECUTE,
    DENY;

    public static ClanChestPermission fromString(String value) {
        if (value == null) return VIEW;
        try {
            return ClanChestPermission.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return VIEW;
        }
    }

    public ClanChestPermission next() {
        switch (this) {
            case VIEW:
                return EXECUTE;
            case EXECUTE:
                return DENY;
            default:
                return VIEW;
        }
    }

    public static ClanChestPermission leaderDefault() {
        return EXECUTE;
    }
}
