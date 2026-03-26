package org.perq.clan;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClanQuestProgress {
    public enum QuestTarget {
        ZOMBIE(EntityType.ZOMBIE, Material.ZOMBIE_HEAD, "Zombies", "zombie"),
        SKELETON(EntityType.SKELETON, Material.BONE, "Skeletons", "skeleton"),
        SPIDER(EntityType.SPIDER, Material.SPIDER_EYE, "Spiders", "spider"),
        CREEPER(EntityType.CREEPER, Material.CREEPER_HEAD, "Creepers", "creeper"),
        ENDERMAN(EntityType.ENDERMAN, Material.ENDER_PEARL, "Endermen", "enderman"),
        WITCH(EntityType.WITCH, Material.POTION, "Witches", "witch"),
        BLAZE(EntityType.BLAZE, Material.BLAZE_ROD, "Blazes", "blaze"),
        SLIME(EntityType.SLIME, Material.SLIME_BALL, "Slimes", "slime"),
        PIGLIN(EntityType.PIGLIN, Material.GOLD_INGOT, "Piglins", "piglin"),
        WITHER_SKELETON(EntityType.WITHER_SKELETON, Material.WITHER_SKELETON_SKULL, "Wither Skeletons", "wither-skeleton"),
        GUARDIAN(EntityType.GUARDIAN, Material.PRISMARINE_SHARD, "Guardians", "guardian");

        private final EntityType entityType;
        private final Material icon;
        private final String displayName;
        private final String key;

        QuestTarget(EntityType entityType, Material icon, String displayName, String key) {
            this.entityType = entityType;
            this.icon = icon;
            this.displayName = displayName;
            this.key = key;
        }

        public EntityType getEntityType() {
            return entityType;
        }

        public Material getIcon() {
            return icon;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getKey() {
            return key;
        }
    }

    public static final class QuestDefinition {
        private final int level;
        private final QuestTarget target;
        private final int requiredKills;
        private final int rewardPoints;

        private QuestDefinition(int level, QuestTarget target, int requiredKills, int rewardPoints) {
            this.level = level;
            this.target = target;
            this.requiredKills = requiredKills;
            this.rewardPoints = rewardPoints;
        }

        public int getLevel() {
            return level;
        }

        public QuestTarget getTarget() {
            return target;
        }

        public int getRequiredKills() {
            return requiredKills;
        }

        public int getRewardPoints() {
            return rewardPoints;
        }
    }

    private static final int EXTRA_QUEST_COUNT = 50;
    private static final int FINAL_ZOMBIE_KILLS = 1000;
    private static final int FINAL_ZOMBIE_REWARD_BONUS = 20;
    private static final List<QuestDefinition> QUESTS;
    private static final Map<EntityType, QuestTarget> TARGETS_BY_ENTITY = new HashMap<>();
    private static final Map<String, QuestTarget> TARGETS_BY_KEY = new HashMap<>();

    static {
        List<QuestDefinition> quests = new ArrayList<>();
        quests.add(new QuestDefinition(2, QuestTarget.ZOMBIE, 50, 10));
        quests.add(new QuestDefinition(2, QuestTarget.SPIDER, 30, 10));
        quests.add(new QuestDefinition(2, QuestTarget.CREEPER, 20, 15));
        quests.add(new QuestDefinition(3, QuestTarget.SKELETON, 50, 30));
        quests.add(new QuestDefinition(3, QuestTarget.ZOMBIE, 60, 15));
        quests.add(new QuestDefinition(3, QuestTarget.ENDERMAN, 25, 20));
        quests.add(new QuestDefinition(4, QuestTarget.CREEPER, 40, 25));
        quests.add(new QuestDefinition(4, QuestTarget.SPIDER, 70, 20));
        quests.add(new QuestDefinition(4, QuestTarget.WITCH, 20, 30));
        quests.add(new QuestDefinition(5, QuestTarget.SKELETON, 80, 35));
        quests.add(new QuestDefinition(5, QuestTarget.ZOMBIE, 90, 25));
        quests.add(new QuestDefinition(5, QuestTarget.ENDERMAN, 35, 30));
        quests.add(new QuestDefinition(6, QuestTarget.BLAZE, 50, 40));
        quests.add(new QuestDefinition(6, QuestTarget.SPIDER, 100, 30));
        quests.add(new QuestDefinition(6, QuestTarget.SLIME, 30, 30));
        quests.add(new QuestDefinition(7, QuestTarget.PIGLIN, 60, 45));
        quests.add(new QuestDefinition(7, QuestTarget.WITHER_SKELETON, 15, 50));
        quests.add(new QuestDefinition(7, QuestTarget.GUARDIAN, 10, 55));
        QuestTarget[] extendedTargets = new QuestTarget[] {
                QuestTarget.SKELETON,
                QuestTarget.SPIDER,
                QuestTarget.CREEPER,
                QuestTarget.ENDERMAN,
                QuestTarget.WITCH,
                QuestTarget.ZOMBIE,
                QuestTarget.BLAZE,
                QuestTarget.SLIME,
                QuestTarget.PIGLIN,
                QuestTarget.WITHER_SKELETON,
                QuestTarget.GUARDIAN
        };
        int level = 8;
        int baseKills = 150;
        int killStep = 17;
        int baseReward = 60;
        int rewardStep = 3;
        int finalQuestIndex = EXTRA_QUEST_COUNT - 1;
        for (int i = 0; i < EXTRA_QUEST_COUNT; i++) {
            int requiredKills = baseKills + (i * killStep);
            QuestTarget target = extendedTargets[i % extendedTargets.length];
            int rewardPoints = baseReward + (i * rewardStep);
            if (i == finalQuestIndex) {
                requiredKills = FINAL_ZOMBIE_KILLS;
                rewardPoints += FINAL_ZOMBIE_REWARD_BONUS;
                target = QuestTarget.ZOMBIE;
            }
            quests.add(new QuestDefinition(level, target, requiredKills, rewardPoints));
            if ((i + 1) % 5 == 0) {
                level++;
            }
        }
        QUESTS = Collections.unmodifiableList(quests);
        for (QuestTarget target : QuestTarget.values()) {
            TARGETS_BY_ENTITY.put(target.getEntityType(), target);
            TARGETS_BY_KEY.put(target.getKey(), target);
        }
    }

    private ClanQuestProgress() {
    }

    public static List<QuestDefinition> getQuestDefinitions() {
        return QUESTS;
    }

    public static QuestTarget getQuestTarget(EntityType entityType) {
        return TARGETS_BY_ENTITY.get(entityType);
    }

    public static QuestTarget getQuestTargetByKey(String key) {
        return TARGETS_BY_KEY.get(key);
    }

    public static int getQuestSkillPoints(Map<QuestTarget, Integer> killCounts) {
        int points = 0;
        for (QuestDefinition quest : QUESTS) {
            int kills = killCounts.getOrDefault(quest.getTarget(), 0);
            if (kills >= quest.getRequiredKills()) {
                points += quest.getRewardPoints();
            }
        }
        return points;
    }

    public static int getQuestLevel(Map<QuestTarget, Integer> killCounts) {
        int level = getCompletedQuestCount(killCounts) + 1;
        // Level starts at 1 and increases with each completed quest, so max is total quests + 1.
        return Math.min(level, getTotalQuestCount() + 1);
    }

    public static int getCompletedQuestCount(Map<QuestTarget, Integer> killCounts) {
        int count = 0;
        for (QuestDefinition quest : QUESTS) {
            int kills = killCounts.getOrDefault(quest.getTarget(), 0);
            if (kills >= quest.getRequiredKills()) {
                count++;
            }
        }
        return count;
    }

    public static int getTotalQuestCount() {
        return QUESTS.size();
    }

    public static Map<QuestTarget, Integer> createEmptyKillCounts() {
        return new EnumMap<>(QuestTarget.class);
    }
}
