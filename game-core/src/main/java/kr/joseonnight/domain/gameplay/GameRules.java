package kr.joseonnight.domain.gameplay;

import org.springframework.util.Assert;

/**
 * Numeric invariants for one game session.
 */
public record GameRules(
        double playerSpeed,
        double playerRadius,
        double enemySpawnRadius,
        double enemySpawnIntervalSeconds,
        double enemySpeed,
        double enemyHealth,
        double enemyRadius,
        double projectileSpeed,
        double projectileDamage,
        double attackCooldownSeconds,
        double soulMagnetRadius,
        int initialExperienceToNextLevel,
        int maxEnemies,
        int maxProjectiles,
        int maxSoulFlames
) {

    private static final double INCREASED_ENEMY_DENSITY = 1.3;

    public GameRules {
        requirePositive(playerSpeed, "playerSpeed");
        requirePositive(playerRadius, "playerRadius");
        requirePositive(enemySpawnRadius, "enemySpawnRadius");
        requirePositive(enemySpawnIntervalSeconds, "enemySpawnIntervalSeconds");
        requireNotNegative(enemySpeed, "enemySpeed");
        requirePositive(enemyHealth, "enemyHealth");
        requirePositive(enemyRadius, "enemyRadius");
        requirePositive(projectileSpeed, "projectileSpeed");
        requirePositive(projectileDamage, "projectileDamage");
        requirePositive(attackCooldownSeconds, "attackCooldownSeconds");
        requirePositive(soulMagnetRadius, "soulMagnetRadius");
        requirePositive(initialExperienceToNextLevel, "initialExperienceToNextLevel");
        requirePositive(maxEnemies, "maxEnemies");
        requirePositive(maxProjectiles, "maxProjectiles");
        requirePositive(maxSoulFlames, "maxSoulFlames");
    }

    public static GameRules standard() {
        return new GameRules(
                240.0,
                18.0,
                760.0,
                0.5 / INCREASED_ENEMY_DENSITY,
                55.0,
                20.0,
                17.0,
                520.0,
                20.0,
                0.65,
                100.0,
                5,
                286,
                160,
                300
        );
    }

    private static void requirePositive(double value, String name) {
        Assert.isTrue(
                Double.isFinite(value) && value > 0.0,
                name + " must be a finite positive number"
        );
    }

    private static void requireNotNegative(double value, String name) {
        Assert.isTrue(
                Double.isFinite(value) && value >= 0.0,
                name + " must be a finite non-negative number"
        );
    }

    private static void requirePositive(int value, String name) {
        Assert.isTrue(value > 0, name + " must be positive");
    }
}
