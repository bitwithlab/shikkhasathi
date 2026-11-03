import pino from 'pino';
import { env } from '../config/env';

/** Never log OTPs, tokens, passwords or secrets — these paths are redacted everywhere. */
export const REDACT = [
  'req.headers.authorization',
  'req.headers.cookie',
  'res.headers["set-cookie"]',
  '*.password',
  '*.code',
  '*.otp',
  '*.refreshToken',
  '*.accessToken',
  '*.idToken',
  '*.api_key',
  '*.apiKey',
  '*.secret',
];

export const loggerOptions: pino.LoggerOptions = {
  level: env.NODE_ENV === 'test' ? 'silent' : env.LOG_LEVEL,
  redact: { paths: REDACT, censor: '[redacted]' },
  ...(env.NODE_ENV === 'development' ? { transport: { target: 'pino-pretty', options: { colorize: true } } } : {}),
};

export const logger = pino(loggerOptions);
