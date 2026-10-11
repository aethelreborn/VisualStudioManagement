package io.github.aethelreborn.vsm.storage;

import com.zaxxer.hikari.HikariConfig;
import io.github.aethelreborn.vsm.config.VsmConfig;
import java.util.logging.Logger;

/** MySQL / MariaDB backend with a HikariCP connection pool (spec 3.1, 15.1). */
public final class MysqlStorage extends SqlStorage {

    private final VsmConfig config;

    public MysqlStorage(VsmConfig config, Logger logger) {
        super(logger, Migrations.Dialect.mysql());
        this.config = config;
    }

    @Override
    public String name() {
        return "MySQL";
    }

    @Override
    protected void configure(HikariConfig hikari) {
        String url = "jdbc:mysql://" + config.mysqlHost() + ":" + config.mysqlPort() + "/" + config.mysqlDatabase()
                + "?useSSL=" + config.mysqlUseSsl()
                + "&characterEncoding=utf8"
                + "&useUnicode=true"
                + "&serverTimezone=UTC"
                + "&allowPublicKeyRetrieval=" + !config.mysqlUseSsl()
                + "&rewriteBatchedStatements=true";
        hikari.setJdbcUrl(url);
        hikari.setUsername(config.mysqlUser());
        hikari.setPassword(config.mysqlPassword());
        hikari.setMaximumPoolSize(config.mysqlPoolMaxSize());
        hikari.setMinimumIdle(config.mysqlPoolMinIdle());
        hikari.setMaxLifetime(config.mysqlPoolMaxLifetimeMs());
        hikari.setConnectionTimeout(config.mysqlConnectionTimeoutMs());
        hikari.setPoolName("vsm-mysql");
    }
}