package kr.joseonnight.adapter.webapi.memberapi;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.HashSet;
import java.util.List;
import kr.joseonnight.adapter.security.jwt.MemberPrincipal;
import kr.joseonnight.application.character.provided.CharacterCatalogFinder;
import kr.joseonnight.application.member.provided.MemberFinder;
import kr.joseonnight.application.member.provided.MemberView;
import kr.joseonnight.application.member.provided.MemberProgressionFinder;
import kr.joseonnight.application.membersettings.provided.MemberSettingsFinder;
import kr.joseonnight.application.membersettings.provided.MemberSettingsModifier;
import kr.joseonnight.application.membersettings.provided.MemberSettingsModifyInfo;
import kr.joseonnight.application.membersettings.provided.MemberSettingsView;
import kr.joseonnight.application.playrecord.provided.PlayRecordFinder;
import kr.joseonnight.application.playrecord.provided.PlayRecordView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/members/me")
public class MemberApi {

    private final MemberFinder memberFinder;
    private final MemberSettingsFinder settingsFinder;
    private final MemberSettingsModifier settingsModifier;
    private final MemberProgressionFinder progressionFinder;
    private final PlayRecordFinder playRecordFinder;
    private final CharacterCatalogFinder characterCatalogFinder;

    public MemberApi(
            MemberFinder memberFinder,
            MemberSettingsFinder settingsFinder,
            MemberSettingsModifier settingsModifier,
            MemberProgressionFinder progressionFinder,
            PlayRecordFinder playRecordFinder,
            CharacterCatalogFinder characterCatalogFinder
    ) {
        this.memberFinder = memberFinder;
        this.settingsFinder = settingsFinder;
        this.settingsModifier = settingsModifier;
        this.progressionFinder = progressionFinder;
        this.playRecordFinder = playRecordFinder;
        this.characterCatalogFinder = characterCatalogFinder;
    }

    @GetMapping("/bootstrap")
    public BootstrapResponse bootstrap(@AuthenticationPrincipal MemberPrincipal principal) {
        MemberView member = memberFinder.find(principal.memberId());
        MemberSettingsView settings = settingsFinder.find(principal.memberId());
        var unlockedIds = new HashSet<>(progressionFinder.unlockedCharacterIds(principal.memberId()));
        List<CharacterResponse> characters = characterCatalogFinder.findCatalog().characters().stream()
                .filter(value -> unlockedIds.contains(value.id()))
                .map(value -> new CharacterResponse(value.id(), value.displayName()))
                .toList();
        return new BootstrapResponse(
                settings.nickname(),
                new MemberResponse(member.id(), settings.nickname(), member.role().name(), member.status().name()),
                characters
        );
    }

    @GetMapping("/settings")
    public SettingsResponse settings(@AuthenticationPrincipal MemberPrincipal principal) {
        return SettingsResponse.from(settingsFinder.find(principal.memberId()));
    }

    @PatchMapping("/settings")
    public SettingsResponse modifySettings(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody SettingsRequest request
    ) {
        return SettingsResponse.from(settingsModifier.modify(
                principal.memberId(),
                new MemberSettingsModifyInfo(request.muted(), request.masterVolume())
        ));
    }

    @GetMapping("/play-records")
    public List<PlayRecordResponse> playRecords(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit
    ) {
        return playRecordFinder.recent(principal.memberId(), limit).stream()
                .map(PlayRecordResponse::from)
                .toList();
    }

    public record BootstrapResponse(
            String nickname,
            MemberResponse member,
            List<CharacterResponse> characters
    ) {
        public BootstrapResponse {
            characters = List.copyOf(characters);
        }
    }

    public record MemberResponse(Long id, String nickname, String role, String status) {
    }

    public record CharacterResponse(String characterId, String displayName) {
    }

    public record SettingsRequest(
            boolean muted,
            @Min(0) @Max(100) int masterVolume
    ) {
    }

    public record SettingsResponse(boolean muted, int masterVolume) {
        static SettingsResponse from(MemberSettingsView settings) {
            return new SettingsResponse(settings.muted(), settings.masterVolume());
        }
    }

    public record PlayRecordResponse(
            String gameSession,
            String characterId,
            long score,
            int killCount,
            int level,
            String outcome,
            long durationMillis,
            java.time.Instant endedAt
    ) {
        static PlayRecordResponse from(PlayRecordView record) {
            return new PlayRecordResponse(
                    record.gameSession().toString(),
                    record.characterId(),
                    record.score(),
                    record.killCount(),
                    record.level(),
                    record.outcome().name(),
                    record.durationMillis(),
                    record.endedAt()
            );
        }
    }
}
