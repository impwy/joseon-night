package kr.joseonnight.application.membersettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import kr.joseonnight.application.member.required.MemberRepository;
import kr.joseonnight.application.membersettings.provided.MemberSettingsCreator;
import kr.joseonnight.application.membersettings.provided.MemberSettingsFinder;
import kr.joseonnight.application.membersettings.provided.MemberSettingsModifier;
import kr.joseonnight.application.membersettings.provided.MemberSettingsModifyInfo;
import kr.joseonnight.application.membersettings.provided.MemberSettingsView;
import kr.joseonnight.domain.member.Member;
import kr.joseonnight.domain.membersettings.TargetFps;
import kr.joseonnight.support.test.ApplicationServiceTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@ApplicationServiceTest
class MemberSettingsServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberSettingsCreator settingsCreator;

    @Autowired
    private MemberSettingsFinder settingsFinder;

    @Autowired
    private MemberSettingsModifier settingsModifier;

    @Autowired
    private EntityManager entityManager;

    @Test
    void createsDefaultsAndPersistsModifiedPreferencesThroughProvidedPorts() {
        Member member = memberRepository.save(Member.register(NOW));
        entityManager.flush();
        entityManager.clear();

        MemberSettingsView created = settingsCreator.create(member.getId(), "야행꾼");

        assertThat(created.muted()).isFalse();
        assertThat(created.musicVolume()).isEqualTo(60);
        assertThat(created.effectsVolume()).isEqualTo(70);
        assertThat(created.targetFps()).isEqualTo(TargetFps.FPS_60);
        entityManager.flush();
        entityManager.clear();

        settingsModifier.modify(
                member.getId(),
                new MemberSettingsModifyInfo(true, 35, 80, TargetFps.AUTO)
        );
        entityManager.flush();
        entityManager.clear();

        MemberSettingsView modified = settingsFinder.find(member.getId());

        assertThat(modified.muted()).isTrue();
        assertThat(modified.musicVolume()).isEqualTo(35);
        assertThat(modified.effectsVolume()).isEqualTo(80);
        assertThat(modified.targetFps()).isEqualTo(TargetFps.AUTO);
    }

    @Test
    void validatesBothVolumesAndTargetFpsBeforeModification() {
        assertThatThrownBy(() -> settingsModifier.modify(
                1L,
                new MemberSettingsModifyInfo(false, -1, 70, TargetFps.FPS_60)
        )).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> settingsModifier.modify(
                1L,
                new MemberSettingsModifyInfo(false, 70, 101, TargetFps.FPS_60)
        )).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> settingsModifier.modify(
                1L,
                new MemberSettingsModifyInfo(false, 70, 70, null)
        )).isInstanceOf(ConstraintViolationException.class);
    }
}
