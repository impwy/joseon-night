package kr.joseonnight.domain.gameplay;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SoundCueTest {
    @Test
    void everyBaseAndEvolutionItemHasItsOwnAttackCue() {
        Set<SoundCue> baseCues = Arrays.stream(ItemType.values())
                .map(SoundCue::forItem)
                .collect(Collectors.toUnmodifiableSet());
        Set<SoundCue> evolutionCues = Arrays.stream(EvolutionType.values())
                .map(SoundCue::forEvolution)
                .collect(Collectors.toUnmodifiableSet());

        assertThat(baseCues).hasSize(ItemType.values().length);
        assertThat(evolutionCues).hasSize(EvolutionType.values().length);
        assertThat(baseCues).doesNotContainAnyElementsOf(evolutionCues);
        assertThat(baseCues).allMatch(cue -> cue.name().endsWith("_ATTACK"));
        assertThat(evolutionCues).allMatch(cue -> cue.name().endsWith("_ATTACK"));
    }
}
