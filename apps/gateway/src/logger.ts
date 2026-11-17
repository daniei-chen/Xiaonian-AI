import { pino, type Logger } from 'pino'

/**
 * 日志脱敏：任何路径下都不允许出现完整密钥。
 * spec §17 明确禁止记录完整 API Key / 授权令牌。
 */
const REDACT_PATHS = [
  'req.headers.authorization',
  'req.headers["x-api-key"]',
  'req.headers.cookie',
  'res.headers["set-cookie"]',
  '*.apiKey',
  '*.api_key',
  '*.authorization',
  '*.password',
  '*.token',
]

export const createLogger = (level: string): Logger =>
  pino({
    level,
    redact: { paths: REDACT_PATHS, censor: '[redacted]' },
    base: { service: 'xiaonian-gateway' },
    timestamp: pino.stdTimeFunctions.isoTime,
  })
