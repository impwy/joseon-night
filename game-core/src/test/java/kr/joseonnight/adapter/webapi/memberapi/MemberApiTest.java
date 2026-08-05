package kr.joseonnight.adapter.webapi.memberapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.validation.Validation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import kr.joseonnight.adapter.security.jwt.MemberPrincipal;
import kr.joseonnight.application.character.provided.CharacterCatalog;
import kr.joseonnight.application.character.provided.CharacterCatalogEntry;
import kr.joseonnight.application.character.provided.CharacterCatalogFinder;
import kr.joseonnight.application.character.provided.SkillCatalogEntry;
import kr.joseonnight.application.item.provided.ItemCatalog;
import kr.joseonnight.application.item.provided.ItemCatalogEntry;
import kr.joseonnight.application.item.provided.ItemCatalogFinder;
import kr.joseonnight.application.member.provided.MemberFinder;
import kr.joseonnight.application.member.provided.MemberProgressionFinder;
import kr.joseonnight.application.member.provided.MemberView;
import kr.joseonnight.application.membersettings.provided.MemberSettingsFinder;
import kr.joseonnight.application.membersettings.provided.MemberSettingsModifier;
import kr.joseonnight.application.membersettings.provided.MemberSettingsModifyInfo;
import kr.joseonnight.application.membersettings.provided.MemberSettingsView;
import kr.joseonnight.application.playrecord.provided.PlayRecordFinder;
import kr.joseonnight.domain.item.ItemCategory;
import kr.joseonnight.domain.member.MemberRole;
import kr.joseonnight.domain.member.MemberStatus;
import kr.joseonnight.domain.membersettings.TargetFps;
import org.junit.jupiter.api.Test;

class MemberApiTest {

    private static final long MEMBER_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");
    private static final MemberPrincipal PRINCIPAL = new MemberPrincipal(MEMBER_ID, MemberRole.PLAYER);

    @Test
    void bootstrapReturnsEveryCharacterWithUnlockAndNestedCatalogDetails() {
        MemberFinder members = mock(MemberFinder.class);
        MemberSettingsFinder settings = mock(MemberSettingsFinder.class);
        MemberSettingsModifier settingsModifier = mock(MemberSettingsModifier.class);
        MemberProgressionFinder progression = mock(MemberProgressionFinder.class);
        PlayRecordFinder playRecords = mock(PlayRecordFinder.class);
        CharacterCatalogFinder characters = mock(CharacterCatalogFinder.class);
        ItemCatalogFinder items = mock(ItemCatalogFinder.class);
        when(members.find(MEMBER_ID)).thenReturn(new MemberView(
                MEMBER_ID, MemberRole.PLAYER, MemberStatus.ACTIVE, NOW, NOW
        ));
        when(settings.find(MEMBER_ID)).thenReturn(settingsView());
        when(progression.unlockedCharacterIds(MEMBER_ID)).thenReturn(List.of("dokkaebi-hunter"));
        when(characters.findCatalog()).thenReturn(characterCatalog());
        when(items.findCatalog()).thenReturn(itemCatalog());
        MemberApi api = new MemberApi(
                members,
                settings,
                settingsModifier,
                progression,
                playRecords,
                characters,
                items
        );

        MemberApi.BootstrapResponse response = api.bootstrap(PRINCIPAL);

        assertThat(response.characters()).satisfiesExactly(
                hunter -> {
                    assertThat(hunter.id()).isEqualTo("dokkaebi-hunter");
                    assertThat(hunter.displayName()).isEqualTo("도깨비 사냥꾼");
                    assertThat(hunter.description()).isEqualTo("봉인 부적으로 싸운다.");
                    assertThat(hunter.unlocked()).isTrue();
                    assertThat(hunter.startingItem().id()).isEqualTo("seal-talisman");
                    assertThat(hunter.startingItem().displayName()).isEqualTo("봉인 부적");
                    assertThat(hunter.skill().id()).isEqualTo("protective-barrier");
                    assertThat(hunter.skill().displayName()).isEqualTo("호신결계");
                    assertThat(hunter.skill().description()).isEqualTo("한 번 충돌을 막는다.");
                },
                shaman -> {
                    assertThat(shaman.id()).isEqualTo("gale-shaman");
                    assertThat(shaman.unlocked()).isFalse();
                    assertThat(shaman.startingItem().id()).isEqualTo("flame-fan");
                    assertThat(shaman.skill().id()).isEqualTo("gale-step");
                }
        );
    }

    @Test
    void patchSettingsUsesIndependentVolumesAndTargetFps() {
        MemberSettingsModifier settingsModifier = mock(MemberSettingsModifier.class);
        MemberSettingsModifyInfo modifyInfo = new MemberSettingsModifyInfo(
                true, 45, 85, TargetFps.FPS_30
        );
        MemberSettingsView modified = new MemberSettingsView(
                MEMBER_ID, "야행꾼", true, 45, 85, TargetFps.FPS_30, NOW
        );
        when(settingsModifier.modify(MEMBER_ID, modifyInfo)).thenReturn(modified);
        MemberApi api = new MemberApi(
                mock(MemberFinder.class),
                mock(MemberSettingsFinder.class),
                settingsModifier,
                mock(MemberProgressionFinder.class),
                mock(PlayRecordFinder.class),
                mock(CharacterCatalogFinder.class),
                mock(ItemCatalogFinder.class)
        );

        MemberApi.SettingsResponse response = api.modifySettings(
                PRINCIPAL,
                new MemberApi.SettingsRequest(true, 45, 85, TargetFps.FPS_30)
        );

        assertThat(response.muted()).isTrue();
        assertThat(response.musicVolume()).isEqualTo(45);
        assertThat(response.effectsVolume()).isEqualTo(85);
        assertThat(response.targetFps()).isEqualTo(TargetFps.FPS_30);
        verify(settingsModifier).modify(MEMBER_ID, modifyInfo);
    }

    @Test
    void settingsRequestRetainsNotNullMinAndMaxValidation() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();

        var violations = validator.validate(new MemberApi.SettingsRequest(false, -1, 101, null));

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("musicVolume", "effectsVolume", "targetFps");
    }

    @Test
    void settingsRequestRejectsEveryMissingField() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        MemberApi.SettingsRequest request = new MemberApi.SettingsRequest(
                null, null, null, null);

        var violations = validator.validate(request);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder(
                        "muted", "musicVolume", "effectsVolume", "targetFps");
    }

    private static MemberSettingsView settingsView() {
        return new MemberSettingsView(
                MEMBER_ID, "야행꾼", false, 70, 70, TargetFps.FPS_60, NOW
        );
    }

    private static CharacterCatalog characterCatalog() {
        return new CharacterCatalog(List.of(
                new CharacterCatalogEntry(
                        "dokkaebi-hunter",
                        "도깨비 사냥꾼",
                        "봉인 부적으로 싸운다.",
                        new SkillCatalogEntry(
                                "protective-barrier", "호신결계", "한 번 충돌을 막는다.", Map.of()
                        ),
                        "seal-talisman",
                        true
                ),
                new CharacterCatalogEntry(
                        "gale-shaman",
                        "질풍 무녀",
                        "화염 부채로 싸운다.",
                        new SkillCatalogEntry(
                                "gale-step", "질풍걸음", "이동 속도가 증가한다.", Map.of()
                        ),
                        "flame-fan",
                        false
                )
        ));
    }

    private static ItemCatalog itemCatalog() {
        return new ItemCatalog(
                List.of(
                        new ItemCatalogEntry(
                                "seal-talisman",
                                "봉인 부적",
                                "부적을 발사한다.",
                                ItemCategory.WEAPON,
                                5,
                                Map.of()
                        ),
                        new ItemCatalogEntry(
                                "flame-fan",
                                "화염 부채",
                                "불꽃 부채를 펼친다.",
                                ItemCategory.WEAPON,
                                5,
                                Map.of()
                        )
                ),
                List.of()
        );
    }
}
