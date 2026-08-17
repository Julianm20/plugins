import { promises as fs } from 'node:fs';
import * as path from 'node:path';

import { logger } from '../utils/logger';

export type ServerState = 'online' | 'offline' | 'unknown';

/** Everything the bot needs to remember across restarts. */
export interface BotState {
  statusChannelId: string | null;
  statusMessageId: string | null;
  lastKnownState: ServerState;
  lastCheckedAt: string | null;
}

export const EMPTY_STATE: BotState = {
  statusChannelId: null,
  statusMessageId: null,
  lastKnownState: 'unknown',
  lastCheckedAt: null,
};

/**
 * Persistence boundary.
 *
 * Deliberately an interface with a narrow surface: swapping the JSON file for
 * SQLite or Postgres later means writing one new class, not touching callers.
 */
export interface StateStore {
  load(): Promise<BotState>;
  patch(changes: Partial<BotState>): Promise<BotState>;
  current(): BotState;
}

/** File-backed store. Fine for one server; replace when it stops being. */
export class JsonStateStore implements StateStore {
  private readonly file: string;
  private state: BotState = { ...EMPTY_STATE };
  /** Serialises writes so two quick updates cannot interleave and lose one. */
  private writeChain: Promise<void> = Promise.resolve();

  constructor(file = path.join(process.cwd(), 'data', 'state.json')) {
    this.file = file;
  }

  async load(): Promise<BotState> {
    try {
      const raw = await fs.readFile(this.file, 'utf8');
      const parsed = JSON.parse(raw) as Partial<BotState>;
      this.state = { ...EMPTY_STATE, ...parsed };
      logger.debug('Loaded persisted state', this.state);
    } catch (error: unknown) {
      if ((error as NodeJS.ErrnoException)?.code === 'ENOENT') {
        logger.info('No saved state yet; starting fresh.');
      } else {
        // A corrupt file must not stop the bot booting - start clean instead.
        logger.warn('Could not read state file, starting fresh', error);
      }
      this.state = { ...EMPTY_STATE };
    }
    return this.state;
  }

  current(): BotState {
    return this.state;
  }

  async patch(changes: Partial<BotState>): Promise<BotState> {
    this.state = { ...this.state, ...changes };
    const snapshot = JSON.stringify(this.state, null, 2);

    this.writeChain = this.writeChain.then(async () => {
      try {
        await fs.mkdir(path.dirname(this.file), { recursive: true });
        // Write then rename, so a crash mid-write cannot leave a truncated file.
        const temp = `${this.file}.tmp`;
        await fs.writeFile(temp, snapshot, 'utf8');
        await fs.rename(temp, this.file);
      } catch (error) {
        logger.error('Failed to save state file', error);
      }
    });
    await this.writeChain;
    return this.state;
  }
}
