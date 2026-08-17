import type { BotConfig } from '../utils/config';
import { logger } from '../utils/logger';
import type { ServerState, StateStore } from '../store/stateStore';
import type { DiscordStatusService } from './discordStatus';
import type { MinecraftStatus, MinecraftStatusProvider } from './minecraftStatus';

/**
 * The polling loop, and the only place that decides whether the server counts
 * as offline.
 *
 * Failure tolerance lives here: one missed reply is treated as {@code unknown}
 * rather than offline, because a single dropped packet or a slow DNS answer is
 * not an outage, and flipping the embed red on every hiccup trains people to
 * ignore it. Only {@code OFFLINE_FAILURE_THRESHOLD} consecutive failures commit
 * to "offline".
 */
export class StatusMonitor {
  private readonly minecraft: MinecraftStatusProvider;
  private readonly discord: DiscordStatusService;
  private readonly config: BotConfig;
  private readonly store: StateStore;

  private timer: NodeJS.Timeout | null = null;
  private running = false;
  private consecutiveFailures = 0;
  private state: ServerState = 'unknown';
  private latest: MinecraftStatus | null = null;
  private lastCheckedAt: Date | null = null;
  private readonly startedAt = Date.now();

  constructor(
    minecraft: MinecraftStatusProvider,
    discord: DiscordStatusService,
    config: BotConfig,
    store: StateStore,
  ) {
    this.minecraft = minecraft;
    this.discord = discord;
    this.config = config;
    this.store = store;
    this.state = store.current().lastKnownState;
  }

  get currentState(): ServerState {
    return this.state;
  }

  get latestStatus(): MinecraftStatus | null {
    return this.latest;
  }

  get lastCheck(): Date | null {
    return this.lastCheckedAt;
  }

  get isRunning(): boolean {
    return this.running;
  }

  get uptimeMs(): number {
    return Date.now() - this.startedAt;
  }

  async start(): Promise<void> {
    if (this.running) {
      return;
    }
    this.running = true;
    logger.info(
      `Minecraft status monitoring started (every ${this.config.statusIntervalMs / 1000}s).`,
    );

    await this.check(true);
    this.timer = setInterval(() => {
      // A rejected promise here would become an unhandled rejection and could
      // take the process down, so it is swallowed and logged instead.
      void this.check(false).catch((error) => logger.error('Status check threw', error));
    }, this.config.statusIntervalMs);
  }

  stop(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
    this.running = false;
    logger.info('Minecraft status monitoring stopped.');
  }

  /**
   * Runs one check and updates Discord if warranted.
   *
   * @param force publish even when nothing changed
   */
  async check(force = false): Promise<MinecraftStatus> {
    const status = await this.minecraft.getStatus();
    this.latest = status;
    this.lastCheckedAt = status.checkedAt;

    const previous = this.state;
    let next: ServerState;

    if (status.online) {
      if (this.consecutiveFailures > 0) {
        logger.info('Minecraft server is answering again.');
      }
      this.consecutiveFailures = 0;
      next = 'online';
    } else {
      this.consecutiveFailures++;
      if (this.consecutiveFailures >= this.config.offlineFailureThreshold) {
        next = 'offline';
      } else {
        // Not enough evidence yet. Hold the previous verdict rather than
        // flapping; the embed shows "checking" only if we had no verdict.
        next = previous === 'online' ? 'unknown' : previous;
        logger.warn(
          `Minecraft status check failed (${this.consecutiveFailures}/` +
            `${this.config.offlineFailureThreshold} before marking offline)`,
          status.error,
        );
      }
    }

    this.state = next;
    const changed = previous !== next;

    if (changed) {
      if (next === 'online') {
        logger.info(
          `Minecraft server ONLINE - ${status.playerCount}/${status.maxPlayers} players`,
        );
      } else if (next === 'offline') {
        logger.info('Minecraft server OFFLINE');
      }
      await this.store.patch({
        lastKnownState: next,
        lastCheckedAt: status.checkedAt.toISOString(),
      });
      // Only on a real transition, never on every check.
      if (previous !== 'unknown' || next === 'online') {
        await this.discord.notify(next);
      }
    }

    await this.discord.publish(status, next, force || changed);
    return status;
  }

  /** Fresh query, guaranteed to write to Discord. Used by /refresh-status. */
  async refresh(): Promise<MinecraftStatus> {
    this.discord.invalidate();
    return this.check(true);
  }
}
