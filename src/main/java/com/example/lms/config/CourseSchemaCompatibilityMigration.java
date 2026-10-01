package com.example.lms.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@Order(0)
public class CourseSchemaCompatibilityMigration implements ApplicationRunner {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(CourseSchemaCompatibilityMigration.class);
    private static final List<String> LEGACY_COLUMNS =
            List.of("category", "instructor", "level");
    private static final String SECTION_COURSE_FK = "fk_section_course";

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public CourseSchemaCompatibilityMigration(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        migrate();
    }

    public void migrate() {
        DatabasePlatform platform = detectDatabasePlatform();
        relaxLegacyCourseColumns();
        dropObsoleteInstructorForeignKeys();
        repairSectionCourseForeignKey(platform);
    }

    private void relaxLegacyCourseColumns() {
        if (!tableExists("courses")) {
            LOGGER.debug("Skipping legacy course-column migration: courses table is absent.");
            return;
        }
        for (String column : LEGACY_COLUMNS) {
            if (!columnExists("courses", column) || !hasObsoleteNotNullConstraint(column)) {
                continue;
            }
            jdbcTemplate.execute(
                    "ALTER TABLE courses ALTER COLUMN " + column + " DROP NOT NULL");
            LOGGER.info("Relaxed obsolete courses.{} NOT NULL constraint.", column);
        }
    }

    private void dropObsoleteInstructorForeignKeys() {
        if (!tableExists("courses") || !columnExists("courses", "instructor_id")) {
            return;
        }
        for (ForeignKey foreignKey : importedKeys("courses", "instructor_id")) {
            if ("users".equalsIgnoreCase(foreignKey.referencedTable())) {
                continue;
            }
            jdbcTemplate.execute("ALTER TABLE courses DROP CONSTRAINT "
                    + quoteIdentifier(foreignKey.name()));
            LOGGER.info("Removed obsolete courses.instructor_id foreign key {}.",
                    foreignKey.name());
        }
    }

    private void repairSectionCourseForeignKey(DatabasePlatform platform) {
        if (platform == DatabasePlatform.OTHER) {
            LOGGER.warn("Skipping sections.course_id foreign-key migration: "
                    + "unsupported database product.");
            return;
        }
        if (!tableExists("sections")
                || !tableExists("courses")
                || !columnExists("sections", "course_id")
                || !columnExists("courses", "id")) {
            LOGGER.info("Skipping sections.course_id foreign-key migration because "
                    + "a required table or column is absent.");
            return;
        }

        List<ForeignKey> courseForeignKeys = importedKeys("sections", "course_id");
        boolean correctForeignKeyExists = courseForeignKeys.stream()
                .anyMatch(foreignKey ->
                        "courses".equalsIgnoreCase(foreignKey.referencedTable())
                                && "id".equalsIgnoreCase(foreignKey.referencedColumn()));
        if (correctForeignKeyExists || constraintExists("sections", SECTION_COURSE_FK)) {
            LOGGER.debug("The sections.course_id foreign key already exists.");
            return;
        }

        long orphanCount = countOrphanedSectionCourseReferences();
        if (orphanCount > 0) {
            LOGGER.error("Cannot add {}: found {} sections.course_id value(s) without "
                            + "a matching courses.id. No data or constraints were changed.",
                    SECTION_COURSE_FK, orphanCount);
            return;
        }

        for (ForeignKey foreignKey : courseForeignKeys) {
            jdbcTemplate.execute("ALTER TABLE sections DROP CONSTRAINT "
                    + quoteIdentifier(foreignKey.name()));
            LOGGER.info("Removed obsolete sections.course_id foreign key {}.",
                    foreignKey.name());
        }

        switch (platform) {
            case H2 -> addSectionCourseForeignKeyForH2();
            case POSTGRESQL -> addSectionCourseForeignKeyForPostgreSql();
            case OTHER -> throw new IllegalStateException("Unexpected database platform");
        }
        LOGGER.info("Added {} from sections.course_id to courses.id.", SECTION_COURSE_FK);
    }

    private DatabasePlatform detectDatabasePlatform() {
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            String normalized = productName == null ? "" : productName.toLowerCase(Locale.ROOT);
            if (normalized.contains("h2")) {
                return DatabasePlatform.H2;
            }
            if (normalized.contains("postgresql")) {
                return DatabasePlatform.POSTGRESQL;
            }
            LOGGER.warn("Unsupported database product: {}.", productName);
            return DatabasePlatform.OTHER;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to detect the database platform", exception);
        }
    }

    private boolean tableExists(String tableName) {
        return withMetadata((metadata, catalog, schema) -> {
            try (ResultSet tables =
                         metadata.getTables(catalog, schema, null, new String[]{"TABLE"})) {
                while (tables.next()) {
                    if (tableName.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private boolean columnExists(String tableName, String columnName) {
        return withMetadata((metadata, catalog, schema) -> {
            try (ResultSet columns = metadata.getColumns(catalog, schema, null, null)) {
                while (columns.next()) {
                    if (tableName.equalsIgnoreCase(columns.getString("TABLE_NAME"))
                            && columnName.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private boolean constraintExists(String tableName, String constraintName) {
        return importedKeys(tableName, null).stream()
                .anyMatch(foreignKey -> constraintName.equalsIgnoreCase(foreignKey.name()));
    }

    private List<ForeignKey> importedKeys(String tableName, String columnName) {
        return withMetadata((metadata, catalog, schema) -> {
            List<ForeignKey> foreignKeys = new ArrayList<>();
            String metadataTableName = findMetadataTableName(
                    metadata, catalog, schema, tableName);
            if (metadataTableName == null) {
                return foreignKeys;
            }
            try (ResultSet keys =
                         metadata.getImportedKeys(catalog, schema, metadataTableName)) {
                while (keys.next()) {
                    String fkColumn = keys.getString("FKCOLUMN_NAME");
                    String fkName = keys.getString("FK_NAME");
                    if (fkName != null
                            && (columnName == null || columnName.equalsIgnoreCase(fkColumn))) {
                        foreignKeys.add(new ForeignKey(
                                fkName,
                                keys.getString("PKTABLE_NAME"),
                                keys.getString("PKCOLUMN_NAME")));
                    }
                }
            }
            return foreignKeys;
        });
    }

    private String findMetadataTableName(
            DatabaseMetaData metadata,
            String catalog,
            String schema,
            String requestedTableName) throws SQLException {
        try (ResultSet tables =
                     metadata.getTables(catalog, schema, null, new String[]{"TABLE"})) {
            while (tables.next()) {
                String actualTableName = tables.getString("TABLE_NAME");
                if (requestedTableName.equalsIgnoreCase(actualTableName)) {
                    return actualTableName;
                }
            }
            return null;
        }
    }

    private long countOrphanedSectionCourseReferences() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM sections s
                LEFT JOIN courses c ON c.id = s.course_id
                WHERE s.course_id IS NOT NULL
                  AND c.id IS NULL
                """, Long.class);
        return count == null ? 0 : count;
    }

    private void addSectionCourseForeignKeyForH2() {
        jdbcTemplate.execute("""
                ALTER TABLE sections
                ADD CONSTRAINT fk_section_course
                FOREIGN KEY (course_id) REFERENCES courses(id)
                """);
    }

    private void addSectionCourseForeignKeyForPostgreSql() {
        jdbcTemplate.execute("""
                ALTER TABLE sections
                ADD CONSTRAINT fk_section_course
                FOREIGN KEY (course_id) REFERENCES courses(id)
                NOT VALID
                """);
    }

    private boolean hasObsoleteNotNullConstraint(String column) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE LOWER(table_schema) = LOWER(CURRENT_SCHEMA())
                  AND LOWER(table_name) = 'courses'
                  AND LOWER(column_name) = ?
                  AND UPPER(is_nullable) = 'NO'
                """, Integer.class, column);
        return count != null && count > 0;
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private <T> T withMetadata(MetadataQuery<T> query) {
        try (Connection connection = dataSource.getConnection()) {
            return query.execute(
                    connection.getMetaData(), connection.getCatalog(), connection.getSchema());
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to inspect the database schema", exception);
        }
    }

    private enum DatabasePlatform {
        H2,
        POSTGRESQL,
        OTHER
    }

    private record ForeignKey(String name, String referencedTable, String referencedColumn) {
    }

    @FunctionalInterface
    private interface MetadataQuery<T> {
        T execute(DatabaseMetaData metadata, String catalog, String schema) throws SQLException;
    }
}
