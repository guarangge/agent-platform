const DISPLAY_TIME_ZONE = "Asia/Shanghai";
const DATE_TIME_FORMATTER = new Intl.DateTimeFormat("zh-CN", {
  timeZone: DISPLAY_TIME_ZONE,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hourCycle: "h23"
});

/** 后端无时区时间按 UTC 存储；页面统一转换为 Asia/Shanghai 展示。 */
export function formatDateTime(value: unknown, emptyText = "-"): string {
  if (value === null || value === undefined || value === "") return emptyText;
  const date = parseBackendDateTime(value);
  if (!date) return String(value).trim();
  const parts = Object.fromEntries(
    DATE_TIME_FORMATTER.formatToParts(date)
      .filter((part) => part.type !== "literal")
      .map((part) => [part.type, part.value])
  );
  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}`;
}

/** Element Plus 表格时间列格式化器。 */
export function formatTableDateTime(_row: unknown, _column: unknown, cellValue: unknown): string {
  return formatDateTime(cellValue);
}

/** 将 Asia/Shanghai 的页面时间转换为后端无时区 UTC 时间。 */
export function toBackendUtcDateTime(value: unknown): string | undefined {
  if (value === null || value === undefined || value === "") return undefined;
  const text = String(value).trim();
  const matched = text.match(/^(\d{4}-\d{2}-\d{2})[T\s](\d{2}:\d{2}:\d{2})$/);
  if (!matched) return undefined;
  const date = new Date(`${matched[1]}T${matched[2]}+08:00`);
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString().slice(0, 19);
}

function parseBackendDateTime(value: unknown): Date | null {
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value;
  if (typeof value === "number") {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? null : date;
  }
  const text = String(value).trim();
  const isoMatch = text.match(/^(\d{4}-\d{2}-\d{2})[T\s](\d{2}:\d{2}:\d{2}(?:\.\d+)?)(.*)$/);
  if (!isoMatch) return null;
  const suffix = isoMatch[3].trim();
  const hasExplicitZone = /^(?:Z|[+-]\d{2}(?::?\d{2})?)$/i.test(suffix);
  const normalized = `${isoMatch[1]}T${isoMatch[2]}${hasExplicitZone ? suffix : "Z"}`;
  const date = new Date(normalized);
  return Number.isNaN(date.getTime()) ? null : date;
}
