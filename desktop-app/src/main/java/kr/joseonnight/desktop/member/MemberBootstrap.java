package kr.joseonnight.desktop.member;

import java.util.List;

/** Minimum lobby data needed before starting a game. */
public record MemberBootstrap(String nickname, List<CharacterOption> characters) {
    public MemberBootstrap {
        nickname = nickname == null || nickname.isBlank() ? "밤손님" : nickname;
        characters = characters == null || characters.isEmpty()
                ? List.of(new CharacterOption("dokkaebi-hunter", "도깨비 사냥꾼"))
                : List.copyOf(characters);
    }

    public static MemberBootstrap fallback() {
        return new MemberBootstrap(null, List.of());
    }

    public record CharacterOption(String characterId, String displayName) {
    }
}
