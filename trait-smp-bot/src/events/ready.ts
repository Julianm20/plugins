import type { Client } from 'discord.js';

import { logger } from '../utils/logger';
import type { StatusMonitor } from '../services/statusMonitor';

/**
 * Startup sequence once Discord is connected: find the status channel, reuse the
 * existing message if there is one, then begin the monitoring loop.
 */
export async function onReady(client: Client<true>, monitor: StatusMonitor): Promise<void> {
  logger.info(`Discord bot connected as ${client.user.tag}`);
  await monitor.start();
}
