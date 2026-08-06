package kr.joseonnight.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MemberTest {

    private static final Instant REGISTERED_AT = Instant.parse("2026-08-05T12:00:00Z");
    private static final String SUBJECT_HMAC = "a".repeat(64);

    @Test
    void registersAnActivePlayer() {
        Member member = Member.register(REGISTERED_AT);

        assertThat(member.getRole()).isEqualTo(MemberRole.PLAYER);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getRegisteredAt()).isEqualTo(REGISTERED_AT);
        assertThat(member.getLastLoginAt()).isEqualTo(REGISTERED_AT);
    }

    @Test
    void rootCreatesAndChangesItsInternalComponents() {
        Member member = persistedMember(1L);
        Instant unlockedAt = REGISTERED_AT.plusSeconds(10);

        OAuthIdentity identity = member.connectGoogle(SUBJECT_HMAC, REGISTERED_AT);
        MemberCharacter character = member.unlockCharacter("dokkaebi-hunter", unlockedAt);
        MemberItem item = member.unlockItem("seal-talisman", unlockedAt);
        member.recordLogin(identity, REGISTERED_AT.plusSeconds(30));

        assertThat(identity.getMemberId()).isEqualTo(1L);
        assertThat(identity.getProvider()).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(identity.getLastLoginAt()).isEqualTo(REGISTERED_AT.plusSeconds(30));
        assertThat(character.getMemberId()).isEqualTo(1L);
        assertThat(character.getCharacterId()).isEqualTo("dokkaebi-hunter");
        assertThat(item.getMemberId()).isEqualTo(1L);
        assertThat(item.getItemId()).isEqualTo("seal-talisman");
        assertThat(member.getLastLoginAt()).isEqualTo(REGISTERED_AT.plusSeconds(30));
    }

    @Test
    void rejectsInternalComponentCreationBeforePersistence() {
        Member member = Member.register(REGISTERED_AT);

        assertThatThrownBy(() -> member.connectGoogle(SUBJECT_HMAC, REGISTERED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("persisted");
    }

    @Test
    void disabledMemberCannotRecordLogin() {
        Member member = persistedMember(1L);
        OAuthIdentity identity = member.connectGoogle(SUBJECT_HMAC, REGISTERED_AT);
        member.disable();

        assertThatThrownBy(() -> member.recordLogin(identity, REGISTERED_AT.plusSeconds(30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Disabled");
        assertThat(member.getLastLoginAt()).isEqualTo(REGISTERED_AT);
        assertThat(identity.getLastLoginAt()).isEqualTo(REGISTERED_AT);
    }

    @Test
    void memberCannotChangeAnotherMembersIdentity() {
        Member owner = persistedMember(1L);
        Member anotherMember = persistedMember(2L);
        OAuthIdentity identity = owner.connectGoogle(SUBJECT_HMAC, REGISTERED_AT);

        assertThatThrownBy(() -> anotherMember.recordLogin(identity, REGISTERED_AT.plusSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("belong");
    }

    @Test
    void rejectsInvalidIdentityAndProgressionIdentifiers() {
        Member member = persistedMember(1L);

        assertThatThrownBy(() -> member.connectGoogle("raw-google-subject", REGISTERED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> member.unlockCharacter(" ", REGISTERED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> member.unlockItem("x".repeat(65), REGISTERED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Member persistedMember(Long id) {
        Member member = Member.register(REGISTERED_AT);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
