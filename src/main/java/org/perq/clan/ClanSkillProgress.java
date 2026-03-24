package org.perq.clan;

public final class ClanSkillProgress {
    private static final int CHEST_UNLOCK_LEVEL = 1;
    private static final int SPAWN_UNLOCK_LEVEL = 2;
    private static final int BASE_COST = 100;
    private static final int COST_INCREMENT = 50;

    private ClanSkillProgress() {
    }

    public static int getChestUnlockLevel() {
        return CHEST_UNLOCK_LEVEL;
    }

    public static int getSpawnUnlockLevel() {
        return SPAWN_UNLOCK_LEVEL;
    }

    public static int getNextLevelCost(int currentLevel) {
        return BASE_COST + (currentLevel * COST_INCREMENT);
    }

    public static boolean hasChest(int level) {
        return level >= CHEST_UNLOCK_LEVEL;
    }

    public static boolean hasSpawn(int level) {
        return level >= SPAWN_UNLOCK_LEVEL;
    }

    public static int getBonusMemberSlots(int level) {
        return Math.max(0, level - SPAWN_UNLOCK_LEVEL);
    }

    public static String getRewardLabel(int nextLevel) {
        if (nextLevel == CHEST_UNLOCK_LEVEL) {
            return "Unlock clan chest";
        }
        if (nextLevel == SPAWN_UNLOCK_LEVEL) {
            return "Unlock clan spawn";
        }
        return "Bonus member slot +1";
    }
}
