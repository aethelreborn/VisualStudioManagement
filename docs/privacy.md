# Privacy and IP handling

## What is stored

| Data | Where | Notes |
|---|---|---|
| Login addresses | `ip_history` | One row per account and address, with first/last seen and a counter. |
| IP punishments | `punishments.target_ip` | Only for IP bans and IP mutes. |
| Punishments | `punishments` | Reason, staff, timestamps, optional evidence URL. |
| Staff notes | `notes` | Free text written by staff. |
| Audit lines | `audit_logs` + `logs/audit-YYYY-MM-DD.log` | Who did what, when, to whom. |

## Display

* Addresses are **masked** (`203.0.113.x`) for staff without `vsm.ip.view`.
* `vsm.ip.view` (admin+ by default) reveals the full address.
* Raw addresses are never written to the console, the audit log or a Discord webhook unless
  `privacy.log-raw-ips: true`.
* IP ban screens never reveal the address, not even to the banned player.

## Retention

| Setting | Effect |
|---|---|
| `privacy.ip-history-retention-days: 180` | Rows older than this are pruned automatically. |
| `audit.retention-days: 365` | Audit log files older than this are gzipped. |
| `/vsm purge <player>` | Deletes a player's personal data: punishments, IP history, allowlist entry, notes, reports, saved locations and watchlist entry. |

`purge` needs `vsm.purge` (owner) and is audited itself.

## Hashed mode

`privacy.hash-ips: true` stores `HMAC-SHA256(ip, serverSecret)` instead of the raw address.

* Exact-match enforcement still works: a ban created in hashed mode matches a login hashed the same way.
* **CIDR range bans stop working**, because a prefix cannot be computed from a hash.
* Unmasked display stops working.
* Set the `VSM_SERVER_SECRET` environment variable so the secret is stable across restarts and machines.
  Without it the secret is derived from `settings.server-name` and is therefore predictable — fine for a
  single small server, not for anything serious.

## Offline-mode warning

When `online-mode=false`, player identity is derived from the name, so anyone can claim any name. The
plugin prints a prominent startup warning, because staff identity is spoofable in that mode. Two
mitigations are built in:

* `security.staff-pin` — staff must run `/vsm staffauth <pin>` after joining before staff commands work.
  PINs are stored as salted SHA-256 digests, never in plaintext, and repeated failures suspend the account
  and alert admins.
* `security.staff-ip-lock` — the first address is recorded and logins from a new one need approval.

## Sanitisation

* Report and note text is stripped of control characters before storage and display.
* Discord mentions (`@everyone`, `@here`, role ids) are stripped before anything leaves the server.
* Player-supplied text is inserted into MiniMessage with `Placeholder.unparsed`, so a reason containing
  `<click:run_command:...>` renders as literal text and can never execute a command.
* Arguments are validated before they reach storage: control characters, over-long reasons, invalid
  UUIDs, invalid addresses and invalid durations are all refused.

## Rate limiting and abuse prevention

`security.rate-limits` caps bans, mutes and kicks per staff member per window. Exceeding a limit suspends
that staff member's punishment powers for ten minutes, writes a `RATE_LIMIT_HIT` audit entry and alerts
admins.
