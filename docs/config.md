# Configuration reference

Every key in `config.yml`, with its default. The file is copied into
`plugins/VisualStudioManagement/` on first start; delete it to regenerate the defaults.

## Prefix and general

| Key | Default | Notes |
|---|---|---|
| `prefix` | `<dark_gray>[<aqua>VSM</aqua>]</dark_gray> ` | MiniMessage. |
| `settings.language` | `en` | Reserved for message packs. |
| `settings.legacy-color-codes` | `false` | When true, `&a`-style codes in messages are translated to MiniMessage. |
| `settings.permission-mode` | `BOTH` | `INTERNAL` \| `BUKKIT` \| `BOTH`. |
| `settings.op-bypass` | `false` | Operator status grants nothing unless true. |
| `settings.server-name` | `main` | Used for per-server scopes. |
| `settings.appeal-url` | `discord.gg/yourserver` | Shown on ban screens. |
| `settings.check-for-updates` | `true` | |
| `settings.metrics` | `false` | bStats opt-in. |

## Storage

| Key | Default | Notes |
|---|---|---|
| `storage.type` | `SQLITE` | `YAML` \| `SQLITE` \| `MYSQL`. |
| `storage.sqlite.file` | `database.db` | Relative to the plugin folder. |
| `storage.mysql.*` | | host, port, database, user, password, use-ssl and the HikariCP pool sizes. |
| `storage.expiry-check-interval-seconds` | `30` | How often expired punishments are swept. |
| `storage.auto-prune-days` | `0` | `0` = never prune inactive records. |
| `storage.backups.*` | enabled, 24 h, keep 7 | |

## Staff

| Key | Default | Notes |
|---|---|---|
| `staff.default-rank` | `helper` | Used when a rank is unspecified. |
| `staff.allow-self-rank-change` | `false` | Nobody may change their own rank. |
| `staff.inactivity-alert-days` | `30` | Alerts admins about inactive staff. |

## Punishments

| Key | Default | Notes |
|---|---|---|
| `punishments.require-reason` | `true` | |
| `punishments.min-reason-length` | `3` | |
| `punishments.max-reason-length` | `256` | Also the sanitisation limit. |
| `punishments.id-prefix` | `VP` | Ids look like `VP-8F3K2Q`. |
| `punishments.id-numeric-mode` | `false` | Obscured numeric ids. |
| `punishments.broadcast.*` | ban/tempban/ipban true | Public broadcast per type. |
| `punishments.ban-ip-on-ban` | `false` | Also store the address on an account ban. |
| `punishments.also-ban-accounts-on-ipban` | `true` | Cascade account bans from an IP ban. |
| `punishments.ip-history-window-days` | `90` | Which accounts the cascade covers. |
| `punishments.ipban.allow-private` | `false` | Required for proxy setups. |
| `punishments.ipban.allow-ranges` | `true` | CIDR support. |
| `punishments.ipban.max-range-prefix-v4` | `24` | Refuses anything broader. |
| `punishments.ipban.max-range-prefix-v6` | `64` | |
| `punishments.blocked-commands-while-muted` | `/me`, `/say`, `/msg`, ... | |
| `punishments.max-durations.<rank>.<type>` | see §9.2 | `none` / `unlimited` means no cap. |
| `punishments.permanent-ban-min-rank` | `admin` | |
| `punishments.permanent-mute-min-rank` | `senior_mod` | |
| `punishments.ipban-min-rank` | `admin` | |
| `punishments.tempipban-min-rank` | `senior_mod` | |
| `punishments.exempt-permission-checks` | `true` | Honour `vsm.exempt.*`. |

`vsm.punishment.unlimited.<type>` bypasses the cap for a specific staff member.

## Reports

`max-open-reports-per-player: 3`, `cooldown-seconds: 60`, `notify-staff: true`,
`allow-reporting-staff: false`, `merge-duplicates-minutes: 10`, `escalate-threshold: 3`,
`escalate-window-minutes: 30`.

## Freeze

| Key | Default |
|---|---|
| `freeze.block-commands` | `true` |
| `freeze.allowed-commands` | `/msg`, `/reply`, `/report`, `/helpop` |
| `freeze.block-damage-dealt` | `true` |
| `freeze.punish-on-logout.enabled` | `true` |
| `freeze.punish-on-logout.template` | `freeze-logout` |
| `freeze.effects.title` | `true` |
| `freeze.effects.blindness` | `false` |

YouTuber freezes never trigger the logout punishment.

## Vanish

`hide-from-tab: true`, `suppress-join-leave: true`, `silent-containers: true`,
`block-item-pickup: true`, `auto-on-join: false`, `persist-across-relogs: true`.

## Teleport and staff mode

`teleport.back-history: 5`, `safe-teleport: true`, `world-blacklist: []`, `confirm-tpall: true`,
`cooldown-seconds: 0`.
`staffmode.save-to-disk: true`, `vanish: true`, `fly: true`, `invulnerable: true`, `allow-building: false`.

## Invsee

`invsee.offline-support: false`, `invsee.edit-requires-confirmation: true`.

## Commands

| Key | Default | Notes |
|---|---|---|
| `commands.root` | `vsm` | The root literal. |
| `commands.standalone-aliases` | `true` | Keep `/ban`, `/tp`, `/freeze` beside `/vsm <sub>`. |

## YouTuber

See [ip-bans.md](ip-bans.md) and the section in [permissions.md](permissions.md). Key ones:

| Key | Default |
|---|---|
| `youtuber.freeze.max-duration` | `10m` |
| `youtuber.freeze.max-concurrent` | `5` |
| `youtuber.freeze.cooldown-seconds` | `5` |
| `youtuber.freeze.release-when-actor-leaves` | `true` |
| `youtuber.move.allow-others` | `true` |
| `youtuber.move.max-distance` | `100` |
| `youtuber.move.allow-cross-world` | `false` |
| `youtuber.move.cooldown-seconds` | `3` |
| `youtuber.tp.cooldown-seconds` | `3` |
| `youtuber.tp.allow-to-staff` | `false` |
| `youtuber.gamemode.allowed` | `SURVIVAL, ADVENTURE, CREATIVE` |
| `youtuber.gamemode.creative-worlds` | `[]` (every world) |
| `youtuber.gamemode.separate-inventories` | `true` |
| `youtuber.gamemode.reset-on-quit` | `SURVIVAL` |
| `youtuber.gamemode.creative-restrictions.block-item-drop` | `true` |
| `youtuber.gamemode.creative-restrictions.block-pvp` | `true` |

## Lockdown and maintenance

`lockdown.allow-returning-players: true`, `maintenance.enabled: false`, `maintenance.whitelist: []`.

## Security

| Key | Default | Notes |
|---|---|---|
| `security.staff-pin.enabled` | `false` | Staff must run `/vsm staffauth <pin>`. |
| `security.staff-pin.max-attempts` | `3` | Lockout and admin alert. |
| `security.staff-ip-lock` | `false` | First address is remembered. |
| `security.session-minutes` | `0` | `0` disables the timeout. |
| `security.two-person-rule.*` | disabled | Second approval for `BAN`, `IP_BAN`, `PURGE`. |
| `security.rate-limits.ban` | 5 / 10 min | |
| `security.rate-limits.mute` | 15 / 10 min | |
| `security.rate-limits.kick` | 20 / 10 min | |

Exceeding a limit suspends that staff member's punishment powers for ten minutes and alerts admins.

## Privacy, alts, audit, webhooks

See [privacy.md](privacy.md).

| Key | Default |
|---|---|
| `privacy.hash-ips` | `false` |
| `privacy.log-raw-ips` | `false` |
| `privacy.ip-history-retention-days` | `180` |
| `privacy.mask-ips-for-staff` | `true` |
| `alts.alert-on-banned-link` | `true` |
| `alts.auto-action` | `ALERT` (`NONE` \| `ALERT` \| `KICK`) |
| `audit.retention-days` | `365` |
| `audit.console-major-actions` | `true` |
| `audit.tamper-evident` | `true` |
| `webhooks.discord.*` | disabled, empty url |

## Other files

| File | Purpose |
|---|---|
| `messages.yml` | Every user-visible string, MiniMessage. |
| `ranks.yml` | Rank definitions, weights, prefixes and permissions. |
| `templates.yml` | Punishment templates, ladders and warning thresholds. |
| `automod.yml` | Spam, caps, blocked words, advertising, escalation. |
| `reasons.yml` | Quick reasons for tab-completion and the GUI. |
| `permissions.yml` | Reference copy of the permission tree. |

A broken `config.yml` or `templates.yml` never crashes the server: `/vsm reload` keeps the last good
configuration and reports the parse error.