package kr.joseonnight.desktop.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import kr.joseonnight.application.gameplay.GameService;
import kr.joseonnight.application.gameplay.provided.GameSessionHandle;
import kr.joseonnight.desktop.gameplay.GamePhase;
import kr.joseonnight.desktop.gameplay.SoundEventSnapshot;
import kr.joseonnight.domain.gameplay.CharacterType;
import kr.joseonnight.domain.gameplay.SoundEvent;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Verifies that the framework-free desktop snapshot accepts the actual game-core JSON shape. */
class DesktopCoreContractTest {

    @Test
    void generalUpgradeIdentifiersMatchTheGameCoreSocketContract() {
        assertThat(Arrays.stream(kr.joseonnight.desktop.gameplay.UpgradeType.values())
                .map(Enum::name)
                .toList())
                .containsExactlyElementsOf(Arrays.stream(kr.joseonnight.domain.gameplay.UpgradeType.values())
                        .map(Enum::name)
                        .toList());
        assertThat(kr.joseonnight.desktop.gameplay.UpgradeType.ITEM_DAMAGE.description())
                .contains("모든 아이템");
    }

    @Test
    void soundCueIdentifiersAndJsonShapeMatchTheGameCoreSocketContract() {
        assertThat(Arrays.stream(kr.joseonnight.desktop.gameplay.SoundCue.values())
                .map(Enum::name)
                .toList())
                .containsExactlyElementsOf(Arrays.stream(kr.joseonnight.domain.gameplay.SoundCue.values())
                        .map(Enum::name)
                        .toList());

        ObjectMapper objectMapper = new ObjectMapper();
        String coreJson = objectMapper.writeValueAsString(
                new SoundEvent(7L, kr.joseonnight.domain.gameplay.SoundCue.FLAME_FAN_ATTACK));
        SoundEventSnapshot desktopEvent = objectMapper.readValue(coreJson, SoundEventSnapshot.class);

        assertThat(desktopEvent.id()).isEqualTo(7L);
        assertThat(desktopEvent.type())
                .isEqualTo(kr.joseonnight.desktop.gameplay.SoundCue.FLAME_FAN_ATTACK);
    }

    @Test
    void lightningStrikeJsonShapeMatchesTheGameCoreSocketContract() {
        ObjectMapper objectMapper = new ObjectMapper();
        String coreJson = objectMapper.writeValueAsString(
                new kr.joseonnight.application.gameplay.provided.LightningStrikeSnapshot(
                        9L, 120.5, -42.25, 0.24, "thunder-bell"));

        kr.joseonnight.desktop.gameplay.LightningStrikeSnapshot desktopStrike =
                objectMapper.readValue(
                        coreJson,
                        kr.joseonnight.desktop.gameplay.LightningStrikeSnapshot.class);

        assertThat(desktopStrike.id()).isEqualTo(9L);
        assertThat(desktopStrike.x()).isEqualTo(120.5);
        assertThat(desktopStrike.y()).isEqualTo(-42.25);
        assertThat(desktopStrike.kindId()).isEqualTo("thunder-bell");
    }

    @Test
    void actualGameCoreSnapshotDeserializesIntoTheDesktopReadModel() {
        GameService gameService = GameService.defaultGame();
        GameSessionHandle handle = gameService.startNewGame("contract-member", CharacterType.GALE_SHAMAN);
        ObjectMapper objectMapper = new ObjectMapper();

        String coreJson = objectMapper.writeValueAsString(handle.snapshot());
        kr.joseonnight.desktop.gameplay.GameSnapshot desktopSnapshot = objectMapper.readValue(
                coreJson,
                kr.joseonnight.desktop.gameplay.GameSnapshot.class);

        assertThat(coreJson).contains("\"paused\":false").doesNotContain("remainingSeconds");
        assertThat(desktopSnapshot.phase()).isEqualTo(GamePhase.RUNNING);
        assertThat(desktopSnapshot.paused()).isFalse();
        assertThat(desktopSnapshot.characterId()).isEqualTo("GALE_SHAMAN");
        assertThat(desktopSnapshot.player().kindId()).isEqualTo("gale-shaman");
        assertThat(desktopSnapshot.itemSlots()).isNotEmpty();
        assertThat(desktopSnapshot.itemSlots().getFirst().displayName()).isEqualTo("화염 부채");
        assertThat(desktopSnapshot.occupiedItemSlots()).isEqualTo(1);
        assertThat(desktopSnapshot.evolutions()).isEmpty();
        assertThat(desktopSnapshot.barrierAvailable()).isFalse();
        assertThat(desktopSnapshot.invulnerabilityRemainingSeconds()).isZero();
        assertThat(desktopSnapshot.chests()).hasSize(5);
        assertThat(desktopSnapshot.chestIndicators()).hasSize(5);
        assertThat(desktopSnapshot.lightningStrikes()).isEmpty();
        assertThat(desktopSnapshot.soundEvents()).isEmpty();
    }
}
