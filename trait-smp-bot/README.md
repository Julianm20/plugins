# Trait SMP Discord Bot

Live Minecraft server status in Discord: one embed that keeps itself up to date, plus
slash commands. Built so the status system is only the first module — bounties, KOTH,
kill streaks and the rest can be added as sibling services without touching this code.

---

## 1. Requirements

- **Node.js 18 or newer** (22 LTS recommended) — <https://nodejs.org>
- A Discord account with permission to add a bot to your server
- Your Minecraft server reachable at `traitsmp.duckdns.org:25565`

Check Node is installed:

```bash
node --version
npm --version
```

## 2. Installation

```bash
cd trait-smp-bot
npm install
cp .env.example .env      # Windows: copy .env.example .env
```

Then edit `.env` (see [section 6](#6-what-goes-in-env)).

## 3. Creating the Discord bot

1. Go to <https://discord.com/developers/applications> → **New Application**, name it
   `Trait SMP`.
2. **Bot** tab → **Reset Token** → **Copy**. This is your `DISCORD_TOKEN`.
   You only get to see it once; if you lose it, reset it again.
3. Still on the Bot tab, turn **off** *Public Bot* unless you want others adding it.
4. **Installation** (or **OAuth2 → URL Generator**) → scopes `bot` and
   `applications.commands` → pick the permissions in section 5 → open the generated URL
   and invite the bot to your server.

**Never paste your token into a file you commit, a screenshot, or a Discord message.**
If it leaks, reset it immediately — anyone with it controls the bot.

## 4. Required intents

**None of the privileged ones.** The bot requests only the default `Guilds` intent.

You do **not** need to enable Message Content, Server Members, or Presence. If a guide
tells you to turn those on, it is for a different bot — leave them off.

## 5. Required permissions

| Permission | Why |
|---|---|
| View Channel | see `#server-status` |
| Send Messages | create the status message |
| Embed Links | the status message is an embed |
| Read Message History | fetch its own message back after a restart |

That is the whole list. **Do not give it Administrator.** The bot never deletes other
people's messages, kicks anyone, or reads chat.

Permission integer: **84992**

## 6. What goes in `.env`

```env
DISCORD_TOKEN=your-token-from-step-3
DISCORD_GUILD_ID=your-server-id
STATUS_CHANNEL_ID=your-server-status-channel-id

MINECRAFT_HOST=traitsmp.duckdns.org
MINECRAFT_PORT=25565
MINECRAFT_TIMEOUT=5000

STATUS_INTERVAL=60
MAX_PLAYERS=50
OFFLINE_FAILURE_THRESHOLD=2

STATUS_NOTIFICATIONS=false
NOTIFICATION_CHANNEL_ID=

SERVER_NAME=TRAIT SMP
EMBED_FOOTER=Trait SMP • Season 1
LOG_LEVEL=info
```

To get IDs: Discord **Settings → Advanced → Developer Mode: on**, then right-click your
server → *Copy Server ID*, and right-click `#server-status` → *Copy Channel ID*.

`STATUS_CHANNEL_ID` is optional — leave it blank and run `/setup-status` in the channel
you want instead. The bot remembers it.

Config is validated at startup. A channel name pasted where an ID belongs, a port out of
range or a URL in `MINECRAFT_HOST` all produce a clear error rather than a confusing
crash later.

## 7. Starting it

```bash
npm run build     # compile TypeScript
npm run deploy    # register slash commands (run once, and after command changes)
npm start         # run the bot
```

Expected output:

```
2026-08-17 00:31:02 [INFO] Starting Trait SMP bot - monitoring traitsmp.duckdns.org:25565 every 60s
2026-08-17 00:31:03 [INFO] Discord bot connected as Trait SMP#1234
2026-08-17 00:31:03 [INFO] Minecraft status monitoring started (every 60s).
2026-08-17 00:31:04 [INFO] Minecraft server ONLINE - 12/50 players
2026-08-17 00:31:04 [INFO] Status message updated.
```

## 8. Registering slash commands

`npm run deploy` registers them to your one guild, which appears **instantly**. (Global
commands can take up to an hour to show up, which is why this uses guild scope.)

Re-run it whenever you change a command's name, description or options. You do not need
to re-run it for code changes inside a command.

## 9. Setting up `#server-status`

1. Make a text channel called `server-status`.
2. Lock it down: **Edit Channel → Permissions → @everyone → Send Messages: off**. The
   bot still posts because its own permissions are separate.
3. In that channel, run `/setup-status`.

The bot creates one message and edits it from then on. If you run `/setup-status` again
it tells you it is already configured and links the existing message — use
`/setup-status reset:true` to delete it and start a fresh one.

## 10. How the monitor works

Every `STATUS_INTERVAL` seconds the bot queries the server and decides what to show.

**It does not edit the message every minute.** The embed's contents are fingerprinted,
and Discord is only written to when something visible actually changed — player count,
version, MOTD or online state. A quiet server with a steady player count produces zero
API calls.

The "Last Updated" field uses Discord's relative timestamp (`<t:…:R>`), so it keeps
counting up in everyone's client without the bot touching the message. It shows when the
content last *changed*, not when it was last checked — the bot is still polling on
schedule regardless.

**Offline detection is deliberately slow to fire.** One failed check is not an outage —
it is usually a dropped UDP packet or a slow DNS answer. So:

| Consecutive failures | Result |
|---|---|
| 1 | `unknown` — embed turns yellow, previous verdict held |
| `OFFLINE_FAILURE_THRESHOLD` (default 2) | `offline` — embed turns red |
| any success | straight back to `online` |

This stops the embed flapping green/red and training people to ignore it. Raise the
threshold if your connection is flaky.

**Notifications** (`STATUS_NOTIFICATIONS=true`) fire only on a real state transition,
never on a routine check.

## 11. Commands

| Command | Who | What |
|---|---|---|
| `/status` | everyone | current status as a quick reply |
| `/bot-status` | everyone | bot uptime, Discord ping, monitoring state |
| `/setup-status` | admins | create the live status message |
| `/refresh-status` | admins | force a fresh query and update |

Admin commands are locked with `setDefaultMemberPermissions(Administrator)` **and**
re-checked at execution time, because the default can be overridden per-guild in
Discord's UI.

## 12. Troubleshooting

**"DISCORD_TOKEN is missing"** — no `.env`, or you ran the bot from the wrong directory.
It must be run from inside `trait-smp-bot`.

**Commands do not appear** — run `npm run deploy`. Check the bot was invited with the
`applications.commands` scope; if not, re-invite it with the URL from step 3.

**"Missing permissions in #server-status"** — the console lists exactly which ones. Add
them to the bot's role or the channel override.

**Server always shows offline, but you can join it** — the bot queries from wherever it
runs. Test from that same machine:

```bash
# Windows PowerShell
Test-NetConnection traitsmp.duckdns.org -Port 25565
```

Common causes: `enable-status=false` in `server.properties` (must be `true`), the port
not forwarded, or a firewall blocking outbound 25565. Raise `MINECRAFT_TIMEOUT` if your
server is slow to answer.

**Status flips between online and offline** — raise `OFFLINE_FAILURE_THRESHOLD`.

**Duplicate status messages** — delete the extras by hand and run
`/setup-status reset:true`. The message ID lives in `data/state.json`; deleting that file
makes the bot create a new message on next start.

**Set `LOG_LEVEL=debug`** to see every check, including the exact query error.

## 13. Keeping it running

### Windows (your PC or NAS)

**PM2** is the simplest option that survives reboots:

```powershell
npm install -g pm2 pm2-windows-startup
pm2-startup install

cd C:\path\to\trait-smp-bot
npm run build
pm2 start dist/index.js --name trait-smp-bot
pm2 save
```

Useful afterwards:

```powershell
pm2 logs trait-smp-bot
pm2 restart trait-smp-bot
pm2 stop trait-smp-bot
pm2 list
```

**Task Scheduler** if you would rather not install PM2: create a task, trigger *At
startup*, action *Start a program* → `node`, arguments `dist\index.js`, "Start in" set to
the project folder. Tick *Run whether user is logged on or not*.

**A plain terminal** works for testing — just remember it dies when you close the window.

### Linux / NAS with systemd

```ini
# /etc/systemd/system/trait-smp-bot.service
[Unit]
Description=Trait SMP Discord Bot
After=network-online.target

[Service]
Type=simple
User=youruser
WorkingDirectory=/opt/trait-smp-bot
ExecStart=/usr/bin/node dist/index.js
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl enable --now trait-smp-bot
journalctl -u trait-smp-bot -f
```

The bot handles `SIGINT`/`SIGTERM` cleanly, so restarts and reboots do not leave a
half-written state file or a duplicate status message.

## 14. Project layout

```
src/
├── commands/          one file per slash command + a registry
├── services/
│   ├── minecraftStatus.ts   the ONLY file that knows the Minecraft protocol
│   ├── discordStatus.ts     embed building and the single-message lifecycle
│   └── statusMonitor.ts     the polling loop and offline-detection logic
├── store/stateStore.ts      persistence behind an interface
├── utils/                   config loading and logging
├── events/ready.ts          startup sequence
├── deploy-commands.ts       slash command registration
└── index.ts                 wiring
```

**Adding a feature later** means adding a service and a command, not editing existing
ones. Two seams are there specifically for that:

- `MinecraftStatusProvider` — anything that can report server status. A future plugin
  pushing data over HTTP implements this interface and nothing else changes.
- `StateStore` — swap `JsonStateStore` for a SQLite or Postgres implementation and every
  caller keeps working. That is the path for player stats and PvP event history.

## 15. A note on the Minecraft library

`minecraft-server-util` is marked deprecated by its author, who suggests the mcstatus.io
web API instead. This project keeps it anyway, deliberately:

- It queries **your server directly**. The mcstatus.io API queries from *their* servers,
  which adds a third party to every poll, fails when they do, leaks your address on a
  schedule, and cannot see a server that is only reachable on your LAN.
- The Server List Ping protocol it implements has been stable for years, so "unmaintained"
  carries much less risk here than it would for a fast-moving API client.

If it ever does break, `MinecraftStatusService` is the only file that touches it —
implement `MinecraftStatusProvider` with another library and change one line in
`index.ts`.
