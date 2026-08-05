package kr.joseonnight.domain.item;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import java.util.Objects;
import kr.joseonnight.domain.shared.JsonConfiguration;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "items")
public class ItemDefinition {

    @Id
    @Column(length = 64, updatable = false)
    private String id;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    private ItemCategory category;

    @Column(name = "max_level", nullable = false)
    private int maxLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode configuration;

    @Column(nullable = false)
    private boolean enabled;

    protected ItemDefinition() {
    }

    @SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW", justification = "JPA entity factory validates required state")
    private ItemDefinition(
            String id,
            String displayName,
            String description,
            ItemCategory category,
            int maxLevel,
            Map<String, Object> configuration,
            boolean enabled
    ) {
        this.id = requireText(id, "id", 64);
        this.displayName = requireText(displayName, "displayName", 80);
        this.description = requireText(description, "description", 500);
        this.category = Objects.requireNonNull(category, "category");
        if (maxLevel < 1) {
            throw new IllegalArgumentException("maxLevel must be at least 1");
        }
        this.maxLevel = maxLevel;
        this.configuration = JsonConfiguration.fromMap(configuration);
        this.enabled = enabled;
    }

    public static ItemDefinition define(
            String id,
            String displayName,
            String description,
            ItemCategory category,
            int maxLevel,
            Map<String, Object> configuration,
            boolean enabled
    ) {
        return new ItemDefinition(id, displayName, description, category, maxLevel, configuration, enabled);
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

    public ItemCategory getCategory() {
        return category;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public Map<String, Object> getConfiguration() {
        return JsonConfiguration.toMap(configuration);
    }

    public boolean isEnabled() {
        return enabled;
    }

    private static String requireText(String value, String field, int maximumLength) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(field + " must contain between 1 and " + maximumLength + " characters");
        }
        return value;
    }
}
