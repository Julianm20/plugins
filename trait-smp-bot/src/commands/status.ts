import { SlashCommandBuilder, type ChatInputCommandInteraction } from 'discord.js';

import type { BotCommand, CommandContext } from './types';

/** Anything older than this gets re-queried rather than served from cache. */
const STALE_AFTER_MS = 30_000;

export const statusCommand: BotCommand = {
  data: new SlashCommandBuilder()
    .setName('status')
    .setDescription('Show the current Trait SMP server status'),

  async execute(
    interaction: ChatInputCommandInteraction,
    { monitor, config }: CommandContext,
  ): Promise<void> {
    await interaction.deferReply();

    const cached = monitor.latestStatus;
    const age = monitor.lastCheck ? Date.now() - monitor.lastCheck.getTime() : Infinity;
    // Reuse the monitor's reading when it is recent; otherwise query now.
    const status = cached && age < STALE_AFTER_MS ? cached : await monitor.check(false);

    if (monitor.currentState === 'online') {
      const max = status.maxPlayers || config.maxPlayers;
      await interaction.editReply(
        `🟢 **${config.serverName} is online!**\n` +
          `Players: ${status.playerCount}/${max}\n` +
          `Version: ${status.version ?? '1.21.x'}`,
      );
      return;
    }

    if (monitor.currentState === 'offline') {
      await interaction.editReply(`🔴 **${config.serverName} is currently offline.**`);
      return;
    }

    await interaction.editReply(
      `🟡 **${config.serverName} is not responding right now.** Still checking.`,
    );
  },
};
