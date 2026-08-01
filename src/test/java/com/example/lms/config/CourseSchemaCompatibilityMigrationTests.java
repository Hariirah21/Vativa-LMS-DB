package com.example.lms.config;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseSchemaCompatibilityMigrationTests {

    private JdbcTemplate jdbcTemplate;
    private CourseSchemaCompatibilityMigration migration;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:migration-" + System.nanoTime()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        jdbcTemplate = new JdbcTemplate(dataSource);
        migration = new CourseSchemaCompatibilityMigration(jdbcTemplate, dataSource);
    }

    @Test
    void addsH2CompatibleConstraintAndCanRunRepeatedly() {
        createCourseAndSectionTables();

        migration.migrate();
        migration.migrate();

        assertThat(importedKeyCount()).isEqualTo(1);
    }

    @Test
    void leavesAnExistingConstraintAlone() {
        createCourseAndSectionTables();
        jdbcTemplate.execute("""
                ALTER TABLE sections
                ADD CONSTRAINT fk_section_course
                FOREIGN KEY (course_id) REFERENCES courses(id)
                """);

        migration.migrate();

        assertThat(importedKeyCount()).isEqualTo(1);
    }

    @Test
    void skipsWhenRequiredTableOrColumnIsMissing() {
        migration.migrate();
        jdbcTemplate.execute("CREATE TABLE courses (id BIGINT PRIMARY KEY)");

        migration.migrate();

        assertThat(importedKeyCount()).isZero();
    }

    @Test
    void preservesOrphanedDataAndDoesNotAddConstraint() {
        createCourseAndSectionTables();
        jdbcTemplate.update("INSERT INTO sections (id, course_id) VALUES (1, 999)");

        migration.migrate();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sections WHERE course_id = 999", Integer.class))
                .isEqualTo(1);
        assertThat(importedKeyCount()).isZero();
    }

    @Test
    void skipsForeignKeyMigrationForUnsupportedDatabase() throws Exception {
        DataSource unsupportedDataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        ResultSet noTables = mock(ResultSet.class);
        JdbcTemplate template = mock(JdbcTemplate.class);
        when(unsupportedDataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("UnsupportedDB");
        when(metadata.getTables(any(), any(), any(), any())).thenReturn(noTables);
        when(noTables.next()).thenReturn(false);

        new CourseSchemaCompatibilityMigration(template, unsupportedDataSource).migrate();

        verify(template, never()).execute(anyString());
    }

    private void createCourseAndSectionTables() {
        jdbcTemplate.execute("CREATE TABLE courses (id BIGINT PRIMARY KEY)");
        jdbcTemplate.execute("""
                CREATE TABLE sections (
                    id BIGINT PRIMARY KEY,
                    course_id BIGINT
                )
                """);
    }

    private int importedKeyCount() {
        DataSource dataSource = jdbcTemplate.getDataSource();
        try (var connection = dataSource.getConnection();
             var keys = connection.getMetaData().getImportedKeys(
                     connection.getCatalog(), connection.getSchema(), "SECTIONS")) {
            int count = 0;
            while (keys.next()) {
                count++;
            }
            return count;
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
