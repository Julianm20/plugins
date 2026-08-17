import type {
  ChatInputCommandInteraction,
  SlashCommandOptionsOnlyBuilder,
  SlashCommandBuilder,
} from 'discord.js';

import type { BotConfig } from '../utils/config';
import type { DiscordStatusService } from '../services/discordStatus';
import type { StatusMonitor } from '../services/statusMonitor';
import type { StateStore } from '../store/stateStore';

/** Everything a command is allowed to reach. Keeps commands out of globals. */
export interface CommandContext {
  config: BotConfig;
  monitor: StatusMonitor;
  discordStatus: DiscordStatusService;
  store: StateStore;
}

export interface BotCommand {
  data: SlashCommandBuilder | SlashCommandOptionsOnlyBuilder;
  execute(
    interaction: ChatInputCommandInteraction,
    context: CommandContext,
  ): Promise<void>;
}
