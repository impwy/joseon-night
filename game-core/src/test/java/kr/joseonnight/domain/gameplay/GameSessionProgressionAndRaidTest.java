package kr.joseonnight.domain.gameplay;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GameSessionProgressionAndRaidTest {

    private static final String SHADOW_DOKKAEBI = "shadow-dokkaebi";
    private static final String DOKKAEBI_WARLORD = "dokkaebi-warlord";

    @Test
    void relocatesTheOldestSoulFlameAtTheCapAndPreservesEveryKillExperience() {
        GameSession session = GameSession.running(rules(1_000.0, 1_000, 1, 300), 11L);
        for (int drop = 0; drop < 300; drop++) {
            session.dropSoulFlame(0.0, 0.0);
        }
        long oldestId = session.state().soulFlames().getFirst().id();

        session.dropSoulFlame(80.0, 40.0);

        assertThat(session.state().soulFlames()).hasSize(300);
        assertThat(session.state().soulFlames().getLast())
                .satisfies(flame -> {
                    assertThat(flame.id()).isEqualTo(oldestId);
                    assertThat(flame.x()).isEqualTo(80.0);
                    assertThat(flame.y()).isEqualTo(40.0);
                });

        session.tick(1.0);

        assertThat(session.state().soulFlames()).isEmpty();
        assertThat(session.state().experience()).isEqualTo(301);
        assertThat(session.state().phase()).isEqualTo(GamePhase.RUNNING);
    }

    @Test
    void experienceRequirementsGrowByOnePointThirtyFive() {
        GameSession session = GameSession.running(rules(1_000.0, 5, 1, 100), 12L);
        int[] expectedRequirements = {5, 7, 10, 14, 19, 26, 36, 49, 67, 91};

        for (int index = 0; index < expectedRequirements.length; index++) {
            int requirement = expectedRequirements[index];
            assertThat(session.state().experienceToNextLevel()).isEqualTo(requirement);
            if (index == expectedRequirements.length - 1) {
                break;
            }
            for (int experience = 0; experience < requirement; experience++) {
                session.dropSoulFlame(0.0, 0.0);
            }
            session.tick(0.001);
            assertThat(session.state().phase()).isEqualTo(GamePhase.LEVEL_UP);
            session.chooseLevelUp(session.state().levelUpOptions().getFirst().optionId());
            assertThat(session.state().experience()).isZero();
        }
    }

    @Test
    void switchesNewSpawnsToWarlordsAfterFourHundredStageOneKills() {
        GameSession session = GameSession.running(raidRules(), 13L);
        session.equipItem(ItemType.THUNDER_BELL);
        session.spawnEnemy(200.0, 0.0, 1_000_000.0);

        for (int kill = 0; kill < 400; kill++) {
            session.spawnEnemy(50.0, 0.0, 100.0);
            session.tick(0.12);
        }

        assertThat(session.state().killCount()).isEqualTo(400);
        assertThat(session.state().enemies())
                .extracting(EntityState::kindId)
                .containsExactly(SHADOW_DOKKAEBI);

        session.tick(0.06);

        assertThat(session.state().enemies())
                .extracting(EntityState::kindId)
                .containsExactlyInAnyOrder(SHADOW_DOKKAEBI, DOKKAEBI_WARLORD);

        session.tick(0.12);
        assertThat(session.state().enemies())
                .extracting(EntityState::kindId)
                .contains(DOKKAEBI_WARLORD);

        session.tick(0.12);
        assertThat(session.state().enemies())
                .extracting(EntityState::kindId)
                .containsExactly(SHADOW_DOKKAEBI);
        assertThat(session.state().killCount()).isEqualTo(401);
    }

    private static GameRules rules(
            double spawnIntervalSeconds,
            int initialExperienceToNextLevel,
            int maxEnemies,
            int maxSoulFlames
    ) {
        return new GameRules(
                240.0,
                18.0,
                100.0,
                spawnIntervalSeconds,
                0.0,
                20.0,
                17.0,
                0.01,
                20.0,
                100.0,
                100.0,
                initialExperienceToNextLevel,
                maxEnemies,
                500,
                maxSoulFlames);
    }

    private static GameRules raidRules() {
        return new GameRules(
                240.0,
                18.0,
                100.0,
                48.05,
                0.0,
                100.0,
                17.0,
                0.01,
                100.0 / 1.55,
                0.01,
                1.0,
                10_000,
                2,
                500,
                300);
    }
}
