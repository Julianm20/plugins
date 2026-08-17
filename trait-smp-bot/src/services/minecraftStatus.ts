import { status } from 'minecraft-server-util';

import type { MinecraftConfig } from '../utils/config';
import { logger } from '../utils/logger';

/** One point-in-time reading of the Minecraft server. */
export interface MinecraftStatus {
  online: boolean;
  playerCount: number;
  maxPlayers: number;
  version: string | null;
  motd: string | null;
  latencyMs: number | null;
  checkedAt: Date;
  /** Populated when the query failed, for logging. Never shown to players. */
  error?: string;
}

/**
 * The seam between the bot and the Minecraft protocol.
 *
 * Nothing outside this file knows how a server is queried. Swapping the library,
 * adding Bedrock support or talking to a plugin's HTTP endpoint later means
 * writing another implementation of this interface and changing one line in index.ts.
 */
export interface MinecraftStatusProvider {
  getStatus(): Promise<MinecraftStatus>;
  isOnline(): Promise<boolean>;
  getPlayerCount(): Promise<number>;
  getMaxPlayers(): Promise<number>;
  getVersion(): Promise<string | null>;
  getMotd(): Promise<string | null>;
}

export class MinecraftStatusService implements MinecraftStatusProvider {
  private readonly config: MinecraftConfig;
  private readonly fallbackMaxPlayers: number;

  constructor(config: MinecraftConfig, fallbackMaxPlayers: number) {
    this.config = config;
    this.fallbackMaxPlayers = fallbackMaxPlayers;
  }

  get address(): string {
    return this.config.port === 25565
      ? this.config.host
      : `${this.config.host}:${this.config.port}`;
  }

  /**
   * Queries the server. Never throws: an unreachable server is a normal
   * outcome, not an error condition, so it comes back as {@code online: false}.
   */
  async getStatus(): Promise<MinecraftStatus> {
    const checkedAt = new Date();
    try {
      const response = await status(this.config.host, this.config.port, {
        timeout: this.config.timeoutMs,
        enableSRV: true,
      });

      return {
        online: true,
        playerCount: response.players.online ?? 0,
        maxPlayers: response.players.max ?? this.fallbackMaxPlayers,
        version: response.version?.name ?? null,
        motd: response.motd?.clean?.trim() || null,
        latencyMs: response.roundTripLatency ?? null,
        checkedAt,
      };
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : String(error);
      logger.debug(`Minecraft query failed for ${this.address}`, message);
      return {
        online: false,
        playerCount: 0,
        maxPlayers: this.fallbackMaxPlayers,
        version: null,
        motd: null,
        latencyMs: null,
        checkedAt,
        error: message,
      };
    }
  }

  async isOnline(): Promise<boolean> {
    return (await this.getStatus()).online;
  }

  async getPlayerCount(): Promise<number> {
    return (await this.getStatus()).playerCount;
  }

  async getMaxPlayers(): Promise<number> {
    return (await this.getStatus()).maxPlayers;
  }

  async getVersion(): Promise<string | null> {
    return (await this.getStatus()).version;
  }

  async getMotd(): Promise<string | null> {
    return (await this.getStatus()).motd;
  }
}
