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
import kr.joseonnight.domain.member.MemberCharacter;
import kr.joseonnight.domain.member.MemberItem;
import kr.joseonnight.support.stereotype.ValidatedApplicationService;
import org.springframework.transaction.annotation.Transactional;

@ValidatedApplicationService
public final class MemberProgressionService
        implements MemberProgressionFinder, MemberProgressionManager {

    private final MemberCharacterRepository characterRepository;
    private final MemberItemRepository itemRepository;
    private final CharacterCatalogFinder characterCatalogFinder;
    private final Clock clock;

    public MemberProgressionService(
            MemberCharacterRepository characterRepository,
            MemberItemRepository itemRepository,
            CharacterCatalogFinder characterCatalogFinder,
            Clock clock
    ) {
        this.characterRepository = characterRepository;
        this.itemRepository = itemRepository;
        this.characterCatalogFinder = characterCatalogFinder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void initializeNewMember(Long memberId) {
        Instant now = Instant.now(clock);
        characterCatalogFinder.findCatalog().characters().stream()
                .filter(CharacterCatalogEntry::unlockedByDefault)
                .forEach(character -> {
                    unlockCharacter(memberId, character.id(), now);
                    unlockItem(memberId, character.startingItemId(), now);
                });
    }

    @Override
    @Transactional
    public void unlockFirstVictoryRewards(Long memberId) {
        Instant now = Instant.now(clock);
        characterCatalogFinder.findCatalog().characters().stream()
                .filter(character -> CharacterType.GALE_SHAMAN.id().equals(character.id()))
                .findFirst()
                .ifPresent(character -> {
                    unlockCharacter(memberId, character.id(), now);
                    unlockItem(memberId, character.startingItemId(), now);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> unlockedCharacterIds(Long memberId) {
        Set<String> enabledIds = characterCatalogFinder.findCatalog().characters().stream()
                .map(CharacterCatalogEntry::id)
                .collect(Collectors.toUnmodifiableSet());
        return characterRepository.findByMemberIdOrderByUnlockedAtAsc(memberId).stream()
                .map(MemberCharacter::getCharacterId)
                .filter(enabledIds::contains)
                .toList();
    }

    private void unlockCharacter(Long memberId, String characterId, Instant now) {
        if (!characterRepository.existsByMemberIdAndCharacterId(memberId, characterId)) {
            characterRepository.save(MemberCharacter.unlock(memberId, characterId, now));
        }
    }

    private void unlockItem(Long memberId, String itemId, Instant now) {
        if (!itemRepository.existsByMemberIdAndItemId(memberId, itemId)) {
            itemRepository.save(MemberItem.unlock(memberId, itemId, now));
        }
    }
}
