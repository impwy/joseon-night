package kr.joseonnight.application.character;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.joseonnight.application.character.provided.CharacterCatalog;
import kr.joseonnight.application.character.provided.CharacterCatalogEntry;
import kr.joseonnight.application.character.provided.CharacterCatalogFinder;
import kr.joseonnight.application.character.provided.SkillCatalogEntry;
import kr.joseonnight.application.character.required.CharacterCatalogCache;
import kr.joseonnight.application.character.required.CharacterDefinitionRepository;
import kr.joseonnight.application.character.required.SkillDefinitionRepository;
import kr.joseonnight.domain.character.CharacterDefinition;
import kr.joseonnight.domain.character.SkillDefinition;
import kr.joseonnight.support.stereotype.ValidatedApplicationService;
import org.springframework.transaction.annotation.Transactional;

@ValidatedApplicationService
public final class CharacterCatalogService implements CharacterCatalogFinder {

    private final CharacterDefinitionRepository characterRepository;
    private final SkillDefinitionRepository skillRepository;
    private final CharacterCatalogCache cache;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Application port is an injected collaborator")
    public CharacterCatalogService(
            CharacterDefinitionRepository characterRepository,
            SkillDefinitionRepository skillRepository,
            CharacterCatalogCache cache
    ) {
        this.characterRepository = characterRepository;
        this.skillRepository = skillRepository;
        this.cache = cache;
    }

    @Override
    @Transactional(readOnly = true)
    public CharacterCatalog findCatalog() {
        return cache.find().orElseGet(this::loadFromPostgreSql);
    }

    private CharacterCatalog loadFromPostgreSql() {
        Map<String, SkillCatalogEntry> skills = skillRepository.findAllByEnabledTrueOrderByIdAsc().stream()
                .map(CharacterCatalogService::toSkillEntry)
                .collect(Collectors.toUnmodifiableMap(SkillCatalogEntry::id, Function.identity()));
        CharacterCatalog catalog = new CharacterCatalog(
                characterRepository.findAllByEnabledTrueOrderByIdAsc().stream()
                        .filter(character -> skills.containsKey(character.getSkillId()))
                        .map(character -> toCharacterEntry(character, skills.get(character.getSkillId())))
                        .toList()
        );
        cache.put(catalog);
        return catalog;
    }

    private static SkillCatalogEntry toSkillEntry(SkillDefinition skill) {
        return new SkillCatalogEntry(
                skill.getId(),
                skill.getDisplayName(),
                skill.getDescription(),
                skill.getConfiguration()
        );
    }

    private static CharacterCatalogEntry toCharacterEntry(
            CharacterDefinition character,
            SkillCatalogEntry skill
    ) {
        return new CharacterCatalogEntry(
                character.getId(),
                character.getDisplayName(),
                character.getDescription(),
                skill,
                character.getStartingItemId(),
                character.isUnlockedByDefault()
        );
    }
}
