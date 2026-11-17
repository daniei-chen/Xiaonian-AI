import { z } from 'zod'

/**
 * 统一错误码。上游（provider）与本地产生的错误都归一到这里。
 * retryable 决定调用方能否直接重试；不可重试的必须换路或让用户介入。
 */
export const ERROR_CODES = {
  bad_request: { retryable: false },
  unauthorized: { retryable: false },
  forbidden: { retryable: false },
  not_found: { retryable: false },
  conflict: { retryable: false },
  rate_limited: { retryable: true },
  internal: { retryable: false },

  /** 401 —— key 失效 */
  upstream_auth: { retryable: false },
  /** 402 —— 余额不足 */
  upstream_balance: { retryable: false },
  /** 408 / 504 —— 上传或识别超时 */
  upstream_timeout: { retryable: true },
  /** 413 —— 文件超限，必须换小文件 */
  upstream_too_large: { retryable: false },
  /** 422 —— 参数无效，重试无意义 */
  upstream_invalid: { retryable: false },
  /** 429 / 503 —— 限流或过载，退避后重试 */
  upstream_overloaded: { retryable: true },
  /** 500 —— 上游内部错误 */
  upstream_error: { retryable: true },

  /** 响应不符合协议，属于我方适配缺陷 */
  protocol_violation: { retryable: false },
  /** 流在完成事件之前断掉，应引导「继续生成」而非重试 */
  stream_interrupted: { retryable: false },
  network: { retryable: true },
  /** 内容安全拒答 */
  content_refused: { retryable: false },
} as const

export type ErrorCode = keyof typeof ERROR_CODES

export const ErrorCodeSchema = z.enum(
  Object.keys(ERROR_CODES) as [ErrorCode, ...ErrorCode[]],
)

/** 所有 HTTP 接口的统一错误结构 */
export const ApiErrorBodySchema = z.object({
  code: ErrorCodeSchema,
  message: z.string(),
  retryable: z.boolean(),
  traceId: z.string(),
  details: z.record(z.string(), z.unknown()).optional(),
})

export type ApiErrorBody = z.infer<typeof ApiErrorBodySchema>

export const isRetryable = (code: ErrorCode): boolean => ERROR_CODES[code].retryable

/** 上游 HTTP 状态码 → 统一错误码 */
export const mapUpstreamStatus = (status: number): ErrorCode => {
  switch (status) {
    case 400:
      return 'bad_request'
    case 401:
      return 'upstream_auth'
    case 402:
      return 'upstream_balance'
    case 408:
    case 504:
      return 'upstream_timeout'
    case 413:
      return 'upstream_too_large'
    case 422:
      return 'upstream_invalid'
    case 429:
    case 503:
      return 'upstream_overloaded'
    default:
      return status >= 500 ? 'upstream_error' : 'bad_request'
  }
}

export const apiError = (
  code: ErrorCode,
  message: string,
  traceId: string,
  details?: Record<string, unknown>,
): ApiErrorBody => ({
  code,
  message,
  retryable: isRetryable(code),
  traceId,
  ...(details ? { details } : {}),
})
