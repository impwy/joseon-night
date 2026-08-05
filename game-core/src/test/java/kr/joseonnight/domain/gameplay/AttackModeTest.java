package kr.joseonnight.domain.gameplay;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AttackModeTest {

    @Test
    void onlyTheThunderBellUsesDirectLightningAmongBaseItems() {
        assertThat(ItemType.THUNDER_BELL.attackMode()).isEqualTo(AttackMode.LIGHTNING);
        assertThat(ItemType.values())
                .filteredOn(item -> item != ItemType.THUNDER_BELL)
                .allMatch(item -> item.attackMode() == AttackMode.PROJECTILE);
    }

    @Test
    void thunderEvolutionsUseDirectLightning() {
        assertThat(EvolutionType.HEAVENLY_THUNDER_SEAL.attackMode())
                .isEqualTo(AttackMode.LIGHTNING);
        assertThat(EvolutionType.THUNDER_FLAME_DIVINE_ORB.attackMode())
                .isEqualTo(AttackMode.LIGHTNING);
        assertThat(EvolutionType.values())
                .filteredOn(evolution -> evolution != EvolutionType.HEAVENLY_THUNDER_SEAL
                        && evolution != EvolutionType.THUNDER_FLAME_DIVINE_ORB)
                .allMatch(evolution -> evolution.attackMode() == AttackMode.PROJECTILE);
    }
}
