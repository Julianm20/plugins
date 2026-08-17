import {
  ChannelType,
  Client,
  EmbedBuilder,
  PermissionFlagsBits,
  type Message,
  type TextChannel,
} from 'discord.js';

import type { BotConfig } from '../utils/config';
import { logger } from '../utils/logger';
import type { MinecraftStatus } from './minecraftStatus';
import type { ServerState, StateStore } from '../store/stateStore';

const COLOUR_ONLINE = 0x57f287;
const COLOUR_OFFLINE = 0xed4245;
const COLOUR_UNKNOWN = 0xfee75c;

/** Discord error code for a message that no longer exists. */
const UNKNOWN_MESSAGE = 10008;
const UNKNOWN_CHANNEL = 10003;

export interface RenderedStatus {
  embed: EmbedBuilder;
  /** Content fingerprint, so we can skip edits that would change nothing. */
  signature: string;
}

/**
 * Owns the single status message: building the embed, finding or creating the
 * message, and editing it only when the content actually differs.
 */
export class DiscordStatusService {
  private readonly client: Client;
  private readonly config: BotConfig;
  private readonly store: StateStore;
  private lastSignature: string | null = null;
  private lastUpdatedAt: Date = new Date();

  constructor(client: Client, config: BotConfig, store: StateStore) {
    this.client = client;
    this.config = config;
    this.store = store;
  }

  // ------------------------------------------------------------- rendering

  render(status: MinecraftStatus, state: ServerState): RenderedStatus {
    const name = this.config.serverName;
    const address = this.config.minecraft.port === 25565
      ? this.config.minecraft.host
      : `${this.config.minecraft.host}:${this.config.minecraft.port}`;

    const embed = new EmbedBuilder().setFooter({ text: this.config.footerText });

    if (state === 'online') {
      const maxPlayers = status.maxPlayers || this.config.maxPlayers;
      embed
        .setTitle(`🟢 ${name} — ONLINE`)
        .setDescription('The server is currently online and accepting players.')
        .setColor(COLOUR_ONLINE)
        .addFields(
          { name: '👥 Players', value: `${status.playerCount} / ${maxPlayers}`, inline: true },
          { name: '🎮 Version', value: status.version ?? '1.21.x', inline: true },
          { name: '📡 Status', value: 'Online', inline: true },
          { name: '🌐 Server', value: `\`${address}\``, inline: false },
        );
      if (status.motd) {
        embed.addFields({ name: '📜 MOTD', value: truncate(status.motd, 512), inline: false });
      }
    } else if (state === 'offline') {
      embed
        .setTitle(`🔴 ${name} — OFFLINE`)
        .setDescription('The Minecraft server is currently offline.')
        .setColor(COLOUR_OFFLINE)
        .addFields(
          // Never a stale player count here: an unreachable server reports nobody.
          { name: '👥 Players', value: `0 / ${this.config.maxPlayers}`, inline: true },
          { name: '📡 Status', value: 'Offline', inline: true },
          { name: '🌐 Server', value: `\`${address}\``, inline: false },
        );
    } else {
      embed
        .setTitle(`🟡 ${name} — CHECKING`)
        .setDescription('The server did not answer the last check. Retrying...')
        .setColor(COLOUR_UNKNOWN)
        .addFields(
          { name: '📡 Status', value: 'Unknown', inline: true },
          { name: '🌐 Server', value: `\`${address}\``, inline: false },
        );
    }

    // Everything that is visible and can change. The timestamp is excluded on
    // purpose - including it would make every single check an edit.
    const signature = [
      state,
      status.playerCount,
      status.maxPlayers,
      status.version ?? '',
      status.motd ?? '',
    ].join('|');

    return { embed, signature };
  }

  /** Adds the relative timestamp. Discord renders it live, so it stays fresh without edits. */
  private stampEmbed(embed: EmbedBuilder, updatedAt: Date): EmbedBuilder {
    const unix = Math.floor(updatedAt.getTime() / 1000);
    return embed.addFields({
      name: '⏱️ Last Updated',
      value: `<t:${unix}:R>`,
      inline: false,
    });
  }

  // ------------------------------------------------------- message handling

  async resolveChannel(channelId: string | null): Promise<TextChannel | null> {
    const id = channelId ?? this.store.current().statusChannelId ?? this.config.statusChannelId;
    if (!id) {
      return null;
    }
    try {
      const channel = await this.client.channels.fetch(id);
      if (!channel || channel.type !== ChannelType.GuildText) {
        logger.warn(`Channel ${id} is not a text channel.`);
        return null;
      }
      return channel as TextChannel;
    } catch (error: unknown) {
      if (codeOf(error) === UNKNOWN_CHANNEL) {
        logger.warn(`Status channel ${id} no longer exists.`);
      } else {
        logger.error('Failed to fetch the status channel', error);
      }
      return null;
    }
  }

  /** Checks we can actually post before trying, so failures are explainable. */
  missingPermissions(channel: TextChannel): string[] {
    const me = channel.guild.members.me;
    if (!me) {
      return [];
    }
    const perms = channel.permissionsFor(me);
    if (!perms) {
      return [];
    }
    const needed: Array<[bigint, string]> = [
      [PermissionFlagsBits.ViewChannel, 'View Channel'],
      [PermissionFlagsBits.SendMessages, 'Send Messages'],
      [PermissionFlagsBits.EmbedLinks, 'Embed Links'],
      [PermissionFlagsBits.ReadMessageHistory, 'Read Message History'],
    ];
    return needed.filter(([flag]) => !perms.has(flag)).map(([, label]) => label);
  }

  /** The stored status message, or null when it is gone or was never made. */
  async findMessage(channel: TextChannel): Promise<Message | null> {
    const messageId = this.store.current().statusMessageId;
    if (!messageId) {
      return null;
    }
    try {
      return await channel.messages.fetch(messageId);
    } catch (error: unknown) {
      if (codeOf(error) === UNKNOWN_MESSAGE) {
        logger.warn('Saved status message is gone; a new one will be created.');
        await this.store.patch({ statusMessageId: null });
        return null;
      }
      logger.error('Failed to fetch the status message', error);
      return null;
    }
  }

  /**
   * Publishes a status reading.
   *
   * Creates the message the first time, edits it afterwards, and skips the edit
   * entirely when nothing visible changed.
   *
   * @param force edit even when the content is identical
   * @returns whether Discord was actually written to
   */
  async publish(
    status: MinecraftStatus,
    state: ServerState,
    force = false,
  ): Promise<boolean> {
    const channel = await this.resolveChannel(null);
    if (!channel) {
      logger.warn('No status channel configured; run /setup-status in the channel you want.');
      return false;
    }

    const missing = this.missingPermissions(channel);
    if (missing.length > 0) {
      logger.error(`Missing permissions in #${channel.name}: ${missing.join(', ')}`);
      return false;
    }

    const { embed, signature } = this.render(status, state);
    const existing = await this.findMessage(channel);
    const changed = signature !== this.lastSignature;

    if (existing && !changed && !force) {
      logger.debug('Status unchanged; skipping the edit.');
      return false;
    }

    if (changed) {
      this.lastUpdatedAt = status.checkedAt;
    }
    const finished = this.stampEmbed(embed, this.lastUpdatedAt);

    try {
      if (existing) {
        await withRetry(() => existing.edit({ embeds: [finished] }), 'edit status message');
      } else {
        const created = await withRetry(
          () => channel.send({ embeds: [finished] }),
          'create status message',
        );
        await this.store.patch({
          statusMessageId: created.id,
          statusChannelId: channel.id,
        });
        logger.info(`Created the status message in #${channel.name}.`);
      }
      this.lastSignature = signature;
      logger.info('Status message updated.');
      return true;
    } catch (error) {
      logger.error('Could not update the status message', error);
      return false;
    }
  }

  /** Posts a one-off state-change notification, when enabled. */
  async notify(state: ServerState): Promise<void> {
    if (!this.config.notificationsEnabled || state === 'unknown') {
      return;
    }
    const channel = await this.resolveChannel(this.config.notificationChannelId);
    if (!channel) {
      return;
    }
    const text =
      state === 'online'
        ? `🟢 **${this.config.serverName} is now ONLINE!**`
        : `🔴 **${this.config.serverName} is now OFFLINE.**`;
    try {
      await withRetry(() => channel.send({ content: text }), 'send notification');
      logger.info(`Sent a ${state} notification.`);
    } catch (error) {
      logger.error('Could not send the notification', error);
    }
  }

  /** Forgets the cached fingerprint so the next publish definitely writes. */
  invalidate(): void {
    this.lastSignature = null;
  }
}

function truncate(value: string, limit: number): string {
  return value.length <= limit ? value : `${value.slice(0, limit - 1)}…`;
}

function codeOf(error: unknown): number | undefined {
  return typeof error === 'object' && error !== null && 'code' in error
    ? (error as { code?: number }).code
    : undefined;
}

/** Retries a Discord call a few times with exponential backoff. */
async function withRetry<T>(operation: () => Promise<T>, description: string): Promise<T> {
  const delays = [1000, 3000, 8000];
  let lastError: unknown;

  for (let attempt = 0; attempt <= delays.length; attempt++) {
    try {
      return await operation();
    } catch (error) {
      lastError = error;
      const code = codeOf(error);
      // A missing message or channel will not fix itself by retrying.
      if (code === UNKNOWN_MESSAGE || code === UNKNOWN_CHANNEL) {
        throw error;
      }
      const delay = delays[attempt];
      if (delay === undefined) {
        break;
      }
      logger.warn(`Failed to ${description}; retrying in ${delay}ms`, error);
      await sleep(delay);
    }
  }
  throw lastError;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
