package kr.vamsur.domain.gameplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Mutable aggregate for exactly one play session.
 *
 * <p>The model deliberately knows nothing about JavaFX, Spring, or HTTP. Its only
 * clock is the delta supplied to {@link #tick(double)}, so equal input, rules, and
 * seed always produce equal snapshots.</p>
 */
public final class GameSession {

    private static final long PLAYER_ID = 0L;
    private static final double PROJECTILE_RADIUS = 8.0;
    private static final double PROJECTILE_LIFETIME_SECONDS = 3.0;
    private static final double SOUL_FLAME_RADIUS = 9.0;
    private static final double SOUL_ATTRACTION_SPEED = 360.0;
    private static final double MAX_PROJECTILE_DISTANCE_FACTOR = 1.5;
    private static final double MIN_ATTACK_COOLDOWN_SECONDS = 0.12;
    private static final double TIME_EPSILON_SECONDS = 1.0e-9;
    private static final int MAX_PROJECTILES_PER_VOLLEY = 5;

    private final GameRules rules;
    private final Random random;
    private final Player player;
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Projectile> projectiles = new ArrayList<>();
    private final List<SoulFlame> soulFlames = new ArrayList<>();

    private GamePhase phase;
    private InputState input = InputState.idle();
    private List<UpgradeType> upgradeChoices = List.of();
    private double elapsedSeconds;
    private double spawnAccumulatorSeconds;
    private double attackCooldownRemainingSeconds;
    private double projectileDamage;
    private double attackCooldownSeconds;
    private int projectileCount = 1;
    private int level = 1;
    private int experience;
    private int experienceToNextLevel;
    private int killCount;
    private long nextEntityId = 1L;

    private GameSession(GameRules rules, long seed, GamePhase initialPhase) {
        this.rules = Objects.requireNonNull(rules, "rules");
        random = new Random(seed);
        phase = Objects.requireNonNull(initialPhase, "initialPhase");
        player = new Player(0.0, 0.0, rules.playerRadius(), rules.playerSpeed(), rules.soulMagnetRadius());
        projectileDamage = rules.projectileDamage();
        attackCooldownSeconds = rules.attackCooldownSeconds();
        experienceToNextLevel = rules.initialExperienceToNextLevel();
    }

    public static GameSession lobby(GameRules rules, long seed) {
        return new GameSession(rules, seed, GamePhase.LOBBY);
    }

    public static GameSession running(GameRules rules, long seed) {
        return new GameSession(rules, seed, GamePhase.RUNNING);
    }

    public void setInput(InputState input) {
        this.input = Objects.requireNonNull(input, "input");
    }

    public void tick(double deltaSeconds) {
        validateDelta(deltaSeconds);
        if (phase != GamePhase.RUNNING || deltaSeconds == 0.0) {
            return;
        }

        double remainingBeforeTick = rules.durationSeconds() - elapsedSeconds;
        double simulatedSeconds = Math.min(deltaSeconds, remainingBeforeTick);
        boolean durationReached = remainingBeforeTick - simulatedSeconds <= TIME_EPSILON_SECONDS;
        elapsedSeconds = durationReached
                ? rules.durationSeconds()
                : elapsedSeconds + simulatedSeconds;

        movePlayer(simulatedSeconds);
        spawnEnemies(simulatedSeconds);
        moveEnemies(simulatedSeconds);
        if (enemyTouchesPlayer()) {
            phase = GamePhase.DEFEAT;
            input = InputState.idle();
            return;
        }

        fireTalismanIfReady(simulatedSeconds);
        moveProjectilesAndResolveHits(simulatedSeconds);
        attractAndCollectSoulFlames(simulatedSeconds);
        beginLevelUpIfReady();

        if (durationReached) {
            phase = GamePhase.VICTORY;
            input = InputState.idle();
            upgradeChoices = List.of();
        }
    }

    public void chooseUpgrade(UpgradeType upgradeType) {
        Objects.requireNonNull(upgradeType, "upgradeType");
        if (phase != GamePhase.LEVEL_UP) {
            throw new IllegalStateException("An upgrade can only be chosen during level up");
        }
        if (!upgradeChoices.contains(upgradeType)) {
            throw new IllegalArgumentException("The upgrade was not offered: " + upgradeType);
        }

        applyUpgrade(upgradeType);
        experience -= experienceToNextLevel;
        level++;
        experienceToNextLevel = Math.max(experienceToNextLevel + 1,
                (int) Math.ceil(experienceToNextLevel * 1.45));

        if (experience >= experienceToNextLevel) {
            upgradeChoices = createUpgradeChoices();
        } else {
            upgradeChoices = List.of();
            phase = GamePhase.RUNNING;
        }
    }

    public GameState state() {
        return new GameState(
                phase,
                elapsedSeconds,
                Math.max(0.0, rules.durationSeconds() - elapsedSeconds),
                level,
                experience,
                experienceToNextLevel,
                killCount,
                player.snapshot(),
                enemies.stream().map(Enemy::snapshot).toList(),
                projectiles.stream().map(Projectile::snapshot).toList(),
                soulFlames.stream().map(SoulFlame::snapshot).toList(),
                upgradeChoices
        );
    }

    private static void validateDelta(double deltaSeconds) {
        if (!Double.isFinite(deltaSeconds) || deltaSeconds < 0.0) {
            throw new IllegalArgumentException("deltaSeconds must be finite and non-negative");
        }
    }

    private void movePlayer(double deltaSeconds) {
        double horizontal = (input.right() ? 1.0 : 0.0) - (input.left() ? 1.0 : 0.0);
        double vertical = (input.down() ? 1.0 : 0.0) - (input.up() ? 1.0 : 0.0);
        double length = Math.hypot(horizontal, vertical);
        if (length == 0.0) {
            return;
        }

        double directionX = horizontal / length;
        double directionY = vertical / length;
        player.x += directionX * player.speed * deltaSeconds;
        player.y += directionY * player.speed * deltaSeconds;
        player.rotationDegrees = Math.toDegrees(Math.atan2(directionY, directionX));
    }

    private void spawnEnemies(double deltaSeconds) {
        double interval = currentSpawnIntervalSeconds();
        spawnAccumulatorSeconds += deltaSeconds;
        while (spawnAccumulatorSeconds >= interval && enemies.size() < rules.maxEnemies()) {
            spawnAccumulatorSeconds -= interval;
            double angle = random.nextDouble() * Math.PI * 2.0;
            double x = player.x + Math.cos(angle) * rules.enemySpawnRadius();
            double y = player.y + Math.sin(angle) * rules.enemySpawnRadius();
            enemies.add(new Enemy(
                    nextId(),
                    x,
                    y,
                    rules.enemyRadius(),
                    currentEnemySpeed(),
                    currentEnemyHealth()
            ));
        }
        if (enemies.size() >= rules.maxEnemies()) {
            spawnAccumulatorSeconds = Math.min(spawnAccumulatorSeconds, interval);
        }
    }

    private double currentSpawnIntervalSeconds() {
        int difficulty = difficultyLevel();
        double configuredMinimum = Math.min(0.15, rules.enemySpawnIntervalSeconds());
        return Math.max(configuredMinimum,
                rules.enemySpawnIntervalSeconds() * Math.pow(0.82, difficulty));
    }

    private double currentEnemySpeed() {
        return rules.enemySpeed() * (1.0 + difficultyLevel() * 0.10);
    }

    private double currentEnemyHealth() {
        return rules.enemyHealth() * (1.0 + difficultyLevel() * 0.35);
    }

    private int difficultyLevel() {
        return (int) (elapsedSeconds / 60.0);
    }

    private void moveEnemies(double deltaSeconds) {
        for (Enemy enemy : enemies) {
            double directionX = player.x - enemy.x;
            double directionY = player.y - enemy.y;
            double distance = Math.hypot(directionX, directionY);
            if (distance == 0.0) {
                continue;
            }

            double distanceToMove = Math.min(enemy.speed * deltaSeconds, distance);
            enemy.x += directionX / distance * distanceToMove;
            enemy.y += directionY / distance * distanceToMove;
            enemy.rotationDegrees = Math.toDegrees(Math.atan2(directionY, directionX));
        }
    }

    private boolean enemyTouchesPlayer() {
        for (Enemy enemy : enemies) {
            double collisionDistance = player.radius + enemy.radius;
            if (squaredDistance(player.x, player.y, enemy.x, enemy.y)
                    <= collisionDistance * collisionDistance) {
                return true;
            }
        }
        return false;
    }

    private void fireTalismanIfReady(double deltaSeconds) {
        attackCooldownRemainingSeconds -= deltaSeconds;
        if (attackCooldownRemainingSeconds > 0.0 || enemies.isEmpty()
                || projectiles.size() >= rules.maxProjectiles()) {
            return;
        }

        Enemy target = nearestEnemy();
        double baseAngle = Math.atan2(target.y - player.y, target.x - player.x);
        int availableCapacity = rules.maxProjectiles() - projectiles.size();
        int shots = Math.min(projectileCount, availableCapacity);
        double center = (shots - 1) / 2.0;
        for (int index = 0; index < shots; index++) {
            double angle = baseAngle + Math.toRadians(8.0) * (index - center);
            projectiles.add(new Projectile(
                    nextId(),
                    player.x,
                    player.y,
                    rules.projectileSpeed() * Math.cos(angle),
                    rules.projectileSpeed() * Math.sin(angle),
                    PROJECTILE_RADIUS,
                    projectileDamage,
                    Math.toDegrees(angle)
            ));
        }
        attackCooldownRemainingSeconds = attackCooldownSeconds;
    }

    private Enemy nearestEnemy() {
        Enemy nearest = enemies.getFirst();
        double nearestDistance = squaredDistance(player.x, player.y, nearest.x, nearest.y);
        for (int index = 1; index < enemies.size(); index++) {
            Enemy candidate = enemies.get(index);
            double candidateDistance = squaredDistance(player.x, player.y, candidate.x, candidate.y);
            if (candidateDistance < nearestDistance) {
                nearest = candidate;
                nearestDistance = candidateDistance;
            }
        }
        return nearest;
    }

    private void moveProjectilesAndResolveHits(double deltaSeconds) {
        for (int projectileIndex = projectiles.size() - 1; projectileIndex >= 0; projectileIndex--) {
            Projectile projectile = projectiles.get(projectileIndex);
            double previousX = projectile.x;
            double previousY = projectile.y;
            projectile.x += projectile.velocityX * deltaSeconds;
            projectile.y += projectile.velocityY * deltaSeconds;
            projectile.remainingLifetimeSeconds -= deltaSeconds;

            Enemy hit = findHitEnemy(projectile, previousX, previousY);
            if (hit != null) {
                hit.health -= projectile.damage;
                projectiles.remove(projectileIndex);
                if (hit.health <= 0.0) {
                    enemies.remove(hit);
                    killCount++;
                    dropSoulFlame(hit.x, hit.y);
                }
                continue;
            }

            double maximumDistance = rules.enemySpawnRadius() * MAX_PROJECTILE_DISTANCE_FACTOR;
            if (projectile.remainingLifetimeSeconds <= 0.0
                    || squaredDistance(player.x, player.y, projectile.x, projectile.y)
                    > maximumDistance * maximumDistance) {
                projectiles.remove(projectileIndex);
            }
        }
    }

    private Enemy findHitEnemy(Projectile projectile, double previousX, double previousY) {
        Enemy nearestHit = null;
        double nearestProgress = Double.POSITIVE_INFINITY;
        for (Enemy enemy : enemies) {
            double collisionRadius = projectile.radius + enemy.radius;
            double progress = segmentProgressNearestToPoint(
                    previousX,
                    previousY,
                    projectile.x,
                    projectile.y,
                    enemy.x,
                    enemy.y
            );
            double nearestX = previousX + (projectile.x - previousX) * progress;
            double nearestY = previousY + (projectile.y - previousY) * progress;
            if (squaredDistance(nearestX, nearestY, enemy.x, enemy.y)
                    <= collisionRadius * collisionRadius && progress < nearestProgress) {
                nearestHit = enemy;
                nearestProgress = progress;
            }
        }
        return nearestHit;
    }

    private static double segmentProgressNearestToPoint(
            double startX,
            double startY,
            double endX,
            double endY,
            double pointX,
            double pointY
    ) {
        double segmentX = endX - startX;
        double segmentY = endY - startY;
        double lengthSquared = segmentX * segmentX + segmentY * segmentY;
        if (lengthSquared == 0.0) {
            return 0.0;
        }
        double projected = ((pointX - startX) * segmentX + (pointY - startY) * segmentY) / lengthSquared;
        return Math.max(0.0, Math.min(1.0, projected));
    }

    private void dropSoulFlame(double x, double y) {
        if (soulFlames.size() < rules.maxSoulFlames()) {
            soulFlames.add(new SoulFlame(nextId(), x, y, SOUL_FLAME_RADIUS));
        }
    }

    private void attractAndCollectSoulFlames(double deltaSeconds) {
        for (int index = soulFlames.size() - 1; index >= 0; index--) {
            SoulFlame soulFlame = soulFlames.get(index);
            double directionX = player.x - soulFlame.x;
            double directionY = player.y - soulFlame.y;
            double distance = Math.hypot(directionX, directionY);
            double collectDistance = player.radius + soulFlame.radius;

            if (distance > collectDistance && distance <= player.soulMagnetRadius) {
                double distanceToMove = Math.min(SOUL_ATTRACTION_SPEED * deltaSeconds,
                        distance - collectDistance);
                soulFlame.x += directionX / distance * distanceToMove;
                soulFlame.y += directionY / distance * distanceToMove;
                distance -= distanceToMove;
            }

            if (distance <= collectDistance) {
                soulFlames.remove(index);
                experience++;
            }
        }
    }

    private void beginLevelUpIfReady() {
        if (experience >= experienceToNextLevel) {
            phase = GamePhase.LEVEL_UP;
            input = InputState.idle();
            upgradeChoices = createUpgradeChoices();
        }
    }

    private List<UpgradeType> createUpgradeChoices() {
        UpgradeType[] shuffled = UpgradeType.values().clone();
        for (int index = shuffled.length - 1; index > 0; index--) {
            int swapIndex = random.nextInt(index + 1);
            UpgradeType temporary = shuffled[index];
            shuffled[index] = shuffled[swapIndex];
            shuffled[swapIndex] = temporary;
        }
        return List.of(shuffled[0], shuffled[1], shuffled[2]);
    }

    private void applyUpgrade(UpgradeType upgradeType) {
        switch (upgradeType) {
            case TALISMAN_DAMAGE -> projectileDamage += 10.0;
            case TALISMAN_COOLDOWN -> attackCooldownSeconds = Math.max(
                    MIN_ATTACK_COOLDOWN_SECONDS,
                    attackCooldownSeconds * 0.82
            );
            case TALISMAN_PROJECTILES -> projectileCount = Math.min(
                    MAX_PROJECTILES_PER_VOLLEY,
                    projectileCount + 1
            );
            case MOVEMENT_SPEED -> player.speed *= 1.10;
            case SOUL_MAGNET -> player.soulMagnetRadius += 30.0;
        }
    }

    private long nextId() {
        return nextEntityId++;
    }

    private static double squaredDistance(double firstX, double firstY, double secondX, double secondY) {
        double horizontal = firstX - secondX;
        double vertical = firstY - secondY;
        return horizontal * horizontal + vertical * vertical;
    }

    private static final class Player {
        private double x;
        private double y;
        private final double radius;
        private double speed;
        private double soulMagnetRadius;
        private double rotationDegrees;

        private Player(double x, double y, double radius, double speed, double soulMagnetRadius) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.speed = speed;
            this.soulMagnetRadius = soulMagnetRadius;
        }

        private EntityState snapshot() {
            return new EntityState(PLAYER_ID, x, y, radius, rotationDegrees);
        }
    }

    private static final class Enemy {
        private final long id;
        private double x;
        private double y;
        private final double radius;
        private final double speed;
        private double health;
        private double rotationDegrees;

        private Enemy(long id, double x, double y, double radius, double speed, double health) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.speed = speed;
            this.health = health;
        }

        private EntityState snapshot() {
            return new EntityState(id, x, y, radius, rotationDegrees);
        }
    }

    private static final class Projectile {
        private final long id;
        private double x;
        private double y;
        private final double velocityX;
        private final double velocityY;
        private final double radius;
        private final double damage;
        private final double rotationDegrees;
        private double remainingLifetimeSeconds = PROJECTILE_LIFETIME_SECONDS;

        private Projectile(
                long id,
                double x,
                double y,
                double velocityX,
                double velocityY,
                double radius,
                double damage,
                double rotationDegrees
        ) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.radius = radius;
            this.damage = damage;
            this.rotationDegrees = rotationDegrees;
        }

        private EntityState snapshot() {
            return new EntityState(id, x, y, radius, rotationDegrees);
        }
    }

    private static final class SoulFlame {
        private final long id;
        private double x;
        private double y;
        private final double radius;

        private SoulFlame(long id, double x, double y, double radius) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.radius = radius;
        }

        private EntityState snapshot() {
            return new EntityState(id, x, y, radius, 0.0);
        }
    }
}
