import {
  PermissionFlagsBits,
  SlashCommandBuilder,
  type ChatInputCommandInteraction,
} from 'discord.js';

import type { BotCommand, CommandContext } from './types';

export const refreshStatusCommand: BotCommand = {
  data: new SlashCommandBuilder()
    .setName('refresh-status')
    .setDescription('Re-query the Minecraft server and update the status message')
    .setDefaultMemberPermissions(PermissionFlagsBits.Administrator),

  async execute(
    interaction: ChatInputCommandInteraction,
    { monitor, config }: CommandContext,
  ): Promise<void> {
    // Belt and braces: the permission above can be overridden per guild, so the
    // check is repeated here rather than trusted.
    if (!interaction.memberPermissions?.has(PermissionFlagsBits.Administrator)) {
      await interaction.reply({
        content: 'That command is for administrators only.',
        ephemeral: true,
      });
      return;
    }

    await interaction.deferReply({ ephemeral: true });
    const status = await monitor.refresh();

    await interaction.editReply(
      status.online
        ? `✅ Refreshed. ${config.serverName} is online with ` +
            `${status.playerCount}/${status.maxPlayers || config.maxPlayers} players.`
        : `✅ Refreshed. ${config.serverName} did not respond` +
            (status.error ? ` (${status.error}).` : '.'),
    );
  },
};
