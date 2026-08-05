package kr.joseonnight.application.member.required;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.sql.SQLException;
import kr.joseonnight.application.membersettings.required.MemberSettingsRepository;
import kr.joseonnight.domain.member.Member;
import kr.joseonnight.domain.member.MemberCharacter;
import kr.joseonnight.domain.member.OAuthIdentity;
import kr.joseonnight.domain.member.OAuthProvider;
import kr.joseonnight.domain.membersettings.MemberSettings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import kr.joseonnight.support.test.RepositoryTest;

@RepositoryTest
class MemberRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");
    private static final String SUBJECT_HMAC = "a".repeat(64);

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private OAuthIdentityRepository identityRepository;

    @Autowired
    private MemberSettingsRepository settingsRepository;

    @Autowired
    private MemberCharacterRepository memberCharacterRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsMemberIdentityAndSettingsThroughRequiredPorts() {
        Member member = memberRepository.save(Member.register(NOW));
        identityRepository.save(OAuthIdentity.connectGoogle(member.getId(), SUBJECT_HMAC, NOW));
        settingsRepository.save(MemberSettings.create(member.getId(), "야행꾼", NOW));

        entityManager.flush();
        entityManager.clear();

        OAuthIdentity identity = identityRepository
                .findByProviderAndSubjectHmac(OAuthProvider.GOOGLE, SUBJECT_HMAC)
                .orElseThrow();
        MemberSettings settings = settingsRepository.findByMemberId(member.getId()).orElseThrow();

        assertThat(identity.getMemberId()).isEqualTo(member.getId());
        assertThat(identity.getSubjectHmac()).isEqualTo(SUBJECT_HMAC);
        assertThat(settings.getNickname()).isEqualTo("야행꾼");
        assertThat(settings.getMasterVolume()).isEqualTo(70);
        assertThat(settings.isMuted()).isFalse();
    }

    @Test
    void verifiesTheMemberCharacterPrimaryKeyConstraintAtFlushTime() {
        Member member = memberRepository.save(Member.register(NOW));
        memberCharacterRepository.save(MemberCharacter.unlock(member.getId(), "dokkaebi-hunter", NOW));
        entityManager.flush();
        entityManager.clear();

        entityManager.persist(MemberCharacter.unlock(member.getId(), "dokkaebi-hunter", NOW));

        assertThatThrownBy(() -> entityManager.flush())
                .hasRootCauseInstanceOf(SQLException.class);
    }
}
