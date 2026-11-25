/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_RUNTIME_EVENT_PROTOCOL_MODE?: "auto" | "unified" | "legacy";
  readonly VITE_RUNTIME_EVENT_PROTOCOL_AGENT_IDS?: string;
  readonly VITE_RUNTIME_EVENT_PROTOCOL_ROLLOUT_PERCENT?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
