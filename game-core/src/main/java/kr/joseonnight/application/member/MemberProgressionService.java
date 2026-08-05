package kr.joseonnight.application.member;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import kr.joseonnight.application.character.provided.CharacterCatalogEntry;
import kr.joseonnight.application.character.provided.CharacterCatalogFinder;
import kr.joseonnight.application.member.provided.MemberProgressionFinder;
import kr.joseonnight.application.member.provided.MemberProgressionManager;
import kr.joseonnight.application.member.required.MemberCharacterRepository;
import kr.joseonnight.application.member.required.MemberItemRepository;
import kr.joseonnight.domain.gameplay.CharacterType;
import kr.joseonnight.domain.member.Member;
import kr.joseonnight.domain.member.MemberCharacter;
import kr.joseonnight.support.stereotype.ValidatedApplicationService;
import org.springframework.transaction.annotation.Transactional;

@ValidatedApplicationService
public final class MemberProgressionService
        implements MemberProgressionFinder, MemberProgressionManager {

    private final MemberCharacterRepository characterRepository;
    private final MemberItemRepository itemRepository;
    private final CharacterCatalogFinder characterCatalogFinder;
    private final MemberValidationService validationService;
    private final Clock clock;

    public MemberProgressionService(
            MemberCharacterRepository characterRepository,
            MemberItemRepository itemRepository,
            CharacterCatalogFinder characterCatalogFinder,
            MemberValidationService validationService,
            Clock clock
    ) {
        this.characterRepository = characterRepository;
        this.itemRepository = itemRepository;
        this.characterCatalogFinder = characterCatalogFinder;
        this.validationService = validationService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void initializeNewMember(Long memberId) {
        Member member = validationService.requireMember(memberId);
        Instant now = Instant.now(clock);
        characterCatalogFinder.findCatalog().characters().stream()
                .filter(CharacterCatalogEntry::unlockedByDefault)
                .forEach(character -> {
                    unlockCharacter(member, character.id(), now);
                    unlockItem(member, character.startingItemId(), now);
                });
    }

    @Override
    @Transactional
    public void unlockFirstVictoryRewards(Long memberId) {
        Member member = validationService.requireMember(memberId);
        Instant now = Instant.now(clock);
        characterCatalogFinder.findCatalog().characters().stream()
                .filter(character -> CharacterType.GALE_SHAMAN.id().equals(character.id()))
                .findFirst()
                .ifPresent(character -> {
                    unlockCharacter(member, character.id(), now);
                    unlockItem(member, character.startingItemId(), now);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> unlockedCharacterIds(Long memberId) {
        validationService.requireMember(memberId);
        Set<String> enabledIds = characterCatalogFinder.findCatalog().characters().stream()
                .map(CharacterCatalogEntry::id)
                .collect(Collectors.toUnmodifiableSet());
        return characterRepository.findByMemberIdOrderByUnlockedAtAsc(memberId).stream()
                .map(MemberCharacter::getCharacterId)
                .filter(enabledIds::contains)
                .toList();
    }

    private void unlockCharacter(Member member, String characterId, Instant now) {
        if (validationService.requiresCharacterUnlock(member.getId(), characterId)) {
            characterRepository.save(member.unlockCharacter(characterId, now));
        }
    }

    private void unlockItem(Member member, String itemId, Instant now) {
        if (validationService.requiresItemUnlock(member.getId(), itemId)) {
            itemRepository.save(member.unlockItem(itemId, now));
        }
    }
}
