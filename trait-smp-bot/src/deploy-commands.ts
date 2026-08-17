import { REST, Routes } from 'discord.js';

import { commands } from './commands';
import { ConfigError, loadConfig } from './utils/config';
import { logger } from './utils/logger';

/**
 * Registers the slash commands with Discord.
 *
 * Guild-scoped rather than global: guild commands appear immediately, while
 * global ones can take up to an hour to propagate. Run this once, and again
 * whenever a command's name, description or options change.
 */
async function deploy(): Promise<void> {
  const config = loadConfig();
  logger.setLevel(config.logLevel);

  const body = commands.map((command) => command.data.toJSON());
  const rest = new REST({ version: '10' }).setToken(config.discordToken);

  logger.info(`Registering ${body.length} slash command(s) with guild ${config.guildId}...`);

  const application = (await rest.get(Routes.oauth2CurrentApplication())) as { id: string };
  await rest.put(Routes.applicationGuildCommands(application.id, config.guildId), { body });

  for (const command of commands) {
    logger.info(`  /${command.data.name} - ${command.data.description}`);
  }
  logger.info('Slash commands registered. They should appear in Discord straight away.');
}

deploy().catch((error: unknown) => {
  if (error instanceof ConfigError) {
    logger.error(`Configuration problem: ${error.message}`);
  } else {
    logger.error('Failed to register slash commands', error);
  }
  process.exit(1);
});
