# Permissions and ranks

## The model in one paragraph

Staff authority comes **only** from the plugin's own whitelist, stored by UUID. Operator status grants
nothing (`settings.op-bypass` is `false`). A rank inherits every permission of its parent, the flattened
set is computed once at load, and an actor may only act on a **strictly lower** weight.

## Ranks

| Rank | Weight | Parent | Staff? |
|---|---|---|---|
| `player` | 0 | — | no |
| `youtuber` | 15 | `player` | no (side branch) |
| `trainee` | 10 | `player` | yes |
| `helper` | 20 | `player` | yes |
| `moderator` | 40 | `helper` | yes |
| `senior_mod` | 60 | `moderator` | yes |
| `admin` | 80 | `senior_mod` | yes |
| `owner` | 100 | `admin` | yes |

Ranks are **data**, not code. Edit `ranks.yml` to add, remove or reweight them; each entry takes
`display`, `weight`, `parent`, `prefix`, `staff` and a `permissions` list. Save with `/vsm reload`.

### The `youtuber` side branch

`youtuber` sits above `player` and below `helper` by weight, but it is **not a step on the ladder**. It
inherits from `player` only, never from helper or above, and promoting a YouTuber to `helper` replaces the
role rather than stacking it. `staff: false` keeps it out of staff lists, notifications and webhooks.

### Rules that cannot be bypassed

* Nobody may promote to a rank at or above their own weight. Only `owner` may create or edit ranks, or
  grant `owner`.
* Nobody may change their own rank (`staff.allow-self-rank-change: false`).
* The last remaining `owner` cannot be removed or demoted, which prevents lock-out.
* Equal ranks cannot act on each other.
* Console is treated as `owner` for permission purposes but is still audited.

## Nodes

### Root and chat

```
vsm.use
vsm.staffchat              vsm.staffchat.toggle
vsm.adminchat
vsm.alerts
```

### Punishments

```
vsm.warn                   vsm.unwarn                 vsm.warnings
vsm.mute                   vsm.tempmute               vsm.unmute
vsm.shadowmute             vsm.shadowmute.see
vsm.ipmute
vsm.kick
vsm.tempban                vsm.ban                    vsm.unban
vsm.ipban                  vsm.ipban.range            vsm.ipunban
vsm.tempipban              vsm.nameban
vsm.punish                 vsm.punishment.modify      vsm.punishment.remove
vsm.punishment.silent
vsm.punishment.unlimited.<type>
vsm.checkban               vsm.checkmute
vsm.banlist                vsm.mutelist
```

### History and info

```
vsm.history                vsm.history.others
vsm.notes.view             vsm.notes.add              vsm.notes.remove
vsm.alts                   vsm.iphistory
vsm.ip.view
vsm.lookup                 vsm.seen
```

### Reports and help

```
vsm.report                 vsm.helpop
vsm.reports.view           vsm.reports.claim          vsm.reports.close
vsm.reports.teleport       vsm.reports.assign
```

### Movement

```
vsm.tp                     vsm.tp.others
vsm.tphere                 vsm.tpall                  vsm.tppos
vsm.back                   vsm.top                    vsm.tpoffline
vsm.fly                    vsm.speed                  vsm.gamemode
vsm.spectate               vsm.follow                 vsm.staffmode
```

### Investigation and control

```
vsm.freeze                 vsm.freeze.exempt
vsm.vanish                 vsm.vanish.see             vsm.vanish.interact
vsm.invsee                 vsm.invsee.edit            vsm.endersee
vsm.commandspy             vsm.socialspy              vsm.watch
vsm.clearchat              vsm.mutechat               vsm.slowmode
vsm.chat.bypass
vsm.lockdown               vsm.maintenance            vsm.maintenance.bypass
```

### Management

```
vsm.staff.manage           vsm.staff.list             vsm.staff.info
vsm.staff.ranks
vsm.reload                 vsm.import                 vsm.export
vsm.purge                  vsm.debug
vsm.audit.view             vsm.audit.export
vsm.override
```

### YouTuber role

```
vsm.youtuber
vsm.youtuber.freeze                 vsm.youtuber.unfreeze
vsm.youtuber.move                   vsm.youtuber.move.others
vsm.youtuber.tp
vsm.youtuber.gamemode.survival      vsm.youtuber.gamemode.adventure
vsm.youtuber.gamemode.creative      vsm.youtuber.gamemode.toggle
vsm.youtuber.creative.worlds.bypass
```

Every `vsm.youtuber.*` node is **scoped**: `ActionService` applies extra limits (eligible targets,
distances, cooldowns, concurrent freezes, creative restrictions). A holder of the matching full staff node
(for example `vsm.freeze`) is not subject to those limits — the staff path wins.

### Exemptions

```
vsm.exempt.ban             vsm.exempt.mute
vsm.exempt.kick            vsm.exempt.warn
vsm.exempt.ip              vsm.exempt.automod
```

`vsm.exempt.ip` additionally covers every IP punishment, so a holder cannot be IP-banned either.
`vsm.override` (owner only) bypasses outranking and exemption checks entirely.

### Defaults

Only `vsm.report` and `vsm.helpop` default to **true**; everything else defaults to **false**. Group nodes
mirror the ranks:

```
vsm.* → vsm.owner → vsm.admin → vsm.senior → vsm.moderator → vsm.helper
vsm.youtuber   (side branch, not a child of any staff rank)
```

`permissions.yml` mirrors this tree as reference; the authoritative copy lives in
`PermissionNodes.java` and is registered with Bukkit at startup, so other permission plugins can grant
single nodes if you want them to.

## Permission modes

`settings.permission-mode`:

| Mode | Behaviour |
|---|---|
| `INTERNAL` | Only whitelist grants count. Bukkit-granted nodes are ignored, so only whitelisted staff can use the plugin. A warning is logged at startup if a non-whitelisted account holds `vsm.*`. |
| `BUKKIT` | Only Bukkit permissions count. |
| `BOTH` *(default)* | Either one grants the node. |

## Defaults in `ranks.yml`

The shipped ranks.yml already grants each rank the nodes from the specification table (helper can warn and
report, moderator can mute and kick, senior_mod can IP-ban temporarily and modify punishments, admin can
ban and manage staff, owner can do everything). Adjust freely — the file is the source of truth.