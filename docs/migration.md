# Migration and imports

## From another punishment plugin

```
/vsm import litebans --dry-run     # show what would be imported
/vsm import litebans                # do it
/vsm import advancedban --dry-run
/vsm import vanilla
```

Imports run off the main thread, log a summary and skip ids that already exist, so they are safe to run
twice.

### LiteBans

Reads `plugins/LiteBans/database.sqlite`, table `litebans_bans`. Original timestamps, staff names and
reasons are preserved. IP bans become `vsm.ipban` entries.

### AdvancedBan

Reads the configured MySQL database (`storage.mysql.*`), table `punishments`. Original staff and reasons
are preserved.

### Vanilla

Reads `banned-players.json` and `banned-ips.json` from the server root. Account bans become `BAN`, address
bans become `IP_BAN` with their expiry intact.

## Moving between storage backends

1. Stop the server.
2. Change `storage.type` in `config.yml`.
3. Start the server. Schemas are created automatically through versioned migrations, so no manual SQL is
   needed.

> Punishment records are **not** copied between backends automatically. Export with
> `/vsm export punishments` and import by hand, or keep using the backend you already have.

## Schema versions

Every SQL backend keeps a `schema_version` table. The plugin creates missing tables and indexes on start
and records the version, so upgrading never requires manual SQL. SQLite and MySQL dialect differences
(`AUTOINCREMENT` vs `AUTO_INCREMENT`) are handled by the storage layer.

## Moving to a new Paper version

The plugin targets the Paper API and declares `api-version` in `paper-plugin.yml`. Upgrading Paper rarely
needs anything from you. If a future Paper release changes the API, update `paperApiVersion` in
`gradle.properties`, rebuild through CI and re-run the release smoke test, which boots a real server with
the jar before anything is published.

## Building

```bash
./gradlew build
```

The build is pinned in `gradle.properties`:

```properties
javaVersion=25
paperApiVersion=26.2.build.133-stable
```

GitHub Actions builds every push, and tagging `v*` builds, smoke-tests against a real Paper server and
publishes the release. Nothing is ever built on a developer machine.
