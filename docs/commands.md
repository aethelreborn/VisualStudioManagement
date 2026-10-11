# Commands

Every feature is reachable under the `/vsm` root. With `commands.standalone-aliases: true` (the default)
each command is also registered under its historical name, so `/ban` and `/vsm ban` are the same command.

Sub-commands only appear in tab-completion when the sender holds the matching node, so a `youtuber` sees
only `move`, `freeze`, `unfreeze`, `tp`, `gamemode` and the `gm*` shortcuts.

## Universal punishment flags

Flags may appear in any order before the reason:

| Flag | Meaning |
|---|---|
| `-s` | Silent: no public broadcast, staff are still notified. Needs `vsm.punishment.silent`. |
| `-p` | Force the public broadcast even when the type is configured to stay quiet. |
| `-e <url>` | Attach evidence to the record. |
| `-t <id>` | Apply a template from `templates.yml`. |
| `-m` | Modify the matching active punishment instead of creating a new one. |

A reason is **required** for every punishment (`punishments.require-reason`,
`punishments.min-reason-length`).

Durations accept `s`, `m`, `h`, `d`, `w`, `mo`, `y` and combined forms such as `1d12h`. `perm`,
`permanent` and `forever` select the permanent variant. `0`, negatives, decimals, unknown units and values
above your rank cap are refused with a clear message.

## Punishments

| Command | Node | Behaviour |
|---|---|---|
| `/vsm warn <player> <reason>` | `vsm.warn` | Adds a warning. Can trigger ladder escalation. |
| `/vsm tempwarn <player> <duration> <reason>` | `vsm.warn` | Warning that stops counting after the duration. |
| `/vsm unwarn <player\|id>` | `vsm.unwarn` | Removes a warning. |
| `/vsm warnings [player]` | `vsm.warnings` | Lists warnings; players may view their own. |
| `/vsm mute <player> <reason>` | `vsm.mute` | Permanent mute, rank capped. |
| `/vsm tempmute <player> <duration> <reason>` | `vsm.tempmute` | Temporary mute. |
| `/vsm shadowmute <player> <reason>` | `vsm.shadowmute` | Only the player sees their own message; staff with `vsm.shadowmute.see` get a tagged copy. |
| `/vsm ipmute <player\|ip> <duration> <reason>` | `vsm.ipmute` | Mutes the address and every account on it. |
| `/vsm unmute <player\|id>` | `vsm.unmute` | Removes an active mute. |
| `/vsm kick <player> <reason>` | `vsm.kick` | Disconnects with the themed screen. |
| `/vsm kickall [reason]` | `vsm.kick` | Kicks everyone without `vsm.exempt.kick`. |
| `/vsm tempban <player> <duration> <reason>` | `vsm.tempban` | Account ban until expiry. |
| `/vsm ban <player> <reason>` | `vsm.ban` | Permanent account ban, admin+ by default. |
| `/vsm unban <player\|id>` | `vsm.unban` | Removes an active account ban. |
| `/vsm ipban <player\|ip> <reason>` | `vsm.ipban` | Permanent IP ban. Resolves a player name to their last known address. |
| `/vsm ipban allow add\|remove <player>` | `vsm.ipban` | IP allowlist exception. |
| `/vsm tempipban <player\|ip> <duration> <reason>` | `vsm.tempipban` | Temporary IP ban. |
| `/vsm ipunban <ip\|player\|id>` | `vsm.ipunban` | Removes an active IP ban and every account ban linked to it. |
| `/vsm nameban <name> <reason>` | `vsm.nameban` | Bans a username that has never joined. |
| `/vsm punish <player> <template>` | `vsm.punish` | Applies the matching ladder step. |
| `/vsm pardon <id> [reason]` | `vsm.punishment.remove` | Removes any punishment by id. |
| `/vsm punishment info <id>` | `vsm.history` | Full details. |
| `/vsm punishment modify <id> duration\|reason <value>` | `vsm.punishment.modify` | Edits an active punishment in place. |
| `/vsm checkban <player\|ip>` | `vsm.checkban` | Ban status. For an address it also states whether this account may still join. |
| `/vsm checkmute <player>` | `vsm.checkmute` | Mute status. |
| `/vsm banlist [page]` | `vsm.banlist` | Paginated active bans. |
| `/vsm mutelist [page]` | `vsm.mutelist` | Paginated active mutes. |

## Staff management

| Command | Node |
|---|---|
| `/staff` | `vsm.use` — opens the staff menu |
| `/staff add <player> <rank>` | `vsm.staff.manage` |
| `/staff remove <player>` | `vsm.staff.manage` |
| `/staff setrank <player> <rank>` | `vsm.staff.manage` |
| `/staff promote <player>` / `/staff demote <player>` | `vsm.staff.manage` |
| `/staff list` | `vsm.staff.list` |
| `/staff info <player>` | `vsm.staff.info` |
| `/staff ranks` | `vsm.use` |
| `/staff pin set <player> <pin>` / `/staff pin remove <player>` | `vsm.staff.manage` |

## History, notes and lookups

| Command | Node |
|---|---|
| `/vsm history <player> [page] [--type ban\|mute\|warn\|kick]` | `vsm.history` |
| `/vsm staffhistory <staff>` | `vsm.history.others` |
| `/vsm mutes\|bans\|warns <player>` | `vsm.history` |
| `/vsm note add <player> <text>` | `vsm.notes.add` |
| `/vsm note remove <id>` | `vsm.notes.remove` |
| `/vsm note list <player>` | `vsm.notes.view` |
| `/vsm alts <player>` | `vsm.alts` |
| `/vsm iphistory <player>` | `vsm.iphistory` |
| `/vsm namehistory <player>` | `vsm.lookup` |
| `/vsm lookup <player>` | `vsm.lookup` |
| `/vsm seen <player>` | `vsm.seen` |
| `/vsm near [radius]` | `vsm.seen` |

## Movement

| Command | Node | Behaviour |
|---|---|---|
| `/vsm tp <player>` | `vsm.tp` | Teleport to a player; silent while vanished. |
| `/vsm tp <player> <destination>` | `vsm.tp.others` | Teleport one player to another. |
| `/vsm tphere <player>` | `vsm.tphere` | Pull a player to you. |
| `/vsm tpall [confirm]` | `vsm.tpall` | Teleport everyone to you. |
| `/vsm tppos <x> <y> <z> [world]` | `vsm.tppos` | Coordinates, `~` supported. |
| `/vsm back` | `vsm.back` | Return to the previous location. |
| `/vsm top` | `vsm.top` | Highest safe block above you. |
| `/vsm tpoffline <player>` | `vsm.tpoffline` | Teleport to an offline player's logout location. |
| `/vsm fly [on\|off] [player]` | `vsm.fly` | Flight, restored on quit. |
| `/vsm speed <1-10> [player]` | `vsm.speed` | Movement speed, capped per rank. |
| `/vsm gm <mode> [player]` | `vsm.gamemode` | Game mode; staff mode restores the original on exit. |
| `/vsm spectate <player>` / `/vsm spectate stop` | `vsm.spectate` | Spectate a player. |
| `/vsm follow <player>` / `/vsm follow stop` | `vsm.follow` | Auto-follow. |
| `/vsm staffmode [restore]` | `vsm.staffmode` | Staff Mode. |

## Investigation and chat control

| Command | Node |
|---|---|
| `/vsm freeze <player> [reason]` | `vsm.freeze` |
| `/vsm unfreeze <player>` / `/vsm unfreeze all` | `vsm.freeze` |
| `/vsm vanish [on\|off\|see]` | `vsm.vanish` |
| `/vsm invsee <player>` | `vsm.invsee` |
| `/vsm endersee <player>` | `vsm.endersee` |
| `/vsm commandspy [on\|off]` | `vsm.commandspy` |
| `/vsm socialspy [on\|off]` | `vsm.socialspy` |
| `/vsm watch add <player> <reason>` / `remove` / `list` | `vsm.watch` |
| `/vsm clearchat` | `vsm.clearchat` |
| `/vsm mutechat [on\|off]` | `vsm.mutechat` |
| `/vsm slowmode <seconds\|off>` | `vsm.slowmode` |
| `/vsm lockdown [on\|off]` | `vsm.lockdown` |
| `/vsm maintenance [on\|off\|whitelist add\|remove]` | `vsm.maintenance` |
| `/vsm alerts [on\|off]` | `vsm.alerts` |
| `/vsm staffauth <pin>` | none — required when `security.staff-pin` is on |

## Reports and chat

| Command | Node |
|---|---|
| `/vsm report <player> <reason>` | `vsm.report` (default **true**) |
| `/vsm reports [open\|list]` | `vsm.reports.view` |
| `/vsm reports claim <id>` | `vsm.reports.claim` |
| `/vsm reports assign <id> <staff>` | `vsm.reports.assign` |
| `/vsm reports close <id> <note>` | `vsm.reports.close` |
| `/vsm reports teleport <id>` | `vsm.reports.teleport` |
| `/vsm helpop <message>` | `vsm.helpop` (default **true**) |
| `/vsm sc [message]` | `vsm.staffchat` |
| `/vsm ac <message>` | `vsm.adminchat` |

## YouTuber toolkit

All of these run through `ActionService` and carry the scoped limits from the `youtuber:` config block.

| Command | Node |
|---|---|
| `/vsm move <x> <y> <z> [world]` | `vsm.youtuber.move` |
| `/vsm move <player> <x> <y> <z> [world]` | `vsm.youtuber.move.others` |
| `/vsm freeze <player> [reason]` | `vsm.youtuber.freeze` |
| `/vsm unfreeze <player>` / `/vsm unfreeze all` | `vsm.youtuber.unfreeze` |
| `/vsm tp <player>` / `/vsm tp <x> <y> <z>` | `vsm.youtuber.tp` |
| `/vsm gamemode survival\|adventure\|creative` | `vsm.youtuber.gamemode.<mode>` |
| `/vsm gm toggle` | `vsm.youtuber.gamemode.toggle` |
| `/vsm gms` / `/vsm gma` / `/vsm gmc` / `/vsm gmt` | matching node |

A YouTuber cannot act on staff, on another YouTuber, on themselves, on `vsm.freeze.exempt` holders or on
vanished staff — and vanished staff never appear in tab-completion. YouTuber freezes carry an owner tag, so
`/vsm unfreeze` only releases freezes that the same YouTuber applied.

## Administration

| Command | Node |
|---|---|
| `/vsm` | any `vsm.*` node — about and health |
| `/vsm reload` | `vsm.reload` |
| `/vsm debug` | `vsm.debug` |
| `/vsm import <litebans\|advancedban\|vanilla> [--dry-run]` | `vsm.import` |
| `/vsm export <punishments\|audit>` | `vsm.export` |
| `/vsm purge <player>` | `vsm.purge` |
| `/vsm backup` | `vsm.purge` |
| `/vsm audit [page]` | `vsm.audit.view` |
| `/vsm audit verify` | `vsm.audit.view` — recomputes the hash chain |
| `/vsm audit export` | `vsm.audit.export` |
| `/vsm playerinfo <player>` | `vsm.lookup` — opens the player info menu |