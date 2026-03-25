package org.perq.clan;

public final class ClanQuestProgress {
    private static final int LEVEL_2_ZOMBIE_KILLS = 100;
    private static final int ZOMBIE_KILL_SKILL_POINTS = 2;

    private ClanQuestProgress() {
    }

    public static int getLevel2ZombieKills() {
        return LEVEL_2_ZOMBIE_KILLS;
    }

    public static int getZombieKillSkillPoints() {
        return ZOMBIE_KILL_SKILL_POINTS;
    }

    public static int getQuestSkillPoints(int zombieKills) {
        return zombieKills * ZOMBIE_KILL_SKILL_POINTS;
    }

    public static int getQuestLevel(int zombieKills) {
        return zombieKills >= LEVEL_2_ZOMBIE_KILLS ? 2 : 1;
    }
}
