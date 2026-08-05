package kr.joseonnight.domain.membersettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class MemberSettingsTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-05T12:00:00Z");

    @Test
    void createsMemberDefaults() {
        MemberSettings settings = MemberSettings.create(1L, " 야행꾼 ", CREATED_AT);

        assertThat(settings.getMemberId()).isEqualTo(1L);
        assertThat(settings.getNickname()).isEqualTo("야행꾼");
        assertThat(settings.isMuted()).isFalse();
        assertThat(settings.getMusicVolume()).isEqualTo(70);
        assertThat(settings.getEffectsVolume()).isEqualTo(70);
        assertThat(settings.getTargetFps()).isEqualTo(TargetFps.FPS_60);
        assertThat(settings.getUpdatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void updatesAllPreferencesTogether() {
        MemberSettings settings = MemberSettings.create(1L, "야행꾼", CREATED_AT);
        Instant updatedAt = CREATED_AT.plusSeconds(30);

        settings.updatePreferences(true, 35, 80, TargetFps.AUTO, updatedAt);

        assertThat(settings.isMuted()).isTrue();
        assertThat(settings.getMusicVolume()).isEqualTo(35);
        assertThat(settings.getEffectsVolume()).isEqualTo(80);
        assertThat(settings.getTargetFps()).isEqualTo(TargetFps.AUTO);
        assertThat(settings.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void invalidUpdateDoesNotPartiallyChangeState() {
        MemberSettings settings = MemberSettings.create(1L, "야행꾼", CREATED_AT);

        assertThatThrownBy(() -> settings.updatePreferences(
                true,
                10,
                101,
                TargetFps.FPS_30,
                CREATED_AT.plusSeconds(30)
        )).isInstanceOf(IllegalArgumentException.class);

        assertThat(settings.isMuted()).isFalse();
        assertThat(settings.getMusicVolume()).isEqualTo(70);
        assertThat(settings.getEffectsVolume()).isEqualTo(70);
        assertThat(settings.getTargetFps()).isEqualTo(TargetFps.FPS_60);
        assertThat(settings.getUpdatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void rejectsInvalidNicknameAndVolumes() {
        assertThatThrownBy(() -> MemberSettings.create(1L, "a", CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MemberSettings.create(1L, "spaces are invalid", CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);

        MemberSettings settings = MemberSettings.create(1L, "야행꾼", CREATED_AT);
        assertThatThrownBy(() -> settings.updatePreferences(
                false, -1, 70, TargetFps.FPS_60, CREATED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
