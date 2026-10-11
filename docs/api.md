# Public API

The API is stable and routed through `ActionService`, so API callers get exactly the same permission,
outranking, exemption, limit, audit, event and webhook behaviour as staff.

```java
VsmAPI vsm = (VsmAPI) Bukkit.getPluginManager().getPlugin("VisualStudioManagement");
```

## Reading

```java
boolean banned = vsm.isBanned(uuid);
boolean muted  = vsm.isMuted(uuid);
boolean warned = vsm.isWarned(uuid);
boolean frozen = vsm.isFrozen(uuid);

List<Punishment> active = vsm.getActivePunishments(uuid);
List<Punishment> mutes  = vsm.getActivePunishments(uuid, PunishmentType.TEMP_MUTE);

String rank     = vsm.getStaffRank(uuid);
boolean staff   = vsm.isStaff(uuid);
StaffMember me  = vsm.getStaffMember(uuid);
```

`getActivePunishments` already evaluates expiry, so a player is never reported as punished past their
expiry.

## Writing

```java
ActionResult result = vsm.issue(staff, target.getUniqueId(), target.getName(),
        PunishmentType.TEMP_MUTE, "Harassment", TimeParser.parse("30m"), true);

if (!result.isSuccess()) {
    switch (result.status()) {
        case DENIED_PERMISSION -> staff.sendMessage("you cannot do that");
        case DENIED_OUTRANK    -> staff.sendMessage("outranked");
        case DENIED_LIMIT      -> staff.sendMessage("duration too long");
        default                 -> staff.sendMessage("failed");
    }
}

vsm.remove(staff, "VP-8F3K2Q", "appeal accepted");
```

`issue(...)` is checked against the same node the command would use, so `TEMP_MUTE` needs `vsm.tempmute`.
Passing `true` for `silent` requires `vsm.punishment.silent`, exactly as the command does.

## Events

| Event | Cancellable | Fired when |
|---|---|---|
| `PunishmentIssuedEvent` | yes | A punishment was created. Cancelling removes it again. |
| `PunishmentRemovedEvent` | no | A punishment was lifted or cascaded. |
| `PunishmentExpiredEvent` | no | A temporary punishment reached its expiry. |
| `StaffRankChangeEvent` | yes | Before a rank change. Cancelling keeps the old rank. |
| `ReportCreatedEvent` | no | A report was stored. |
| `StaffActionEvent` | no | Any action routed through `ActionService`. |

```java
@EventHandler
public void onIssue(PunishmentIssuedEvent event) {
    if (event.getPunishment().type().isBan()) {
        logger.info("ban issued by " + event.getStaffName());
    }
}
```

All of them live in `io.github.aethelreborn.vsm.api.events`.
