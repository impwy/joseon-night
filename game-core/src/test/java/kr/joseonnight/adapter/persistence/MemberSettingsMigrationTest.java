package kr.joseonnight.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import kr.joseonnight.support.test.RepositoryTest;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RepositoryTest
class MemberSettingsMigrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void migratesExistingMasterVolumeToBothIndependentVolumes() throws SQLException {
        String schema = "member_settings_v4_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE SCHEMA " + schema);
        try {
            Flyway.configure()
                    .dataSource(dataSource)
                    .defaultSchema(schema)
                    .schemas(schema)
                    .target(MigrationVersion.fromVersion("3"))
                    .load()
                    .migrate();
            execute("""
                    INSERT INTO SCHEMA_NAME.members (
                        id, role, status, registered_at, last_login_at
                    ) VALUES (
                        1, 'PLAYER', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """.replace("SCHEMA_NAME", schema));
            execute("""
                    INSERT INTO SCHEMA_NAME.member_settings (
                        id, member_id, nickname, muted, master_volume, updated_at
                    ) VALUES (
                        1, 1, '야행꾼', FALSE, 42, CURRENT_TIMESTAMP
                    )
                    """.replace("SCHEMA_NAME", schema));

            Flyway.configure()
                    .dataSource(dataSource)
                    .defaultSchema(schema)
                    .schemas(schema)
                    .load()
                    .migrate();

            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("""
                         SELECT music_volume, effects_volume, target_fps
                         FROM SCHEMA_NAME.member_settings
                         WHERE member_id = 1
                         """.replace("SCHEMA_NAME", schema))) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt("music_volume")).isEqualTo(42);
                assertThat(result.getInt("effects_volume")).isEqualTo(42);
                assertThat(result.getString("target_fps")).isEqualTo("FPS_60");
            }
        } finally {
            execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
