package kr.joseonnight.desktop.member;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MemberBootstrapTest {
    @Test
    void lockedCharacterIsVisibleButCannotBecomeASelectableCharacter() {
        MemberBootstrap.CharacterOption unlocked = character("dokkaebi-hunter", true);
        MemberBootstrap.CharacterOption locked = character("gale-shaman", false);
        MemberBootstrap bootstrap = new MemberBootstrap("밤손님", List.of(unlocked, locked));

        assertThat(bootstrap.characters()).containsExactly(unlocked, locked);
        assertThat(bootstrap.firstUnlockedCharacter()).contains(unlocked);
        assertThat(bootstrap.unlockedCharacter("dokkaebi-hunter")).contains(unlocked);
        assertThat(bootstrap.unlockedCharacter("gale-shaman")).isEmpty();
    }

    private static MemberBootstrap.CharacterOption character(String id, boolean unlocked) {
        return new MemberBootstrap.CharacterOption(
                id,
                id,
                "설명",
                unlocked,
                new MemberBootstrap.CatalogEntry("item", "아이템"),
                new MemberBootstrap.SkillEntry("skill", "고유 스킬", "스킬 설명"));
    }
}
