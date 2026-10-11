package io.github.aethelreborn.vsm.storage;

import com.zaxxer.hikari.HikariConfig;
import java.nio.file.Path;
import java.util.logging.Logger;

/** SQLite backend (spec 15.1). Single writer connection keeps locking simple and predictable. */
public final class SqliteStorage extends SqlStorage {

    private final Path file;

    public SqliteStorage(Path file, Logger logger) {
        super(logger, Migrations.Dialect.sqlite());
        this.file = file;
    }

    @Override
    public String name() {
        return "SQLite";
    }

    @Override
    protected void configure(HikariConfig config) {
        config.setJdbcUrl("jdbc:sqlite:" + file.toAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setConnectionTestQuery("SELECT 1");
        config.setAutoCommit(true);
        // WAL keeps reads fast while the single writer commits.
        config.setConnectionInitSql("PRAGMA journal_mode=WAL");
        config.setConnectionInitSql("PRAGMA synchronous=NORMAL");
    }
}