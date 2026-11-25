export type RuntimeEventProtocolMode = "auto" | "unified" | "legacy";

export interface RuntimeEventRolloutConfig {
  mode?: string;
  agentIds?: string;
  rolloutPercent?: string | number;
}

/**
 * 根据全局模式、Agent 白名单和稳定百分比决定会话读取协议。
 * auto 表示优先统一协议且失败自动回退，legacy 可作为即时回滚开关。
 */
export function resolveRuntimeEventProtocolMode(
  agentId: number | null | undefined,
  config: RuntimeEventRolloutConfig = runtimeEventRolloutConfig()
): RuntimeEventProtocolMode {
  const mode = normalizeMode(config.mode);
  if (mode !== "auto") return mode;
  const allowlist = parseAgentIds(config.agentIds);
  if (allowlist.size > 0) return agentId && allowlist.has(agentId) ? "auto" : "legacy";
  const rolloutPercent = normalizeRolloutPercent(config.rolloutPercent);
  if (rolloutPercent >= 100) return "auto";
  if (rolloutPercent <= 0 || !agentId) return "legacy";
  return stableBucket(agentId) < rolloutPercent ? "auto" : "legacy";
}

function runtimeEventRolloutConfig(): RuntimeEventRolloutConfig {
  return {
    mode: import.meta.env.VITE_RUNTIME_EVENT_PROTOCOL_MODE,
    agentIds: import.meta.env.VITE_RUNTIME_EVENT_PROTOCOL_AGENT_IDS,
    rolloutPercent: import.meta.env.VITE_RUNTIME_EVENT_PROTOCOL_ROLLOUT_PERCENT
  };
}

function normalizeMode(value: unknown): RuntimeEventProtocolMode {
  return value === "unified" || value === "legacy" || value === "auto" ? value : "auto";
}

function parseAgentIds(value: unknown): Set<number> {
  if (typeof value !== "string" || !value.trim()) return new Set();
  return new Set(value.split(",")
    .map((item) => Number(item.trim()))
    .filter((item) => Number.isInteger(item) && item > 0));
}

function normalizeRolloutPercent(value: unknown): number {
  const parsed = Number(value ?? 100);
  if (!Number.isFinite(parsed)) return 100;
  return Math.max(0, Math.min(100, Math.floor(parsed)));
}

function stableBucket(agentId: number): number {
  return Math.abs(Math.imul(agentId, 2654435761)) % 100;
}
