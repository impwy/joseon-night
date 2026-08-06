package kr.joseonnight.domain.gameplay;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GameSessionChestEffectTest {

    private static final double TOLERANCE = 1.0e-9;

    @Test
    void barrierIsConsumedBeforeHeartAndEachProtectionGrantsTwoSeconds() {
        GameSession session = openChestAndChoose(
                ChestRewardType.HEART,
                0,
                CharacterType.DOKKAEBI_HUNTER);
        assertThat(session.state().barrierAvailable()).isTrue();
        assertThat(session.state().heartAvailable()).isTrue();

        EntityState player = session.state().player();
        session.spawnEnemy(player.x(), player.y(), 10_000.0);
        session.tick(0.01);

        assertThat(session.state().phase()).isEqualTo(GamePhase.RUNNING);
        assertThat(session.state().barrierAvailable()).isFalse();
        assertThat(session.state().heartAvailable()).isTrue();
        assertThat(session.state().invulnerabilityRemainingSeconds())
                .isCloseTo(2.0, within(TOLERANCE));

        session.tick(2.01);
        assertThat(session.state().phase()).isEqualTo(GamePhase.RUNNING);
        assertThat(session.state().heartAvailable()).isFalse();
        assertThat(session.state().invulnerabilityRemainingSeconds())
                .isCloseTo(2.0, within(TOLERANCE));
        assertThat(session.state().soundEvents())
                .extracting(SoundEvent::type)
                .filteredOn(cue -> cue == SoundCue.GUARD)
                .hasSize(2);

        session.tick(2.01);
        assertThat(session.state().phase()).isEqualTo(GamePhase.DEFEAT);
    }

    @Test
    void heldHeartIsNotOfferedByTheNextChest() {
        GameSession session = openChestAndChoose(
                ChestRewardType.HEART,
                0,
                CharacterType.GALE_SHAMAN);
        ChestState nextYellow = session.state().chests().stream()
                .filter(chest -> chest.type() == ChestType.YELLOW)
                .findFirst()
                .orElseThrow();

        session.movePlayerTo(nextYellow.x(), nextYellow.y());
        session.tick(0.01);

        assertThat(session.state().phase()).isEqualTo(GamePhase.CHEST_REWARD);
        assertThat(session.state().chestRewardOptions())
                .noneMatch(option -> option.targetId().equals(ChestRewardType.HEART.id()))
                .noneMatch(option -> option.targetId().equals(ChestRewardType.MAGNET.id()));
    }

    @Test
    void magnetCollectsEverySoulAndStartsOneImmediateLevelUp() {
        GameSession session = openChestAndChoose(
                ChestRewardType.MAGNET,
                5,
                CharacterType.GALE_SHAMAN);

        assertThat(session.state().soulFlames()).isEmpty();
        assertThat(session.state().experience()).isEqualTo(5);
        assertThat(session.state().phase()).isEqualTo(GamePhase.LEVEL_UP);

        chooseFirstLevelUpOption(session);

        assertThat(session.state().phase()).isEqualTo(GamePhase.RUNNING);
        assertThat(session.state().level()).isEqualTo(2);
        assertThat(session.state().experience()).isZero();
        assertThat(session.state().experienceToNextLevel()).isEqualTo(7);
    }

    @Test
    void magnetPreservesExcessExperienceAcrossSequentialLevelUps() {
        GameSession session = openChestAndChoose(
                ChestRewardType.MAGNET,
                30,
                CharacterType.GALE_SHAMAN);
        List<Integer> consumedThresholds = new ArrayList<>();

        while (session.state().phase() == GamePhase.LEVEL_UP) {
            consumedThresholds.add(session.state().experienceToNextLevel());
            chooseFirstLevelUpOption(session);
        }

        assertThat(consumedThresholds).containsExactly(5, 7, 10);
        assertThat(session.state().level()).isEqualTo(4);
        assertThat(session.state().experience()).isEqualTo(8);
        assertThat(session.state().experienceToNextLevel()).isEqualTo(14);
        assertThat(session.state().soulFlames()).isEmpty();
    }

    @Test
    void magnetCollectsTheAccumulatedValueAfterTheSoulFlameCap() {
        GameSession session = openChestAndChoose(
                ChestRewardType.MAGNET,
                301,
                CharacterType.GALE_SHAMAN);

        assertThat(session.state().soulFlames()).isEmpty();
        assertThat(session.state().experience()).isEqualTo(301);
        assertThat(session.state().phase()).isEqualTo(GamePhase.LEVEL_UP);
    }

    private static GameSession openChestAndChoose(
            ChestRewardType effect,
            int soulCount,
            CharacterType character
    ) {
        for (long seed = 0; seed < 200; seed++) {
            GameSession session = GameSession.running(quietRules(), seed, character);
            for (int soul = 0; soul < soulCount; soul++) {
                session.dropSoulFlame(10_000.0 + soul, 10_000.0);
            }
            ChestState yellow = session.state().chests().stream()
                    .filter(chest -> chest.type() == ChestType.YELLOW)
                    .findFirst()
                    .orElseThrow();
            session.movePlayerTo(yellow.x(), yellow.y());
            session.tick(0.01);
            RewardOption option = session.state().chestRewardOptions().stream()
                    .filter(candidate -> candidate.kind() == RewardKind.CHEST_EFFECT)
                    .filter(candidate -> candidate.targetId().equals(effect.id()))
                    .findFirst()
                    .orElse(null);
            if (option != null) {
                session.chooseChestReward(option.optionId());
                return session;
            }
        }
        throw new AssertionError("No deterministic seed offered chest effect: " + effect);
    }

    private static void chooseFirstLevelUpOption(GameSession session) {
        session.chooseLevelUp(session.state().levelUpOptions().getFirst().optionId());
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }

    private static GameRules quietRules() {
        return new GameRules(
                240.0,
                18.0,
                760.0,
                1_000.0,
                0.0,
                20.0,
                17.0,
                520.0,
                20.0,
                0.65,
                100.0,
                5,
                10,
                160,
                300);
    }
}
