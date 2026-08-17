import * as dotenv from 'dotenv';

// quiet: dotenv v17 otherwise prints a promotional banner on every start.
dotenv.config({ quiet: true });

export interface MinecraftConfig {
  host: string;
  port: number;
  timeoutMs: number;
}

export interface BotConfig {
  discordToken: string;
  guildId: string;
  statusChannelId: string | null;
  notificationChannelId: string | null;
  minecraft: MinecraftConfig;
  statusIntervalMs: number;
  maxPlayers: number;
  offlineFailureThreshold: number;
  notificationsEnabled: boolean;
  serverName: string;
  footerText: string;
  logLevel: string;
}

export class ConfigError extends Error {}

function required(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) {
    throw new ConfigError(
      `${name} is missing. Copy .env.example to .env and fill it in.`,
    );
  }
  return value;
}

function optional(name: string): string | null {
  const value = process.env[name]?.trim();
  return value ? value : null;
}

function integer(name: string, fallback: number, min: number, max: number): number {
  const raw = process.env[name]?.trim();
  if (!raw) {
    return fallback;
  }
  const parsed = Number.parseInt(raw, 10);
  if (Number.isNaN(parsed)) {
    throw new ConfigError(`${name} must be a whole number, got "${raw}".`);
  }
  if (parsed < min || parsed > max) {
    throw new ConfigError(`${name} must be between ${min} and ${max}, got ${parsed}.`);
  }
  return parsed;
}

function boolean(name: string, fallback: boolean): boolean {
  const raw = process.env[name]?.trim().toLowerCase();
  if (!raw) {
    return fallback;
  }
  return raw === 'true' || raw === '1' || raw === 'yes';
}

/** A Discord snowflake: 17-20 digits. Catches pasted channel names early. */
function snowflake(name: string, value: string | null): string | null {
  if (value === null) {
    return null;
  }
  if (!/^\d{17,20}$/.test(value)) {
    throw new ConfigError(
      `${name} does not look like a Discord ID (expected 17-20 digits, got "${value}"). ` +
        'Enable Developer Mode in Discord, then right-click and Copy ID.',
    );
  }
  return value;
}

export function loadConfig(): BotConfig {
  const token = required('DISCORD_TOKEN');
  const guildId = snowflake('DISCORD_GUILD_ID', required('DISCORD_GUILD_ID'))!;

  const host = process.env.MINECRAFT_HOST?.trim() || 'traitsmp.duckdns.org';
  if (/^https?:\/\//i.test(host)) {
    throw new ConfigError(
      `MINECRAFT_HOST should be a bare hostname such as traitsmp.duckdns.org, not a URL ("${host}").`,
    );
  }

  return {
    discordToken: token,
    guildId,
    statusChannelId: snowflake('STATUS_CHANNEL_ID', optional('STATUS_CHANNEL_ID')),
    notificationChannelId: snowflake(
      'NOTIFICATION_CHANNEL_ID',
      optional('NOTIFICATION_CHANNEL_ID'),
    ),
    minecraft: {
      host,
      port: integer('MINECRAFT_PORT', 25565, 1, 65535),
      timeoutMs: integer('MINECRAFT_TIMEOUT', 5000, 500, 60000),
    },
    // Discord rate-limits message edits, so refuse anything faster than 10s.
    statusIntervalMs: integer('STATUS_INTERVAL', 60, 10, 86400) * 1000,
    maxPlayers: integer('MAX_PLAYERS', 50, 1, 10000),
    offlineFailureThreshold: integer('OFFLINE_FAILURE_THRESHOLD', 2, 1, 20),
    notificationsEnabled: boolean('STATUS_NOTIFICATIONS', false),
    serverName: process.env.SERVER_NAME?.trim() || 'TRAIT SMP',
    footerText: process.env.EMBED_FOOTER?.trim() || 'Trait SMP • Season 1',
    logLevel: process.env.LOG_LEVEL?.trim().toLowerCase() || 'info',
  };
}
