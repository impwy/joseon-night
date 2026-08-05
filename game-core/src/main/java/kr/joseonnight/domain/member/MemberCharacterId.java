package kr.joseonnight.domain.member;

import java.io.Serializable;
import java.util.Objects;

public class MemberCharacterId implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long memberId;
    private String characterId;

    public MemberCharacterId() {
    }

    public MemberCharacterId(Long memberId, String characterId) {
        this.memberId = memberId;
        this.characterId = characterId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MemberCharacterId that)) {
            return false;
        }
        return Objects.equals(memberId, that.memberId)
                && Objects.equals(characterId, that.characterId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memberId, characterId);
    }
}
