package kr.joseonnight.adapter.webapi.memberapi;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.joseonnight.adapter.security.jwt.MemberPrincipal;
import kr.joseonnight.application.character.provided.CharacterCatalogEntry;
import kr.joseonnight.application.character.provided.CharacterCatalogFinder;
import kr.joseonnight.application.item.provided.ItemCatalogEntry;
import kr.joseonnight.application.item.provided.ItemCatalogFinder;
import kr.joseonnight.application.member.provided.MemberFinder;
import kr.joseonnight.application.member.provided.MemberView;
import kr.joseonnight.application.member.provided.MemberProgressionFinder;
import kr.joseonnight.application.membersettings.provided.MemberSettingsFinder;
import kr.joseonnight.application.membersettings.provided.MemberSettingsModifier;
import kr.joseonnight.application.membersettings.provided.MemberSettingsModifyInfo;
import kr.joseonnight.application.membersettings.provided.MemberSettingsView;
import kr.joseonnight.application.playrecord.provided.PlayRecordFinder;
import kr.joseonnight.application.playrecord.provided.PlayRecordView;
import kr.joseonnight.domain.membersettings.TargetFps;
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
    private final ItemCatalogFinder itemCatalogFinder;

    public MemberApi(
            MemberFinder memberFinder,
            MemberSettingsFinder settingsFinder,
            MemberSettingsModifier settingsModifier,
            MemberProgressionFinder progressionFinder,
            PlayRecordFinder playRecordFinder,
            CharacterCatalogFinder characterCatalogFinder,
            ItemCatalogFinder itemCatalogFinder
    ) {
        this.memberFinder = memberFinder;
        this.settingsFinder = settingsFinder;
        this.settingsModifier = settingsModifier;
        this.progressionFinder = progressionFinder;
        this.playRecordFinder = playRecordFinder;
        this.characterCatalogFinder = characterCatalogFinder;
        this.itemCatalogFinder = itemCatalogFinder;
    }

    @GetMapping("/bootstrap")
    public BootstrapResponse bootstrap(@AuthenticationPrincipal MemberPrincipal principal) {
        MemberView member = memberFinder.find(principal.memberId());
        MemberSettingsView settings = settingsFinder.find(principal.memberId());
        var unlockedIds = new HashSet<>(progressionFinder.unlockedCharacterIds(principal.memberId()));
        Map<String, ItemCatalogEntry> items = itemCatalogFinder.findCatalog().items().stream()
                .collect(Collectors.toUnmodifiableMap(ItemCatalogEntry::id, Function.identity()));
        List<CharacterResponse> characters = characterCatalogFinder.findCatalog().characters().stream()
                .map(value -> toCharacterResponse(value, items, unlockedIds.contains(value.id())))
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
                new MemberSettingsModifyInfo(
                        request.muted(),
                        request.musicVolume(),
                        request.effectsVolume(),
                        request.targetFps()
                )
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

    private static CharacterResponse toCharacterResponse(
            CharacterCatalogEntry character,
            Map<String, ItemCatalogEntry> items,
            boolean unlocked
    ) {
        ItemCatalogEntry startingItem = Objects.requireNonNull(
                items.get(character.startingItemId()),
                () -> "Starting item is absent from the catalog: " + character.startingItemId()
        );
        var skill = character.skill();
        return new CharacterResponse(
                character.id(),
                character.displayName(),
                character.description(),
                unlocked,
                new StartingItemResponse(startingItem.id(), startingItem.displayName()),
                new SkillResponse(skill.id(), skill.displayName(), skill.description())
        );
    }

    public record CharacterResponse(
            String id,
            String displayName,
            String description,
            boolean unlocked,
            StartingItemResponse startingItem,
            SkillResponse skill
    ) {
    }

    public record StartingItemResponse(String id, String displayName) {
    }

    public record SkillResponse(String id, String displayName, String description) {
    }

    public record SettingsRequest(
            @NotNull Boolean muted,
            @NotNull @Min(0) @Max(100) Integer musicVolume,
            @NotNull @Min(0) @Max(100) Integer effectsVolume,
            @NotNull TargetFps targetFps
    ) {
    }

    public record SettingsResponse(
            boolean muted,
            int musicVolume,
            int effectsVolume,
            TargetFps targetFps
    ) {
        static SettingsResponse from(MemberSettingsView settings) {
            return new SettingsResponse(
                    settings.muted(),
                    settings.musicVolume(),
                    settings.effectsVolume(),
                    settings.targetFps()
            );
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
