package kr.joseonnight.domain.member;

import java.io.Serializable;
import java.util.Objects;

public class MemberItemId implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long memberId;
    private String itemId;

    public MemberItemId() {
    }

    public MemberItemId(Long memberId, String itemId) {
        this.memberId = memberId;
        this.itemId = itemId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MemberItemId that)) {
            return false;
        }
        return Objects.equals(memberId, that.memberId) && Objects.equals(itemId, that.itemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memberId, itemId);
    }
}
