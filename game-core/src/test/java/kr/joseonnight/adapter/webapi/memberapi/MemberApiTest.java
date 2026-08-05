package kr.joseonnight.adapter.webapi.memberapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import kr.joseonnight.adapter.security.jwt.MemberPrincipal;
import kr.joseonnight.adapter.webapi.RestApiExceptionHandler;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

class MemberApiTest {

    private static final long MEMBER_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");
    private static final MemberPrincipal PRINCIPAL = new MemberPrincipal(MEMBER_ID, MemberRole.PLAYER);
    private static final Authentication AUTHENTICATION = UsernamePasswordAuthenticationToken.authenticated(
            PRINCIPAL,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_PLAYER"))
    );

    private MockMvcTester mvc;
    private MemberFinder members;
    private MemberSettingsFinder settings;
    private MemberSettingsModifier settingsModifier;
    private MemberProgressionFinder progression;
    private PlayRecordFinder playRecords;
    private CharacterCatalogFinder characters;
    private ItemCatalogFinder items;

    @BeforeEach
    void setUp() {
        members = mock(MemberFinder.class);
        settings = mock(MemberSettingsFinder.class);
        settingsModifier = mock(MemberSettingsModifier.class);
        progression = mock(MemberProgressionFinder.class);
        playRecords = mock(PlayRecordFinder.class);
        characters = mock(CharacterCatalogFinder.class);
        items = mock(ItemCatalogFinder.class);

        MemberApi api = new MemberApi(
                members,
                settings,
                settingsModifier,
                progression,
                playRecords,
                characters,
                items
        );
        mvc = MockMvcTester.of(
                List.of(api),
                builder -> builder
                        .setControllerAdvice(new RestApiExceptionHandler())
                        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                        .addFilters(new SecurityContextHolderFilter(
                                new HttpSessionSecurityContextRepository()))
                        .build()
        );
    }

    @Test
    void bootstrapReturnsEveryCharacterWithUnlockAndNestedCatalogDetails() {
        when(members.find(MEMBER_ID)).thenReturn(new MemberView(
                MEMBER_ID, MemberRole.PLAYER, MemberStatus.ACTIVE, NOW, NOW
        ));
        when(settings.find(MEMBER_ID)).thenReturn(settingsView());
        when(progression.unlockedCharacterIds(MEMBER_ID)).thenReturn(List.of("dokkaebi-hunter"));
        when(characters.findCatalog()).thenReturn(characterCatalog());
        when(items.findCatalog()).thenReturn(itemCatalog());

        var result = mvc.get()
                .uri("/api/v1/members/me/bootstrap")
                .with(authentication(AUTHENTICATION))
                .exchange();

        assertThat(result).hasStatusOk();
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.characters").asArray().hasSize(2);
        json.extractingPath("$.characters[0].id").asString().isEqualTo("dokkaebi-hunter");
        json.extractingPath("$.characters[0].displayName").asString().isEqualTo("도깨비 사냥꾼");
        json.extractingPath("$.characters[0].description").asString().isEqualTo("봉인 부적으로 싸운다.");
        json.extractingPath("$.characters[0].unlocked").asBoolean().isTrue();
        json.extractingPath("$.characters[0].startingItem.id").asString().isEqualTo("seal-talisman");
        json.extractingPath("$.characters[0].startingItem.displayName").asString().isEqualTo("봉인 부적");
        json.extractingPath("$.characters[0].skill.id").asString().isEqualTo("protective-barrier");
        json.extractingPath("$.characters[0].skill.displayName").asString().isEqualTo("호신결계");
        json.extractingPath("$.characters[0].skill.description").asString().isEqualTo("한 번 충돌을 막는다.");
        json.extractingPath("$.characters[1].id").asString().isEqualTo("gale-shaman");
        json.extractingPath("$.characters[1].unlocked").asBoolean().isFalse();
        json.extractingPath("$.characters[1].startingItem.id").asString().isEqualTo("flame-fan");
        json.extractingPath("$.characters[1].skill.id").asString().isEqualTo("gale-step");
        verify(members).find(MEMBER_ID);
    }

    @Test
    void patchSettingsUsesAuthenticatedMemberIndependentVolumesAndTargetFps() {
        MemberSettingsModifyInfo modifyInfo = new MemberSettingsModifyInfo(
                true, 45, 85, TargetFps.FPS_30
        );
        MemberSettingsView modified = new MemberSettingsView(
                MEMBER_ID, "야행꾼", true, 45, 85, TargetFps.FPS_30, NOW
        );
        when(settingsModifier.modify(MEMBER_ID, modifyInfo)).thenReturn(modified);

        var result = mvc.patch()
                .uri("/api/v1/members/me/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "muted": true,
                          "musicVolume": 45,
                          "effectsVolume": 85,
                          "targetFps": "FPS_30"
                        }
                """)
                .with(authentication(AUTHENTICATION))
                .exchange();

        assertThat(result).hasStatusOk();
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.muted").asBoolean().isTrue();
        json.extractingPath("$.musicVolume").asNumber().isEqualTo(45);
        json.extractingPath("$.effectsVolume").asNumber().isEqualTo(85);
        json.extractingPath("$.targetFps").asString().isEqualTo("FPS_30");
        verify(settingsModifier).modify(MEMBER_ID, modifyInfo);
    }

    @Test
    void patchSettingsRejectsOutOfRangeVolumesAndMissingTargetFps() {
        var result = mvc.patch()
                .uri("/api/v1/members/me/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "muted": false,
                          "musicVolume": -1,
                          "effectsVolume": 101,
                          "targetFps": null
                        }
                """)
                .with(authentication(AUTHENTICATION))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(settingsModifier);
    }

    @Test
    void patchSettingsRejectsEveryMissingField() {
        var result = mvc.patch()
                .uri("/api/v1/members/me/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(authentication(AUTHENTICATION))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(settingsModifier);
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
