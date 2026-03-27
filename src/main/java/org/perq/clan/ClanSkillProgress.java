package org.perq.clan;

import java.util.ArrayList;
import java.util.List;

public final class ClanSkillProgress {
    private static final int CHEST_UNLOCK_POINTS = 100;
    private static final int SPAWN_UNLOCK_POINTS = 200;
    private static final int RENAME_UNLOCK_POINTS = 300;
    private static final int ENDER_PEARL_UNLOCK_POINTS = 400;
    private static final int BONUS_SLOT_STEP = 100;

    private ClanSkillProgress() {
    }

    public static int getChestUnlockPoints() {
        return CHEST_UNLOCK_POINTS;
    }

    public static int getSpawnUnlockPoints() {
        return SPAWN_UNLOCK_POINTS;
    }

    public static int getRenameUnlockPoints() {
        return RENAME_UNLOCK_POINTS;
    }

    public static int getEnderPearlUnlockPoints() {
        return ENDER_PEARL_UNLOCK_POINTS;
    }

    public static boolean hasChest(int points) {
        return points >= CHEST_UNLOCK_POINTS;
    }

    public static boolean hasSpawn(int points) {
        return points >= SPAWN_UNLOCK_POINTS;
    }

    public static boolean hasRename(int points) {
        return points >= RENAME_UNLOCK_POINTS;
    }

    public static boolean hasEnderPearlProtection(int points) {
        return points >= ENDER_PEARL_UNLOCK_POINTS;
    }

    public static int getBonusMemberSlots(int points) {
        if (points < SPAWN_UNLOCK_POINTS) return 0;
        return (points - SPAWN_UNLOCK_POINTS) / BONUS_SLOT_STEP;
    }

    public static int getBonusSlotStep() {
        return BONUS_SLOT_STEP;
    }

    public static int getNextUnlockPoints(int points) {
        int[] unlocks = {CHEST_UNLOCK_POINTS, SPAWN_UNLOCK_POINTS, RENAME_UNLOCK_POINTS,
                ENDER_PEARL_UNLOCK_POINTS};
        for (int unlock : unlocks) {
            if (points < unlock) return unlock;
        }
        return ((points / BONUS_SLOT_STEP) + 1) * BONUS_SLOT_STEP;
    }

    public static String getRewardLabel(int points) {
        int nextUnlock = getNextUnlockPoints(points);
        List<String> rewards = new ArrayList<>();
        if (nextUnlock == CHEST_UNLOCK_POINTS) {
            rewards.add("clan chest");
        }
        if (nextUnlock == SPAWN_UNLOCK_POINTS) {
            rewards.add("clan spawn");
        }
        if (nextUnlock == RENAME_UNLOCK_POINTS) {
            rewards.add("clan rename");
        }
        if (nextUnlock == ENDER_PEARL_UNLOCK_POINTS) {
            rewards.add("ender pearl shield");
        }
        if (rewards.isEmpty()) {
            return "Bonus member slot +1";
        }
        return "Unlock " + String.join(" & ", rewards);
    }
}
