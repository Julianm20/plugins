import { Client, Events, GatewayIntentBits, MessageFlags } from 'discord.js';

import { commandsByName, type CommandContext } from './commands';
import { onReady } from './events/ready';
import { DiscordStatusService } from './services/discordStatus';
import { MinecraftStatusService } from './services/minecraftStatus';
import { StatusMonitor } from './services/statusMonitor';
import { JsonStateStore } from './store/stateStore';
import { ConfigError, loadConfig } from './utils/config';
import { logger } from './utils/logger';

async function main(): Promise<void> {
  const config = loadConfig();
  logger.setLevel(config.logLevel);
  logger.info(
    `Starting Trait SMP bot - monitoring ${config.minecraft.host}:${config.minecraft.port} ` +
      `every ${config.statusIntervalMs / 1000}s`,
  );

  const store = new JsonStateStore();
  await store.load();

  // If the channel is pinned in .env it wins; otherwise whatever /setup-status saved.
  if (config.statusChannelId && !store.current().statusChannelId) {
    await store.patch({ statusChannelId: config.statusChannelId });
  }

  // Guilds is the only intent needed: the bot reads no message content and
  // tracks no members, so asking for more would be requesting access we do not use.
  const client = new Client({ intents: [GatewayIntentBits.Guilds] });

  const minecraft = new MinecraftStatusService(config.minecraft, config.maxPlayers);
  const discordStatus = new DiscordStatusService(client, config, store);
  const monitor = new StatusMonitor(minecraft, discordStatus, config, store);

  const context: CommandContext = { config, monitor, discordStatus, store };

  client.once(Events.ClientReady, (ready) => {
    void onReady(ready, monitor).catch((error) =>
      logger.error('Startup routine failed', error),
    );
  });

  client.on(Events.InteractionCreate, async (interaction) => {
    if (!interaction.isChatInputCommand()) {
      return;
    }
    const command = commandsByName.get(interaction.commandName);
    if (!command) {
      logger.warn(`Received an unknown command: ${interaction.commandName}`);
      return;
    }

    try {
      await command.execute(interaction, context);
    } catch (error) {
      logger.error(`Command /${interaction.commandName} failed`, error);
      // Never leave the user staring at "thinking…" forever.
      const message = 'Something went wrong running that command.';
      try {
        if (interaction.deferred || interaction.replied) {
          await interaction.editReply(message);
        } else {
          await interaction.reply({ content: message, flags: MessageFlags.Ephemeral });
        }
      } catch (replyError) {
        logger.error('Could not report the command failure back to Discord', replyError);
      }
    }
  });

  client.on(Events.Error, (error) => logger.error('Discord client error', error));
  client.on(Events.ShardDisconnect, () => logger.warn('Disconnected from Discord'));
  client.on(Events.ShardReconnecting, () => logger.info('Reconnecting to Discord...'));

  const shutdown = (signal: string): void => {
    logger.info(`Received ${signal}, shutting down.`);
    monitor.stop();
    void client.destroy().finally(() => process.exit(0));
  };
  process.on('SIGINT', () => shutdown('SIGINT'));
  process.on('SIGTERM', () => shutdown('SIGTERM'));

  // A stray rejection should be logged, not silently kill the process.
  process.on('unhandledRejection', (reason) =>
    logger.error('Unhandled promise rejection', reason),
  );

  await client.login(config.discordToken);
}

main().catch((error: unknown) => {
  if (error instanceof ConfigError) {
    logger.error(`Configuration problem: ${error.message}`);
  } else {
    logger.error('Fatal startup error', error);
  }
  process.exit(1);
});
