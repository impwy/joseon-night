package kr.joseonnight.domain.gameplay;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import org.springframework.util.Assert;

/**
 * Mutable aggregate for exactly one authoritative play session.
 *
 * <p>The model owns no wall clock. A server loop supplies fixed deltas, so an equal character,
 * seed, and command sequence always produces equal state.</p>
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
    private static final double BARRIER_INVULNERABILITY_SECONDS = 2.0;
    private static final double CHEST_RADIUS = 24.0;
    private static final double CHEST_MIN_DISTANCE = 600.0;
    private static final double CHEST_MAX_DISTANCE = 2_200.0;
    private static final int YELLOW_CHEST_COUNT = 3;
    private static final int PURPLE_CHEST_COUNT = 2;
    private static final int MAX_PROJECTILES_PER_VOLLEY = 5;
    private static final int MAX_RECENT_SOUND_EVENTS = 64;
    private static final int MAX_ACTIVE_LIGHTNING_STRIKES = 32;
    private static final double LIGHTNING_VISUAL_SECONDS = 0.24;

    private final GameRules rules;
    private final Random random;
    private final CharacterType character;
    private final Player player;
    private final ItemLoadout loadout;
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Projectile> projectiles = new ArrayList<>();
    private final List<LightningStrike> lightningStrikes = new ArrayList<>();
    private final List<SoulFlame> soulFlames = new ArrayList<>();
    private final List<WorldChest> chests = new ArrayList<>();
    private final List<SoundEvent> soundEvents = new ArrayList<>();
    private final Map<ItemType, Double> itemCooldowns = new EnumMap<>(ItemType.class);
    private final Map<EvolutionType, Double> evolutionCooldowns = new EnumMap<>(EvolutionType.class);

    private GamePhase phase;
    private InputState input = InputState.idle();
    private List<UpgradeType> upgradeChoices = List.of();
    private List<RewardOption> levelUpOptions = List.of();
    private List<RewardOption> chestRewardOptions = List.of();
    private double elapsedSeconds;
    private double spawnAccumulatorSeconds;
    private double itemBaseDamage;
    private double itemAttackCooldownSeconds;
    private int itemProjectileCount = 1;
    private int level = 1;
    private int experience;
    private int experienceToNextLevel;
    private int killCount;
    private long nextEntityId = 1L;
    private long nextSoundEventId = 1L;

    private GameSession(
            GameRules rules,
            long seed,
            GamePhase initialPhase,
            CharacterType character
    ) {
        this.rules = Objects.requireNonNull(rules, "rules");
        this.character = Objects.requireNonNull(character, "character");
        random = new Random(seed);
        phase = Objects.requireNonNull(initialPhase, "initialPhase");
        player = new Player(
                rules.playerRadius(),
                rules.playerSpeed() * character.speedMultiplier(),
                rules.soulMagnetRadius(),
                character.startsWithBarrier());
        loadout = new ItemLoadout(character);
        itemBaseDamage = rules.projectileDamage();
        itemAttackCooldownSeconds = rules.attackCooldownSeconds();
        experienceToNextLevel = rules.initialExperienceToNextLevel();
        if (initialPhase == GamePhase.RUNNING) {
            createWorldChests();
        }
    }

    public static GameSession lobby(GameRules rules, long seed) {
        return new GameSession(rules, seed, GamePhase.LOBBY, CharacterType.DOKKAEBI_HUNTER);
    }

    public static GameSession running(GameRules rules, long seed) {
        return running(rules, seed, CharacterType.DOKKAEBI_HUNTER);
    }

    public static GameSession running(GameRules rules, long seed, CharacterType character) {
        return new GameSession(rules, seed, GamePhase.RUNNING, character);
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
        player.invulnerabilityRemainingSeconds = Math.max(
                0.0,
                player.invulnerabilityRemainingSeconds - simulatedSeconds);

        movePlayer(simulatedSeconds);
        if (openTouchedChest()) {
            return;
        }
        spawnEnemies(simulatedSeconds);
        moveEnemies(simulatedSeconds);
        if (resolveEnemyContact()) {
            return;
        }

        updateLightningStrikes(simulatedSeconds);
        fireEquippedWeapons(simulatedSeconds);
        moveProjectilesAndResolveHits(simulatedSeconds);
        attractAndCollectSoulFlames(simulatedSeconds);
        beginLevelUpIfReady();

        if (durationReached && phase == GamePhase.RUNNING) {
            finish(GamePhase.VICTORY);
        }
    }

    /** Selects one of the currently offered general upgrades. */
    public void chooseUpgrade(UpgradeType upgradeType) {
        Objects.requireNonNull(upgradeType, "upgradeType");
        ensurePhase(GamePhase.LEVEL_UP, "An upgrade can only be chosen during level up");
        Assert.state(upgradeChoices.contains(upgradeType),
                () -> "The upgrade was not offered: " + upgradeType);
        applyGeneralUpgrade(upgradeType);
        completeLevelUp();
    }

    public void chooseLevelUp(String optionId) {
        ensurePhase(GamePhase.LEVEL_UP, "A level-up reward can only be chosen during level up");
        RewardOption option = findOption(levelUpOptions, optionId);
        switch (option.kind()) {
            case UPGRADE -> applyGeneralUpgrade(UpgradeType.valueOf(option.targetId()));
            case ITEM -> loadout.apply(option);
            case EVOLUTION -> throw new IllegalArgumentException(
                    "An evolution can only be selected from a purple chest");
        }
        completeLevelUp();
    }

    public void chooseChestReward(String optionId) {
        ensurePhase(GamePhase.CHEST_REWARD, "A chest reward can only be chosen while a chest is open");
        RewardOption option = findOption(chestRewardOptions, optionId);
        loadout.apply(option);
        chestRewardOptions = List.of();
        phase = GamePhase.RUNNING;
    }

    public void abandon() {
        if (phase == GamePhase.VICTORY || phase == GamePhase.DEFEAT || phase == GamePhase.ABANDONED) {
            return;
        }
        finish(GamePhase.ABANDONED);
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
                character,
                player.barrierAvailable,
                player.invulnerabilityRemainingSeconds,
                player.snapshot(character.id()),
                enemies.stream().map(Enemy::snapshot).toList(),
                projectiles.stream().map(Projectile::snapshot).toList(),
                lightningStrikes.stream().map(LightningStrike::snapshot).toList(),
                soulFlames.stream().map(SoulFlame::snapshot).toList(),
                upgradeChoices,
                loadout.itemStates(),
                loadout.evolutionStates(),
                loadout.occupiedSlots(),
                chests.stream().map(WorldChest::snapshot).toList(),
                chests.stream().map(this::indicatorFor).toList(),
                levelUpOptions,
                chestRewardOptions,
                soundEvents);
    }

    private static void validateDelta(double deltaSeconds) {
        Assert.isTrue(Double.isFinite(deltaSeconds) && deltaSeconds >= 0.0,
                "deltaSeconds must be finite and non-negative");
    }

    private void movePlayer(double deltaSeconds) {
        double horizontal = (input.right() ? 1.0 : 0.0) - (input.left() ? 1.0 : 0.0);
        double vertical = (input.down() ? 1.0 : 0.0) - (input.up() ? 1.0 : 0.0);
        double length = Math.hypot(horizontal, vertical);
        if (length == 0.0) {
            return;
        }
        player.x += horizontal / length * player.speed * deltaSeconds;
        player.y += vertical / length * player.speed * deltaSeconds;
    }

    private void createWorldChests() {
        for (int index = 0; index < YELLOW_CHEST_COUNT; index++) {
            createWorldChest(ChestType.YELLOW);
        }
        for (int index = 0; index < PURPLE_CHEST_COUNT; index++) {
            createWorldChest(ChestType.PURPLE);
        }
    }

    private void createWorldChest(ChestType type) {
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = CHEST_MIN_DISTANCE
                + random.nextDouble() * (CHEST_MAX_DISTANCE - CHEST_MIN_DISTANCE);
        chests.add(new WorldChest(
                nextId(),
                type,
                Math.cos(angle) * distance,
                Math.sin(angle) * distance));
    }

    private boolean openTouchedChest() {
        for (int index = 0; index < chests.size(); index++) {
            WorldChest chest = chests.get(index);
            double collisionDistance = player.radius + CHEST_RADIUS;
            if (squaredDistance(player.x, player.y, chest.x, chest.y)
                    <= collisionDistance * collisionDistance) {
                chests.remove(index);
                emitSound(SoundCue.CHEST_OPENED);
                chestRewardOptions = chest.type == ChestType.PURPLE
                        ? loadout.purpleChestOptions(random)
                        : loadout.yellowChestOptions(random);
                input = InputState.idle();
                if (chestRewardOptions.isEmpty()) {
                    phase = GamePhase.RUNNING;
                } else {
                    phase = GamePhase.CHEST_REWARD;
                }
                return phase == GamePhase.CHEST_REWARD;
            }
        }
        return false;
    }

    private DirectionIndicatorState indicatorFor(WorldChest chest) {
        double directionX = chest.x - player.x;
        double directionY = chest.y - player.y;
        double distance = Math.hypot(directionX, directionY);
        if (distance > 0.0) {
            directionX /= distance;
            directionY /= distance;
        }
        return new DirectionIndicatorState(chest.id, chest.type, directionX, directionY, distance);
    }

    private void spawnEnemies(double deltaSeconds) {
        double interval = currentSpawnIntervalSeconds();
        spawnAccumulatorSeconds += deltaSeconds;
        while (spawnAccumulatorSeconds >= interval && enemies.size() < rules.maxEnemies()) {
            spawnAccumulatorSeconds -= interval;
            double angle = random.nextDouble() * Math.PI * 2.0;
            enemies.add(new Enemy(
                    nextId(),
                    player.x + Math.cos(angle) * rules.enemySpawnRadius(),
                    player.y + Math.sin(angle) * rules.enemySpawnRadius(),
                    rules.enemyRadius(),
                    currentEnemySpeed(),
                    currentEnemyHealth()));
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

    /**
     * Returns true only when contact ended the run. There is no health system.
     */
    private boolean resolveEnemyContact() {
        if (!enemyTouchesPlayer() || player.invulnerabilityRemainingSeconds > 0.0) {
            return false;
        }
        if (player.barrierAvailable) {
            player.barrierAvailable = false;
            player.invulnerabilityRemainingSeconds = BARRIER_INVULNERABILITY_SECONDS;
            emitSound(SoundCue.GUARD);
            return false;
        }
        finish(GamePhase.DEFEAT);
        return true;
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

    private void fireEquippedWeapons(double deltaSeconds) {
        if (enemies.isEmpty()) {
            reduceCooldowns(deltaSeconds);
            return;
        }
        for (ItemType item : loadout.equippedItems()) {
            if (enemies.isEmpty()) {
                break;
            }
            double remaining = itemCooldowns.getOrDefault(item, 0.0) - deltaSeconds;
            if (remaining <= 0.0 && fireItem(item, loadout.itemLevel(item))) {
                remaining = itemCooldown(item);
            }
            itemCooldowns.put(item, remaining);
        }
        for (EvolutionType evolution : loadout.equippedEvolutions()) {
            if (enemies.isEmpty()) {
                break;
            }
            double remaining = evolutionCooldowns.getOrDefault(evolution, 0.0) - deltaSeconds;
            if (remaining <= 0.0 && fireEvolution(evolution)) {
                remaining = Math.max(MIN_ATTACK_COOLDOWN_SECONDS, itemAttackCooldownSeconds * 0.65);
            }
            evolutionCooldowns.put(evolution, remaining);
        }
    }

    private void reduceCooldowns(double deltaSeconds) {
        itemCooldowns.replaceAll((ignored, remaining) -> remaining - deltaSeconds);
        evolutionCooldowns.replaceAll((ignored, remaining) -> remaining - deltaSeconds);
    }

    private boolean fireItem(ItemType item, int itemLevel) {
        int shots = Math.min(MAX_PROJECTILES_PER_VOLLEY,
                itemProjectileCount + (itemLevel - 1) / 2 + itemShotBonus(item));
        double damage = itemBaseDamage * itemDamageMultiplier(item) * (1.0 + 0.20 * (itemLevel - 1));
        if (item.attackMode() == AttackMode.LIGHTNING) {
            return strikeWithLightning(item.id(), SoundCue.forItem(item), shots, damage);
        }
        return fireVolley(item.id(), SoundCue.forItem(item), shots, damage, itemSpreadDegrees(item));
    }

    private boolean fireEvolution(EvolutionType evolution) {
        int shots = Math.min(MAX_PROJECTILES_PER_VOLLEY, itemProjectileCount + 3);
        double damage = itemBaseDamage * 3.0;
        if (evolution.attackMode() == AttackMode.LIGHTNING) {
            return strikeWithLightning(evolution.id(), SoundCue.forEvolution(evolution), shots, damage);
        }
        return fireVolley(evolution.id(), SoundCue.forEvolution(evolution), shots, damage, 18.0);
    }

    private boolean strikeWithLightning(
            String kindId,
            SoundCue soundCue,
            int requestedStrikes,
            double damage
    ) {
        List<Enemy> targets = enemies.stream()
                .sorted(java.util.Comparator
                        .comparingDouble((Enemy enemy) -> squaredDistance(
                                player.x, player.y, enemy.x, enemy.y))
                        .thenComparingLong(enemy -> enemy.id))
                .limit(requestedStrikes)
                .toList();
        if (targets.isEmpty()) {
            return false;
        }
        emitSound(soundCue);
        for (Enemy target : targets) {
            if (!enemies.contains(target)) {
                continue;
            }
            if (lightningStrikes.size() == MAX_ACTIVE_LIGHTNING_STRIKES) {
                lightningStrikes.removeFirst();
            }
            lightningStrikes.add(new LightningStrike(
                    nextId(), target.x, target.y, LIGHTNING_VISUAL_SECONDS, kindId));
            damageEnemy(target, damage);
        }
        return true;
    }

    private void updateLightningStrikes(double deltaSeconds) {
        for (int index = lightningStrikes.size() - 1; index >= 0; index--) {
            LightningStrike strike = lightningStrikes.get(index);
            strike.remainingSeconds -= deltaSeconds;
            if (strike.remainingSeconds <= 0.0) {
                lightningStrikes.remove(index);
            }
        }
    }

    private boolean fireVolley(
            String kindId,
            SoundCue soundCue,
            int requestedShots,
            double damage,
            double spreadDegrees
    ) {
        int availableCapacity = rules.maxProjectiles() - projectiles.size();
        int shots = Math.min(requestedShots, availableCapacity);
        if (shots <= 0 || enemies.isEmpty()) {
            return false;
        }
        Enemy target = nearestEnemy();
        double baseAngle = Math.atan2(target.y - player.y, target.x - player.x);
        emitSound(soundCue);
        double center = (shots - 1) / 2.0;
        for (int index = 0; index < shots; index++) {
            double angle = baseAngle + Math.toRadians(spreadDegrees) * (index - center);
            projectiles.add(new Projectile(
                    nextId(),
                    player.x,
                    player.y,
                    rules.projectileSpeed() * Math.cos(angle),
                    rules.projectileSpeed() * Math.sin(angle),
                    PROJECTILE_RADIUS,
                    damage,
                    Math.toDegrees(angle),
                    kindId));
        }
        return true;
    }

    private double itemCooldown(ItemType item) {
        double multiplier = switch (item) {
            case SEAL_TALISMAN -> 1.0;
            case FLAME_FAN -> 1.15;
            case EXORCIST_SWORD -> 1.30;
            case RETURNING_BOOMERANG -> 1.40;
            case THUNDER_BELL -> 1.65;
            case SPIRIT_GOURD -> 1.75;
        };
        return Math.max(MIN_ATTACK_COOLDOWN_SECONDS, itemAttackCooldownSeconds * multiplier);
    }

    private static double itemDamageMultiplier(ItemType item) {
        return switch (item) {
            case SEAL_TALISMAN -> 1.0;
            case FLAME_FAN -> 0.75;
            case EXORCIST_SWORD -> 1.35;
            case RETURNING_BOOMERANG -> 1.10;
            case THUNDER_BELL -> 1.55;
            case SPIRIT_GOURD -> 1.25;
        };
    }

    private static int itemShotBonus(ItemType item) {
        return switch (item) {
            case FLAME_FAN -> 1;
            case EXORCIST_SWORD, THUNDER_BELL, SPIRIT_GOURD, RETURNING_BOOMERANG, SEAL_TALISMAN -> 0;
        };
    }

    private static double itemSpreadDegrees(ItemType item) {
        return switch (item) {
            case FLAME_FAN -> 14.0;
            case EXORCIST_SWORD -> 22.0;
            case RETURNING_BOOMERANG -> 10.0;
            case THUNDER_BELL -> 28.0;
            case SPIRIT_GOURD -> 32.0;
            case SEAL_TALISMAN -> 8.0;
        };
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
                projectiles.remove(projectileIndex);
                damageEnemy(hit, projectile.damage);
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
                    enemy.y);
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
        double projected = ((pointX - startX) * segmentX + (pointY - startY) * segmentY)
                / lengthSquared;
        return Math.max(0.0, Math.min(1.0, projected));
    }

    private void dropSoulFlame(double x, double y) {
        if (soulFlames.size() < rules.maxSoulFlames()) {
            soulFlames.add(new SoulFlame(nextId(), x, y));
        }
    }

    private void damageEnemy(Enemy enemy, double damage) {
        enemy.health -= damage;
        if (enemy.health <= 0.0 && enemies.remove(enemy)) {
            killCount++;
            dropSoulFlame(enemy.x, enemy.y);
        }
    }

    /** Package-scoped root operation used by deterministic domain scenarios. */
    void equipItem(ItemType item) {
        loadout.apply(RewardOption.item(Objects.requireNonNull(item, "item"), false, 0));
    }

    /** Package-scoped root operation used by deterministic domain scenarios. */
    void evolve(EvolutionType evolution) {
        loadout.apply(RewardOption.evolution(Objects.requireNonNull(evolution, "evolution")));
    }

    /** Package-scoped root operation used by deterministic domain scenarios. */
    void spawnEnemy(double x, double y, double health) {
        Assert.isTrue(Double.isFinite(x) && Double.isFinite(y),
                "enemy position must be finite");
        Assert.isTrue(Double.isFinite(health) && health > 0.0,
                "enemy health must be positive");
        Assert.state(enemies.size() < rules.maxEnemies(), "Maximum enemy count reached");
        enemies.add(new Enemy(
                nextId(),
                x,
                y,
                rules.enemyRadius(),
                currentEnemySpeed(),
                health));
    }

    private void attractAndCollectSoulFlames(double deltaSeconds) {
        for (int index = soulFlames.size() - 1; index >= 0; index--) {
            SoulFlame soulFlame = soulFlames.get(index);
            double directionX = player.x - soulFlame.x;
            double directionY = player.y - soulFlame.y;
            double distance = Math.hypot(directionX, directionY);
            double collectDistance = player.radius + SOUL_FLAME_RADIUS;

            if (distance > collectDistance && distance <= player.soulMagnetRadius) {
                double distanceToMove = Math.min(
                        SOUL_ATTRACTION_SPEED * deltaSeconds,
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
        if (experience < experienceToNextLevel) {
            return;
        }
        input = InputState.idle();
        phase = GamePhase.LEVEL_UP;
        emitSound(SoundCue.LEVEL_UP);
        levelUpOptions = loadout.levelUpOptions(random);
        upgradeChoices = levelUpOptions.stream()
                .filter(option -> option.kind() == RewardKind.UPGRADE)
                .map(option -> UpgradeType.valueOf(option.targetId()))
                .toList();
    }

    private void completeLevelUp() {
        experience -= experienceToNextLevel;
        level++;
        experienceToNextLevel = Math.max(
                experienceToNextLevel + 1,
                (int) Math.ceil(experienceToNextLevel * 1.45));
        levelUpOptions = List.of();
        upgradeChoices = List.of();
        phase = GamePhase.RUNNING;
        if (experience >= experienceToNextLevel) {
            beginLevelUpIfReady();
        }
    }

    private void applyGeneralUpgrade(UpgradeType upgradeType) {
        switch (upgradeType) {
            case ITEM_DAMAGE -> itemBaseDamage += 10.0;
            case ITEM_COOLDOWN -> itemAttackCooldownSeconds = Math.max(
                    MIN_ATTACK_COOLDOWN_SECONDS,
                    itemAttackCooldownSeconds * 0.82);
            case ITEM_PROJECTILES -> itemProjectileCount = Math.min(
                    MAX_PROJECTILES_PER_VOLLEY,
                    itemProjectileCount + 1);
            case MOVEMENT_SPEED -> player.speed *= 1.10;
            case SOUL_MAGNET -> player.soulMagnetRadius += 30.0;
        }
    }

    private static RewardOption findOption(List<RewardOption> options, String optionId) {
        Objects.requireNonNull(optionId, "optionId");
        return options.stream()
                .filter(option -> option.optionId().equals(optionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The reward was not offered: " + optionId));
    }

    private void finish(GamePhase result) {
        phase = result;
        if (result == GamePhase.DEFEAT) {
            emitSound(SoundCue.DEFEAT);
        } else if (result == GamePhase.VICTORY) {
            emitSound(SoundCue.VICTORY);
        }
        input = InputState.idle();
        upgradeChoices = List.of();
        levelUpOptions = List.of();
        chestRewardOptions = List.of();
    }

    private void ensurePhase(GamePhase expected, String message) {
        Assert.state(phase == expected, message);
    }

    private long nextId() {
        return nextEntityId++;
    }

    private void emitSound(SoundCue type) {
        if (soundEvents.size() == MAX_RECENT_SOUND_EVENTS) {
            soundEvents.removeFirst();
        }
        soundEvents.add(new SoundEvent(nextSoundEventId++, type));
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
        private boolean barrierAvailable;
        private double invulnerabilityRemainingSeconds;

        private Player(double radius, double speed, double soulMagnetRadius, boolean barrierAvailable) {
            this.radius = radius;
            this.speed = speed;
            this.soulMagnetRadius = soulMagnetRadius;
            this.barrierAvailable = barrierAvailable;
        }

        private EntityState snapshot(String characterId) {
            return new EntityState(PLAYER_ID, x, y, radius, 0.0, characterId);
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
            return new EntityState(id, x, y, radius, rotationDegrees, "shadow-dokkaebi");
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
        private final String kindId;
        private double remainingLifetimeSeconds = PROJECTILE_LIFETIME_SECONDS;

        private Projectile(
                long id,
                double x,
                double y,
                double velocityX,
                double velocityY,
                double radius,
                double damage,
                double rotationDegrees,
                String kindId
        ) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.radius = radius;
            this.damage = damage;
            this.rotationDegrees = rotationDegrees;
            this.kindId = kindId;
        }

        private EntityState snapshot() {
            return new EntityState(id, x, y, radius, rotationDegrees, kindId);
        }
    }

    private static final class SoulFlame {
        private final long id;
        private double x;
        private double y;

        private SoulFlame(long id, double x, double y) {
            this.id = id;
            this.x = x;
            this.y = y;
        }

        private EntityState snapshot() {
            return new EntityState(id, x, y, SOUL_FLAME_RADIUS, 0.0, "soul-flame");
        }
    }

    private static final class LightningStrike {
        private final long id;
        private final double x;
        private final double y;
        private final String kindId;
        private double remainingSeconds;

        private LightningStrike(long id, double x, double y, double remainingSeconds, String kindId) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.remainingSeconds = remainingSeconds;
            this.kindId = kindId;
        }

        private LightningStrikeState snapshot() {
            return new LightningStrikeState(id, x, y, remainingSeconds, kindId);
        }
    }

    private static final class WorldChest {
        private final long id;
        private final ChestType type;
        private final double x;
        private final double y;

        private WorldChest(long id, ChestType type, double x, double y) {
            this.id = id;
            this.type = type;
            this.x = x;
            this.y = y;
        }

        private ChestState snapshot() {
            return new ChestState(id, type, x, y, CHEST_RADIUS);
        }
    }
}
