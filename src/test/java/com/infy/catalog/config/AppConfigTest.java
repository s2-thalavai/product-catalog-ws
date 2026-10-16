package com.infy.catalog.config;

import com.infy.catalog.config.AppConfig.StoreType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppConfigTest {

    @Test
    void defaultsGiveARunnableInMemoryConfig() {
        AppConfig config = AppConfig.fromEnvironment(Map.of());

        assertEquals(8080, config.port());
        assertEquals("/ws/v1/products", config.contextPath());
        assertEquals(StoreType.MEMORY, config.storeType());
        assertEquals(20, config.defaultPageSize());
        assertEquals(100, config.maxPageSize());
        assertFalse(config.seedData());
    }

    @Test
    void readsPostgresSettings() {
        AppConfig config = AppConfig.fromEnvironment(Map.of(
                "STORE_TYPE", "postgres",
                "DB_URL", "jdbc:postgresql://db:5432/catalog",
                "DB_USERNAME", "catalog",
                "DB_PASSWORD", "secret",
                "SEED_DATA", "true",
                "PAGE_SIZE_MAX", "50"));

        assertEquals(StoreType.POSTGRES, config.storeType());
        assertEquals("jdbc:postgresql://db:5432/catalog", config.dbUrl());
        assertTrue(config.seedData());
        assertEquals(50, config.maxPageSize());
    }

    @Test
    void postgresWithoutCredentialsFailsFast() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> AppConfig.fromEnvironment(Map.of("STORE_TYPE", "postgres")));
        assertTrue(e.getMessage().contains("DB_URL"));
    }

    @Test
    void rejectsUnknownStoreTypeAndNonNumericValues() {
        assertThrows(IllegalArgumentException.class, () -> AppConfig.fromEnvironment(Map.of("STORE_TYPE", "mongo")));
        assertThrows(IllegalArgumentException.class, () -> AppConfig.fromEnvironment(Map.of("APP_PORT", "eighty")));
        assertThrows(IllegalArgumentException.class,
                () -> AppConfig.fromEnvironment(Map.of("PAGE_SIZE_DEFAULT", "50", "PAGE_SIZE_MAX", "10")));
    }

    @Test
    void toStringNeverContainsThePassword() {
        AppConfig config = AppConfig.fromEnvironment(Map.of(
                "STORE_TYPE", "postgres", "DB_URL", "jdbc:postgresql://db/catalog",
                "DB_USERNAME", "catalog", "DB_PASSWORD", "s3cr3t-value"));

        assertFalse(config.toString().contains("s3cr3t-value"));
    }
}
