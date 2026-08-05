package kr.joseonnight.application.membersettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import kr.joseonnight.application.membersettings.required.MemberSettingsRepository;
import kr.joseonnight.domain.membersettings.MemberSettings;
import org.junit.jupiter.api.Test;

class MemberSettingsValidationServiceTest {

    @Test
    void normalizesNicknameBeforeCheckingDuplication() {
        MemberSettingsRepository repository = mock(MemberSettingsRepository.class);
        when(repository.existsByNickname("야행꾼")).thenReturn(true);
        MemberSettingsValidationService validation =
                new MemberSettingsValidationService(repository);

        assertThatThrownBy(() -> validation.rejectDuplicateNickname(" 야행꾼 "))
                .isInstanceOf(DuplicateNicknameException.class);
    }

    @Test
    void returnsExistingSettingsAndRejectsMissingSettings() {
        MemberSettingsRepository repository = mock(MemberSettingsRepository.class);
        MemberSettings settings = MemberSettings.create(
                1L,
                "야행꾼",
                Instant.parse("2026-08-05T12:00:00Z")
        );
        when(repository.findByMemberId(1L)).thenReturn(Optional.of(settings));
        when(repository.findByMemberId(2L)).thenReturn(Optional.empty());
        MemberSettingsValidationService validation =
                new MemberSettingsValidationService(repository);

        assertThat(validation.requireSettings(1L)).isSameAs(settings);
        assertThatThrownBy(() -> validation.requireSettings(2L))
                .isInstanceOf(MemberSettingsNotFoundException.class)
                .hasMessageContaining("2");
    }
}
