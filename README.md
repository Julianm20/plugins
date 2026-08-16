# Server Plugins

Three independent Paper plugins, three separate JARs. Nothing is shared between them —
each one can be dropped into `plugins/` on its own, and any of them can be moved to its
own repository later without touching build config.

| Plugin | JAR | What it does |
|---|---|---|
| ⚔️ [KillStreaks](#-killstreaks) | `KillStreaks-1.0.0.jar` | PvP kill streaks, milestones, configurable rewards, anti-farming |
| 👥 [HiddenTeams](#-hiddenteams) | `HiddenTeams-1.0.0.jar` | Private teams nobody outside the team can detect |
| 🌌 [EndScheduler](#-endscheduler) | `EndScheduler-1.0.0.jar` | Keeps The End locked until a scheduled opening |

Target: **Paper 1.21.x, Java 21**.

## Building

```bash
mvn clean package          # builds all three
mvn -pl killstreaks clean package   # or just one
```

JARs land in each module's `target/`. Copy them into your server's `plugins/` folder and
restart. Each plugin writes its own config on first start.

The root `pom.xml` is an aggregator only — the modules do **not** inherit from it, so
`cd killstreaks && mvn clean package` works standalone too.

### Paper API version

Each `pom.xml` has its own property:

```xml
<paper.api.version>1.21.4-R0.1-SNAPSHOT</paper.api.version>
```

Set this to match your server. A plugin built against 1.21.x runs on any later 1.21.x —
`api-version: '1.21'` in each `plugin.yml` covers the whole line. Building needs network
access to `https://repo.papermc.io`; that is the only external repository used.

### Message formatting

All three use [MiniMessage](https://docs.advntr.dev/minimessage/format.html) tags in
config — `<red>`, `<gold>`, `<bold>`, `<gradient:#a:#b>` — not legacy `&` codes.
Placeholders are `%wrapped%`. Any message set to `""` is skipped, so you can turn off
individual lines without touching code.

---

## ⚔️ KillStreaks

Kill a player, your streak goes up. Die, it resets.

**Commands**

| Command | Permission | Default |
|---|---|---|
| `/streak` | `killstreaks.use` | everyone |
| `/streak top` | `killstreaks.top` | everyone |
| `/streak <player>` | `killstreaks.others` | op |
| `/streakadmin set <player> <n>` | `killstreaks.admin` | op |
| `/streakadmin reset <player>` | `killstreaks.admin` | op |
| `/streakadmin resetall confirm` | `killstreaks.admin` | op |
| `/streakadmin reload` | `killstreaks.admin` | op |

**Milestones** are config entries keyed by the exact streak number. The defaults ship
with 3 / 5 / 10 / 15 / 20; add or remove as many as you like. Each one can broadcast,
message the killer, play a sound, and run console commands as rewards:

```yaml
milestones:
  '10':
    announce: true
    announcement: "<gray>[<gold>Streak<gray>] <red>%player% <yellow>is on a <red><bold>10<reset><yellow> kill streak!"
    killer-message: "<gold>Kill streak: <red>10<gold>."
    sound: "ui.toast.challenge_complete"
    commands:
      - "give %player% golden_apple 2"
      - "give %player% ender_pearl 4"
```

Rewards are commands rather than hardcoded items, so you can hand out anything your other
plugins understand. Placeholders: `%player%` / `%killer%`, `%victim%`, `%streak%`.

**Anti-farming.** Killing the same person repeatedly stops counting:

```yaml
anti-farm:
  enabled: true
  window-seconds: 600
  max-kills-per-victim: 2   # set to 1 to require a different player every time
```

The kill still counts for lifetime stats, it just earns no streak progress and no rewards.

**Other settings:** `disabled-worlds`, `reset-on-non-pvp-death` (set `false` so only PvP
deaths reset a streak), `reset-on-quit`, `announce-streak-ended-from`, `leaderboard-size`.

**Data** lives in `plugins/KillStreaks/data.yml` — current streak, best streak, kills and
deaths per UUID. Saved every 5 minutes, on shutdown, and written through a temp file so a
crash mid-write cannot truncate it.

---

## 👥 HiddenTeams

Teams that players outside them cannot detect. That constraint drove several design
decisions that look odd until you see what they prevent:

- **Team names are not unique.** If they were, `/team create Wolves` failing with "name
  taken" would confirm that a team called Wolves exists.
- **Invites don't reveal membership.** Inviting someone who is already in another team
  gives the byte-for-byte same response as a successful invite (`stealth-invites: true`),
  so nobody can use `/team invite` to probe who is teamed up. It does tell you when a
  player is offline — that is already public in the tab list.
- **Tab completion never leaks.** `/team invite <tab>` lists every online player rather
  than filtering out those already in teams, which would turn tab-complete into a team
  detector. `/team kick <tab>` only ever lists your own teammates.
- **No scoreboard teams.** The plugin deliberately does not touch Bukkit's scoreboard
  API, because that syncs prefixes and colours to every client. No `[TeamName]` anywhere,
  no nametag colours, no public list.

Friendly fire being off does not leak either: the only player who sees the cancelled hit
is the attacker, learning about their own team.

**Commands** — all under `hiddenteams.use` (everyone by default):

```
/team create <name>        /team list            /team leave
/team invite <player>      /team chat            /team kick <player>
/team accept [player]      /tc <message>         /team transfer <player>
/team deny [player]        /team rename <name>   /team disband confirm
```

`/team chat` toggles all your chat to team-only; `/tc <message>` sends one message
without toggling. Player text is spliced into the format as a component, never parsed as
markup, so nobody can inject colours or fake a chat line.

**Staff:** `/teamadmin list|info <player>|disband <player>|reload` behind
`hiddenteams.admin` (op). This is the only code path that can see a team you are not in.
Set `admin-commands: false` to remove it entirely. Team chat is **not** logged to console
by default (`log-team-chat: false`) — turn it on if you want moderation records.

**Key settings**

```yaml
max-team-size: 4          # owner included
friendly-fire: false
block-teammate-projectiles: true
invite-expiry-seconds: 120
stealth-invites: true
invite-requires-owner: false
```

Owners cannot leave a team that still has members — they have to `/team transfer` or
`/team disband confirm` first, so a team is never left ownerless.

**Data** lives in `plugins/HiddenTeams/teams.yml`. Invites are memory-only and expire, so
an invite can never outlive the team it pointed at.

---

## 🌌 EndScheduler

The End stays sealed until a scheduled moment, then opens with an announcement.

```yaml
end:
  enabled: true
  open-time: "SUNDAY 18:00"
  timezone: "America/Los_Angeles"
```

`open-time` accepts either the weekday form above (resolved to the *next* Sunday at
18:00) or an exact date: `"2026-08-23 18:00"`.

**Restart safety.** The resolved opening instant is written to
`plugins/EndScheduler/state.yml` the first time the plugin starts, and re-used on every
restart afterwards. This matters more than it sounds: recomputing "next Sunday 18:00" on
each boot would mean a restart at 18:05 on Sunday silently pushes the opening a week out.
Two cases follow from storing it:

- Server crashes Saturday, comes back Sunday **before** the time → still locked, countdown
  resumes, warnings that were already missed do not fire late.
- Server crashes Saturday, comes back Sunday **after** the time → opens immediately on
  startup and logs it. It does not wait for next week.

Once open it stays open — the `opened` flag is latched in `state.yml`, so restarts never
re-lock it or re-announce.

To change the schedule, edit `open-time` and run `/endscheduler reload`. The new value no
longer matches what `state.yml` was resolved from, so it re-arms.

**While locked:** end portals are blocked (both `PlayerPortalEvent` and
`PlayerTeleportEvent` are handled — Bukkit gives portal events their own handler list, so
one without the other leaves a hole), players get a message with the remaining time, and
anyone who logs in inside The End is moved to the overworld spawn
(`evict-players-on-join`). Staff with `endscheduler.bypass` (op) can always get in.

**Countdown** warnings default to 60/30/10/5/1 minutes plus a 10-second countdown; empty
lists disable them.

**Commands**

| Command | Permission | Default |
|---|---|---|
| `/endstatus` | `endscheduler.status` | everyone |
| `/endscheduler status\|open\|lock\|reload` | `endscheduler.admin` | op |

`open` forces it open now with the full announcement; `lock` re-locks and re-arms from
config, moving anyone currently in The End back out.

If `open-time` is unreadable the End stays **locked** and says so in `/endstatus` and the
console, rather than silently unlocking — `/endscheduler open` is the escape hatch.

---

## Adding more plugins later

Add a directory, give it its own `pom.xml` with no `<parent>`, and list it in the root
`<modules>`. Bounties, King of the Hill and Revenge Contracts each get their own JAR that
way, and none of them can break the others.
