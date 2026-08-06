package kr.joseonnight.desktop.member;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Lobby data needed to show the whole character catalog and the member's unlocks. */
public record MemberBootstrap(String nickname, List<CharacterOption> characters) {
    public MemberBootstrap {
        nickname = nickname == null || nickname.isBlank() ? "밤손님" : nickname;
        characters = characters == null || characters.isEmpty()
                ? List.of(CharacterOption.fallback())
                : List.copyOf(characters);
    }

    public static MemberBootstrap fallback() {
        return new MemberBootstrap(null, List.of());
    }

    public Optional<CharacterOption> firstUnlockedCharacter() {
        return characters.stream().filter(CharacterOption::unlocked).findFirst();
    }

    public Optional<CharacterOption> unlockedCharacter(String characterId) {
        return characters.stream()
                .filter(CharacterOption::unlocked)
                .filter(character -> character.characterId().equals(characterId))
                .findFirst();
    }

    public record CharacterOption(
            String characterId,
            String displayName,
            String description,
            boolean unlocked,
            CatalogEntry startingItem,
            SkillEntry skill) {
        public CharacterOption {
            characterId = requireText(characterId, "characterId");
            displayName = requireText(displayName, "displayName");
            description = description == null ? "" : description;
            startingItem = Objects.requireNonNull(startingItem, "startingItem");
            skill = Objects.requireNonNull(skill, "skill");
        }

        private static CharacterOption fallback() {
            return new CharacterOption(
                    "dokkaebi-hunter",
                    "도깨비 사냥꾼",
                    "봉인 부적으로 그림자 도깨비를 사냥합니다.",
                    true,
                    new CatalogEntry("seal-talisman", "봉인 부적"),
                    new SkillEntry("protective-barrier", "호신결계", "한 번의 충돌을 막고 2초 동안 무적이 됩니다."));
        }
    }

    public record CatalogEntry(String id, String displayName) {
        public CatalogEntry {
            id = requireText(id, "id");
            displayName = requireText(displayName, "displayName");
        }
    }

    public record SkillEntry(String id, String displayName, String description) {
        public SkillEntry {
            id = requireText(id, "id");
            displayName = requireText(displayName, "displayName");
            description = description == null ? "" : description;
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
