const LEVELS = ['debug', 'info', 'warn', 'error'] as const;
export type LogLevel = (typeof LEVELS)[number];

let threshold = 1; // info

/**
 * Anything that looks like a bot token is masked before it reaches the console.
 *
 * Tokens end up in log lines by accident more often than people expect - a
 * thrown Discord error can carry the request that produced it. Scrubbing at the
 * sink means no individual log call has to remember.
 */
const TOKEN_PATTERN = /[\w-]{24,28}\.[\w-]{6}\.[\w-]{27,40}/g;

function scrub(value: string): string {
  return value.replace(TOKEN_PATTERN, '***TOKEN REDACTED***');
}

function format(level: LogLevel, message: string): string {
  const time = new Date().toISOString().replace('T', ' ').slice(0, 19);
  return `${time} [${level.toUpperCase()}] ${scrub(message)}`;
}

function describe(detail: unknown): string {
  if (detail === undefined || detail === null) {
    return '';
  }
  if (detail instanceof Error) {
    return ` - ${detail.name}: ${detail.message}`;
  }
  if (typeof detail === 'string') {
    return ` - ${detail}`;
  }
  try {
    return ` - ${JSON.stringify(detail)}`;
  } catch {
    return ` - ${String(detail)}`;
  }
}

export const logger = {
  setLevel(level: string): void {
    const index = LEVELS.indexOf(level as LogLevel);
    threshold = index === -1 ? 1 : index;
  },

  debug(message: string, detail?: unknown): void {
    if (threshold <= 0) {
      console.log(format('debug', message + describe(detail)));
    }
  },

  info(message: string, detail?: unknown): void {
    if (threshold <= 1) {
      console.log(format('info', message + describe(detail)));
    }
  },

  warn(message: string, detail?: unknown): void {
    if (threshold <= 2) {
      console.warn(format('warn', message + describe(detail)));
    }
  },

  error(message: string, detail?: unknown): void {
    if (threshold <= 3) {
      console.error(format('error', message + describe(detail)));
    }
  },
};
