# Changelog

All notable changes are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[semantic versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] — 2026-10-11

### Added

- Paper plugin (`paper-plugin.yml`) with a bootstrapper and a library-resolving loader; the full
  Brigadier command tree is registered through `LifecycleEvents.COMMANDS`.
- Rank system driven by `ranks.yml` with inheritance flattened at load, strict outranking, rank-change
  guards and a last-owner lock-out guard.
- `ActionService` as the single choke point for every staff action, covering permission checks,
  outranking, exemptions, per-rank duration caps, rate limits, auditing, events, broadcasts and webhooks.
- Punishments: warn, tempwarn, mute, tempmute, shadowmute, ipmute, kick, ban, tempban, IP ban, temporary
  IP ban, name ban, with silent / evidence flags, modify-in-place, pardon and cascading account bans.
- IP layer: login history, alt detection, CIDR ranges with prefix guards, an IP allowlist, privacy masking
  and optional HMAC hashing.
- Storage backends: YAML, SQLite and MySQL with versioned migrations and in-memory caches; all I/O is
  asynchronous and never touches the main thread.
- Listeners for pre-login enforcement, join notices, quit cleanup, chat, command filtering, freeze,
  teleport, damage, inventory and server-list ping.
- The `/vsm` root command with every feature reachable underneath, plus standalone aliases.
- YouTuber side-branch rank with scoped freeze, move, teleport and game-mode powers, owner-tagged freezes,
  concurrent and duration limits, cooldowns and separate creative inventories.
- GUI framework plus the staff, report, punish, history and player-info menus.
- Public API (`VsmAPI`) and six custom events.
- Optional PlaceholderAPI expansion, Discord webhooks, and LiteBans / AdvancedBan / vanilla importers.
- Tamper-evident audit log with `/vsm audit verify`.
- 94 unit tests and a GitHub Actions pipeline that builds, unit-tests, boots a real Paper 26.2 server with
  the plugin and only then publishes a release.

### Fixed during bring-up

- The PlaceholderAPI expansion is loaded through a nested type so the plugin remains loadable on servers
  without PlaceholderAPI installed.
- Side-branch ranks are no longer treated as ladder steps by `/staff promote` and `/staff demote`.
- A template with an unparsable duration is disabled instead of silently escalating to a permanent
  punishment.
- Warning thresholds resolve to the highest unmet step rather than the lowest.
