import {
  ChannelType,
  PermissionFlagsBits,
  SlashCommandBuilder,
  type ChatInputCommandInteraction,
  type TextChannel,
} from 'discord.js';

import { logger } from '../utils/logger';
import type { BotCommand, CommandContext } from './types';

export const setupStatusCommand: BotCommand = {
  data: new SlashCommandBuilder()
    .setName('setup-status')
    .setDescription('Create the live status message in a channel')
    .setDefaultMemberPermissions(PermissionFlagsBits.Administrator)
    .addChannelOption((option) =>
      option
        .setName('channel')
        .setDescription('Where the status message goes (defaults to this channel)')
        .addChannelTypes(ChannelType.GuildText)
        .setRequired(false),
    )
    .addBooleanOption((option) =>
      option
        .setName('reset')
        .setDescription('Replace the existing status message with a new one')
        .setRequired(false),
    ),

  async execute(
    interaction: ChatInputCommandInteraction,
    { monitor, discordStatus, store }: CommandContext,
  ): Promise<void> {
    if (!interaction.memberPermissions?.has(PermissionFlagsBits.Administrator)) {
      await interaction.reply({
        content: 'That command is for administrators only.',
        ephemeral: true,
      });
      return;
    }

    await interaction.deferReply({ ephemeral: true });

    const chosen = interaction.options.getChannel('channel');
    const reset = interaction.options.getBoolean('reset') ?? false;
    const channel = (chosen ?? interaction.channel) as TextChannel | null;

    if (!channel || channel.type !== ChannelType.GuildText) {
      await interaction.editReply('Pick a normal text channel for the status message.');
      return;
    }

    const missing = discordStatus.missingPermissions(channel);
    if (missing.length > 0) {
      await interaction.editReply(
        `I am missing these permissions in <#${channel.id}>: **${missing.join(', ')}**. ` +
          'Grant them and run this again.',
      );
      return;
    }

    // Refuse to silently orphan a working status message.
    const existingId = store.current().statusMessageId;
    if (existingId && !reset) {
      const existingChannelId = store.current().statusChannelId;
      const alive = await discordStatus
        .resolveChannel(existingChannelId)
        .then((c) => (c ? discordStatus.findMessage(c) : null));

      if (alive) {
        await interaction.editReply(
          'The status system is already set up: ' +
            `${alive.url}\n\n` +
            'Run `/setup-status reset:true` to replace it with a new message ' +
            '(the old one is deleted), or `/refresh-status` to just update it.',
        );
        return;
      }
    }

    if (reset && existingId) {
      const oldChannel = await discordStatus.resolveChannel(store.current().statusChannelId);
      const old = oldChannel ? await discordStatus.findMessage(oldChannel) : null;
      if (old) {
        try {
          await old.delete();
          logger.info('Deleted the previous status message during reset.');
        } catch (error) {
          logger.warn('Could not delete the previous status message', error);
        }
      }
    }

    // Clearing the id is what makes publish() create a fresh message.
    await store.patch({ statusMessageId: null, statusChannelId: channel.id });
    discordStatus.invalidate();

    const status = await monitor.refresh();
    const created = store.current().statusMessageId;

    if (!created) {
      await interaction.editReply(
        'Could not create the status message. Check the console for the reason.',
      );
      return;
    }

    if (!monitor.isRunning) {
      await monitor.start();
    }

    await interaction.editReply(
      `Status system set up in <#${channel.id}>.\n` +
        `The Minecraft server is currently **${status.online ? 'online' : 'not responding'}**. ` +
        'The message will keep itself up to date from now on.',
    );
  },
};
