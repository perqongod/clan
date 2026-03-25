package org.perq.clan;

/**
 * Access levels for clan feature commands.
 * VIEW: visible in help/tab but execution blocked.
 * EXECUTE: visible and executable.
 * DENY: hidden in help/tab and execution blocked.
 */
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
