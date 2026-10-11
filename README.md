# Visual Studio Management (VSM)

Self-contained staff, moderation, IP-ban and movement-tools plugin for **Paper**.

Everything staff do — from a command, a GUI click, the public API or auto-moderation — flows through a
single choke point (`ActionService`), so permission checks, outranking, exemptions, limits, auditing and
webhooks can never be bypassed.

---

## Requirements

| | |
|---|---|
| Server | Paper 26.1 or newer (built and smoke-tested against **26.2 build 133**) |
| Java | 21 or newer (26.x requires **Java 25**) |
| Database | SQLite by default; YAML and MySQL / MariaDB supported |
| Optional | PlaceholderAPI, a Discord webhook URL |

## Install

1. Download the latest `VisualStudioManagement-<version>.jar` from the
   [releases page](https://github.com/aethelreborn/VisualStudioManagement/releases).
2. Drop it in `plugins/`.
3. Start the server. On first run the plugin writes `config.yml`, `messages.yml`, `ranks.yml`,
   `templates.yml`, `reasons.yml`, `automod.yml` and `permissions.yml` into
   `plugins/VisualStudioManagement/`.
4. Make yourself staff from the console:

   ```
   staff add <your-name> owner
   ```

> The first start resolves HikariCP, the SQLite driver and the MySQL driver through Paper's library
> loader, so the server needs network access the first time it starts.

## Quick start

```
vsm                                  # about and health
staff                                # staff menu
staff add Steve moderator             # whitelist by UUID
vsm warn Steve Spamming chat
vsm tempmute Griefer 30m Harassment
vsm ipban Steve Ban evasion
vsm checkban Steve
vsm reload
```

Everything is also reachable as a standalone command (`/ban`, `/tp`, `/freeze`, ...). Set
`commands.standalone-aliases: false` to force everything under `/vsm`.

## Ranks

Staff authority comes **only** from the plugin's whitelist — operator status grants nothing
(`settings.op-bypass` is `false` by default).

```
player (0) → youtuber (15, side branch) → helper (20) → moderator (40)
           → senior_mod (60) → admin (80) → owner (100)
```

* A rank inherits every permission of its parent, resolved once at load time.
* An actor may only act on a **strictly lower** weight; equal ranks cannot act on each other.
* Nobody may promote to a rank at or above their own, and nobody may change their own rank.
* The last remaining `owner` cannot be removed or demoted.
* `youtuber` is a **side branch**: it inherits from `player` only, never from the staff ladder, and is
  flagged `staff: false` so it stays out of staff lists, notifications and webhooks.

See [docs/permissions.md](docs/permissions.md) and [docs/commands.md](docs/commands.md).

## The YouTuber role

The `youtuber` rank is a scoped side branch, not staff. It can:

* `vsm move <x> <y> <z> [world]` — move yourself, and optionally one eligible player
* `vsm freeze <player>` / `vsm unfreeze <player>` / `vsm unfreeze all`
* `vsm tp <player>` / `vsm tp <x> <y> <z>`
* `vsm gamemode survival|adventure|creative`, `vsm gm toggle`, `vsm gms|gma|gmc|gmt`

Every action is limited and audited. Eligible targets are ordinary players only — never staff, never
another YouTuber, never yourself, never `vsm.freeze.exempt` holders, and never vanished staff (who also
disappear from tab-completion). Freezes auto-release, are capped per YouTuber and are released when the
YouTuber leaves; they create no punishment records. Creative mode uses a **separate inventory** that is
swapped in and out and written to disk, so items taken in creative can never reach survival and a crash
cannot lose the state. If a holder also has the matching full staff node, the staff path wins and the
scoped limits do not apply.

## Storage

| Type | Use |
|---|---|
| `YAML` | Small servers. Everything in memory, written back atomically. Warns above ~5,000 records. |
| `SQLITE` | Default. Single file, WAL enabled, no setup. |
| `MYSQL` | Multi-server or large networks, with a HikariCP pool. |

All storage I/O is asynchronous with an in-memory cache; chat, pre-login and tab-completion handlers only
ever read the cache, so a slow database can never delay a login or a message.

## Documentation

| Document | Contents |
|---|---|
| [docs/commands.md](docs/commands.md) | Every command, its permission and its behaviour |
| [docs/permissions.md](docs/permissions.md) | The full node tree and the rank model |
| [docs/config.md](docs/config.md) | Every configuration key |
| [docs/ip-bans.md](docs/ip-bans.md) | IP bans, CIDR ranges, allowlists and the proxy warning |
| [docs/privacy.md](docs/privacy.md) | IP handling, retention, hashing and the offline-mode warning |
| [docs/templates.md](docs/templates.md) | Templates, ladders and warning thresholds |
| [docs/api.md](docs/api.md) | The public API and custom events |
| [docs/migration.md](docs/migration.md) | Importing from LiteBans, AdvancedBan and vanilla |

## Building from source

The repository builds entirely in GitHub Actions — nothing is built on a developer machine.

```bash
./gradlew build     # compile + unit tests
```

* `build.yml` compiles, runs the unit tests and uploads the jar on every push and pull request.
* `release.yml` runs on a `v*` tag: it builds, downloads a real Paper 26.2 server, boots it with the
  plugin, runs console commands against it, asserts a clean startup, and only then publishes the GitHub
  release. A smoke-test failure means no release is created.

## Project layout

```
src/main/java/io/github/aethelreborn/vsm
├── VisualStudioManagement.java   main class, owns every service
├── VsmBootstrap.java             Paper bootstrapper (Brigadier command registration)
├── VsmLoader.java                Paper loader (library resolution)
├── api/                          public API and custom events
├── command/                      the /vsm Brigadier tree, one module per feature group
├── listeners/                    login, chat, command, movement, inventory, damage, ping
├── services/                     the choke point and every feature service
├── storage/                      StorageProvider, YAML, SQL, migrations, caches
├── gui/                          menu framework and the menus
├── integration/                  PlaceholderAPI, Discord, importers
├── models/                       Punishment, StaffMember, Report, AuditEntry, ...
└── util/                         TimeParser, CidrMatcher, IpUtil, Json, scheduler, ...
```

## Licence

Provided as-is for your own server. No warranty.