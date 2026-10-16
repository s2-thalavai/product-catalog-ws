package com.infy.catalog.infra;

import org.flywaydb.core.Flyway;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs Flyway at startup. Safe with several replicas starting at once: Flyway takes a
 * PostgreSQL advisory lock while migrating.
 */
public final class DatabaseMigrations {

    private DatabaseMigrations() {
    }

    public static void migrate(DataSource dataSource, boolean seedDemoData) {
        List<String> locations = new ArrayList<>(List.of("classpath:db/migration"));
        if (seedDemoData) {
            locations.add("classpath:db/seed");
        }
        Flyway.configure()
                .dataSource(dataSource)
                .locations(locations.toArray(String[]::new))
                // Seed scripts are applied in dev but absent from the location list in prod: don't fail on that.
                .ignoreMigrationPatterns("*:missing")
                .load()
                .migrate();
    }
}
