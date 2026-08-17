import type { BotCommand } from './types';
import { botStatusCommand } from './botStatus';
import { refreshStatusCommand } from './refreshStatus';
import { setupStatusCommand } from './setupStatus';
import { statusCommand } from './status';

/** Every command the bot exposes. Adding one means adding it here and nowhere else. */
export const commands: BotCommand[] = [
  statusCommand,
  setupStatusCommand,
  refreshStatusCommand,
  botStatusCommand,
];

export const commandsByName = new Map<string, BotCommand>(
  commands.map((command) => [command.data.name, command]),
);

export type { BotCommand, CommandContext } from './types';
