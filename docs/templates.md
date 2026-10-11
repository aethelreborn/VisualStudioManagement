# Templates, ladders and warning thresholds

## Templates

`templates.yml` gives consistent punishments and lets staff type `/vsm punish Steve spam` instead of
guessing durations.

```yaml
templates:
  spam:
    display: "Chat spam"
    reason: "Spamming chat"
    ladder:
      - { type: WARN }
      - { type: TEMP_MUTE, duration: 30m }
      - { type: TEMP_MUTE, duration: 6h }
      - { type: TEMP_MUTE, duration: 3d }
```

| Key | Meaning |
|---|---|
| `display` | Shown to staff in messages and the GUI. |
| `reason` | The reason recorded on the punishment. |
| `ladder` | Ordered steps. |
| `ladder[].type` | Any punishment type name, e.g. `TEMP_BAN`, `IP_BAN`. |
| `ladder[].duration` | Any duration the parser accepts; omit for a permanent step. |
| `ladder[].flags` | `evidence-required`, `also-ban-accounts`. |

## How a step is chosen

The step is picked by **how many punishments the target already has from this template**. Zero prior
punishments gives step 0, one gives step 1, and so on. Once the ladder is exhausted the **last** step
repeats, so a repeat offender keeps escalating within the top step.

## Invalid templates

Templates are validated on load. A template with an unknown type, a missing ladder or an unparsable
duration is **disabled** and the problem is logged; the rest of the file still works. The plugin warns on
every punishment command until the file is fixed, so staff are never silently handed the wrong punishment.

## Warning thresholds

```yaml
warning-thresholds:
  3: { type: TEMP_MUTE, duration: 1h,  reason: "3 warnings reached" }
  5: { type: TEMP_BAN,  duration: 1d,  reason: "5 warnings reached" }
  8: { type: TEMP_BAN,  duration: 7d,  reason: "8 warnings reached" }
```

When a warning is issued, the highest threshold that the target has reached fires automatically as an
auto-moderation punishment with `AUTOMOD` as the actor and a distinct audit source. That makes the
escalation visible and attributable.

## Quick reasons

`reasons.yml` is a flat list used for tab-completion and the punish GUI. It has no effect on logic — it
just saves typing.

```
reasons:
  - "Spamming chat"
  - "Advertising"
  - "Use of unfair modifications"
```
