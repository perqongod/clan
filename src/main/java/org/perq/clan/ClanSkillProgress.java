package org.perq.clan;

public final class ClanSkillProgress {
    private static final int CHEST_UNLOCK_POINTS = 100;
    private static final int SPAWN_UNLOCK_POINTS = 100;
    private static final int BANK_UNLOCK_POINTS = 200;
    private static final int BONUS_SLOT_STEP = 100;

    private ClanSkillProgress() {
    }

    public static int getChestUnlockPoints() {
        return CHEST_UNLOCK_POINTS;
    }

    public static int getSpawnUnlockPoints() {
        return SPAWN_UNLOCK_POINTS;
    }

    public static int getBankUnlockPoints() {
        return BANK_UNLOCK_POINTS;
    }

    public static boolean hasChest(int points) {
        return points >= CHEST_UNLOCK_POINTS;
    }

    public static boolean hasSpawn(int points) {
        return points >= SPAWN_UNLOCK_POINTS;
    }

    public static boolean hasBank(int points) {
        return points >= BANK_UNLOCK_POINTS;
    }

    public static int getBonusMemberSlots(int points) {
        if (points < BANK_UNLOCK_POINTS) return 0;
        return (points - BANK_UNLOCK_POINTS) / BONUS_SLOT_STEP;
    }

    public static int getNextUnlockPoints(int points) {
        if (points < CHEST_UNLOCK_POINTS) return CHEST_UNLOCK_POINTS;
        if (points < BANK_UNLOCK_POINTS) return BANK_UNLOCK_POINTS;
        return ((points / BONUS_SLOT_STEP) + 1) * BONUS_SLOT_STEP;
    }

    public static String getRewardLabel(int points) {
        if (points < CHEST_UNLOCK_POINTS) {
            return "Unlock clan chest & spawn";
        }
        if (points < BANK_UNLOCK_POINTS) {
            return "Unlock clan bank";
        }
        return "Bonus member slot +1";
    }
}
