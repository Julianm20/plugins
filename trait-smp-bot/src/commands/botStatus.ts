import { EmbedBuilder, SlashCommandBuilder, type ChatInputCommandInteraction } from 'discord.js';

import type { BotCommand, CommandContext } from './types';

function formatUptime(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000);
  const days = Math.floor(totalSeconds / 86400);
  const hours = Math.floor((totalSeconds % 86400) / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  if (days > 0) return `${days}d ${hours}h ${minutes}m`;
  if (hours > 0) return `${hours}h ${minutes}m`;
  return `${minutes}m`;
}

export const botStatusCommand: BotCommand = {
  data: new SlashCommandBuilder()
    .setName('bot-status')
    .setDescription('Show information about the bot itself'),

  async execute(
    interaction: ChatInputCommandInteraction,
    { monitor, config }: CommandContext,
  ): Promise<void> {
    // websocket ping is -1 until the first heartbeat completes.
    const ping = Math.round(interaction.client.ws.ping);
    const lastCheck = monitor.lastCheck;
    const state = monitor.currentState;
    const stateLabel =
      state === 'online' ? '🟢 Online' : state === 'offline' ? '🔴 Offline' : '🟡 Unknown';

    const embed = new EmbedBuilder()
      .setTitle('🤖 Bot Online')
      .setColor(0x5865f2)
      .addFields(
        { name: '📡 Discord Ping', value: ping < 0 ? 'measuring…' : `${ping}ms`, inline: true },
        { name: '⏱️ Uptime', value: formatUptime(monitor.uptimeMs), inline: true },
        {
          name: '🔁 Monitoring',
          value: monitor.isRunning
            ? `Every ${config.statusIntervalMs / 1000}s`
            : 'Stopped',
          inline: true,
        },
        { name: '🎮 Minecraft', value: stateLabel, inline: true },
        {
          name: '🕒 Last Check',
          value: lastCheck ? `<t:${Math.floor(lastCheck.getTime() / 1000)}:R>` : 'not yet',
          inline: true,
        },
        {
          name: '🌐 Server',
          value: `\`${config.minecraft.host}:${config.minecraft.port}\``,
          inline: true,
        },
      )
      .setFooter({ text: config.footerText });

    await interaction.reply({ embeds: [embed] });
  },
};
