package kr.joseonnight.domain.item;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import java.util.Objects;
import kr.joseonnight.domain.shared.JsonConfiguration;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.util.Assert;

@Entity
@Table(name = "item_evolution_recipes")
public class ItemEvolutionRecipe {

    @Id
    @Column(length = 64, updatable = false)
    private String id;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "first_item_id", nullable = false, length = 64)
    private String firstItemId;

    @Column(name = "second_item_id", nullable = false, length = 64)
    private String secondItemId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode configuration;

    @Column(nullable = false)
    private boolean enabled;

    protected ItemEvolutionRecipe() {
    }

    @SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW", justification = "JPA entity factory validates required state")
    private ItemEvolutionRecipe(
            String id,
            String displayName,
            String firstItemId,
            String secondItemId,
            Map<String, Object> configuration,
            boolean enabled
    ) {
        this.id = requireText(id, "id", 64);
        this.displayName = requireText(displayName, "displayName", 80);
        this.firstItemId = requireText(firstItemId, "firstItemId", 64);
        this.secondItemId = requireText(secondItemId, "secondItemId", 64);
        Assert.isTrue(
                !this.firstItemId.equals(this.secondItemId),
                "Evolution materials must be different"
        );
        this.configuration = JsonConfiguration.fromMap(configuration);
        this.enabled = enabled;
    }

    public static ItemEvolutionRecipe define(
            String id,
            String displayName,
            String firstItemId,
            String secondItemId,
            Map<String, Object> configuration,
            boolean enabled
    ) {
        return new ItemEvolutionRecipe(id, displayName, firstItemId, secondItemId, configuration, enabled);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getFirstItemId() {
        return firstItemId;
    }

    public String getSecondItemId() {
        return secondItemId;
    }

    public Map<String, Object> getConfiguration() {
        return JsonConfiguration.toMap(configuration);
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
