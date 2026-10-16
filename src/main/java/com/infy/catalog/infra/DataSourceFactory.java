package com.infy.catalog.infra;

import com.infy.catalog.config.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public final class DataSourceFactory {

    private DataSourceFactory() {
    }

    public static HikariDataSource create(AppConfig config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("catalog-db");
        hikari.setJdbcUrl(config.dbUrl());
        hikari.setUsername(config.dbUsername());
        hikari.setPassword(config.dbPassword());
        hikari.setMaximumPoolSize(config.dbPoolSize());
        hikari.setMinimumIdle(Math.min(2, config.dbPoolSize()));
        // Fail a single request fast when the pool is exhausted or the DB is down...
        hikari.setConnectionTimeout(3_000);
        // ...but at startup, wait up to 60 s for PostgreSQL (it often starts after the app in k8s).
        hikari.setInitializationFailTimeout(60_000);
        return new HikariDataSource(hikari);
    }
}
