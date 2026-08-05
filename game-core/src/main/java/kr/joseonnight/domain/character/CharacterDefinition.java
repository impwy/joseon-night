package kr.joseonnight.domain.character;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import org.springframework.util.Assert;

@Entity
@Table(name = "characters")
public class CharacterDefinition {

    @Id
    @Column(length = 64, updatable = false)
    private String id;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "skill_id", nullable = false, length = 64)
    private String skillId;

    @Column(name = "starting_item_id", nullable = false, length = 64)
    private String startingItemId;

    @Column(name = "unlocked_by_default", nullable = false)
    private boolean unlockedByDefault;

    @Column(nullable = false)
    private boolean enabled;

    protected CharacterDefinition() {
    }

    @SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW", justification = "JPA entity factory validates required state")
    private CharacterDefinition(
            String id,
            String displayName,
            String description,
            String skillId,
            String startingItemId,
            boolean unlockedByDefault,
            boolean enabled
    ) {
        this.id = requireText(id, "id", 64);
        this.displayName = requireText(displayName, "displayName", 80);
        this.description = requireText(description, "description", 500);
        this.skillId = requireText(skillId, "skillId", 64);
        this.startingItemId = requireText(startingItemId, "startingItemId", 64);
        this.unlockedByDefault = unlockedByDefault;
        this.enabled = enabled;
    }

    public static CharacterDefinition define(
            String id,
            String displayName,
            String description,
            String skillId,
            String startingItemId,
            boolean unlockedByDefault,
            boolean enabled
    ) {
        return new CharacterDefinition(
                id,
                displayName,
                description,
                skillId,
                startingItemId,
                unlockedByDefault,
                enabled
        );
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getSkillId() {
        return skillId;
    }

    public String getStartingItemId() {
        return startingItemId;
    }

    public boolean isUnlockedByDefault() {
        return unlockedByDefault;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private static String requireText(String value, String field, int maximumLength) {
        Objects.requireNonNull(value, field);
        Assert.isTrue(
                !value.isBlank() && value.length() <= maximumLength,
                field + " must contain between 1 and " + maximumLength + " characters"
        );
        return value;
    }
}
