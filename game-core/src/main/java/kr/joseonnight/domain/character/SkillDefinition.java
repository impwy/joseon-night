package kr.joseonnight.domain.character;

import static lombok.AccessLevel.PROTECTED;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import java.util.Objects;
import kr.joseonnight.domain.shared.JsonConfiguration;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.util.Assert;

@Entity
@Table(name = "skills")
@NoArgsConstructor(access = PROTECTED)
public class SkillDefinition {

    @Id
    @Column(length = 64, updatable = false)
    private String id;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(nullable = false, length = 500)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode configuration;

    @Column(nullable = false)
    private boolean enabled;

    @SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW", justification = "JPA entity factory validates required state")
    private SkillDefinition(
            String id,
            String displayName,
            String description,
            Map<String, Object> configuration,
            boolean enabled
    ) {
        this.id = requireText(id, "id", 64);
        this.displayName = requireText(displayName, "displayName", 80);
        this.description = requireText(description, "description", 500);
        this.configuration = JsonConfiguration.fromMap(configuration);
        this.enabled = enabled;
    }

    public static SkillDefinition define(
            String id,
            String displayName,
            String description,
            Map<String, Object> configuration,
            boolean enabled
    ) {
        return new SkillDefinition(id, displayName, description, configuration, enabled);
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
