# IP bans

## The commands

```
/vsm ipban Steve Ban evasion          # resolves Steve's most recent login address
/vsm ipban 203.0.113.45 Griefing      # a literal address
/vsm ipban 203.0.113.0/24 Abuse       # a CIDR range, admin+ and vsm.ipban.range
/vsm tempipban Steve 7d Evasion       # temporary
/vsm ipban allow add Steve            # allowlist exception
/vsm ipban allow remove Steve
/vsm ipunban 203.0.113.45             # by address
/vsm ipunban VP-8F3K2Q                # by punishment id
/vsm ipunban Steve                    # by their last known address
/vsm checkban 203.0.113.45
```

## Behaviour

1. **Resolution.** `/vsm ipban <player>` uses the most recent login address on record. If the player has
   never joined the plugin fails with a clear message instead of guessing.
2. **Account cascade.** With `punishments.also-ban-accounts-on-ipban: true` (the default) the plugin also
   creates account bans for every account seen on that address within
   `punishments.ip-history-window-days`, all linked to one **parent punishment id**. A single
   `/vsm ipunban` therefore reverses the whole set.
3. **CIDR ranges.** `punishments.ipban.allow-ranges` enables them; `vsm.ipban.range` gates who may use
   them. `max-range-prefix-v4: 24` and `max-range-prefix-v6: 64` refuse anything broader, so a `/8` is
   rejected while a `/24` is accepted.
4. **Safety guards.** The plugin refuses:
   * loopback and private ranges (`127.0.0.0/8`, `10/8`, `172.16/12`, `192.168/16`, `::1`) unless
     `punishments.ipban.allow-private: true`;
   * the actor's own address;
   * any address belonging to a player holding `vsm.exempt.ip`, unless the actor holds `vsm.override`.
5. **Allowlist.** `/vsm ipban allow add <player>` lets a specific UUID join despite an IP ban — useful for
   shared households and schools. The UUID is stored in `ip_allow` and survives restarts.
6. **Enforcement** runs at `AsyncPlayerPreLoginEvent`, and a new IP ban also kicks every already-connected
   player whose address matches.
7. **Display.** Addresses are masked (`203.0.113.x`) for staff without `vsm.ip.view`, and IP-ban screens
   never reveal the address at all. Raw addresses are never sent to Discord.

## Enforcement order at login

1. Maintenance mode / lockdown.
2. Active **account ban** by UUID.
3. Active **IP ban** by exact match, then CIDR ranges, unless the UUID is allowlisted.
4. Active **name ban**.

Every step reads only the in-memory cache, so a slow database can never delay or break a login.

## ⚠ Running behind a proxy

If your server sits behind Velocity, BungeeCord or a CDN, the address the plugin sees is the **proxy's**
unless the proxy forwards the real one.

* **Velocity** forwards the client address automatically.
* **BungeeCord / waterfall** need `proxy_protocol: true` or a forwarding plugin, otherwise *every* player
  appears to share the proxy's address and one IP ban locks out the whole server.
* A CDN in front of the server hides the real address entirely.

Always verify what the plugin sees before issuing an IP ban:

```
/vsm iphistory <player>
```

If every entry is identical, forwarding is not configured. The startup log prints a warning when the
server runs in offline mode for the same class of reason.

With `punishments.ipban.allow-private: true` (needed for proxy setups, and for local testing) private
ranges are accepted, but the same care still applies: the guard exists to stop accidents, not to work
around a misconfigured network.
