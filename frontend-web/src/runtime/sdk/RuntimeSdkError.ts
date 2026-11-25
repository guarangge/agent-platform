/** Runtime SDK 统一错误，调用方不需要识别 fetch 或 Controller 的异常结构。 */
export class RuntimeSdkError extends Error {
  readonly code: string;
  readonly httpStatus: number | null;
  readonly originalCause?: unknown;

  constructor(message: string, code: string, httpStatus: number | null = null, cause?: unknown) {
    super(message);
    this.name = "RuntimeSdkError";
    this.code = code;
    this.httpStatus = httpStatus;
    this.originalCause = cause;
  }
}
