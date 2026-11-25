export interface PageResponse<T> {
  records: T[];
  total: number;
  pageNum: number;
  pageSize: number;
}

export interface SandboxProfile {
  profileId: number;
  profileCode: string;
  profileName: string;
  image: string;
  allowedCommands: string[];
  networkMode: "none" | "bridge";
  memoryBytes: number;
  nanoCpus: number;
  pidsLimit: number;
  timeoutSeconds: number;
  maxOutputBytes: number;
  maxWorkspaceBytes: number;
  status: "enabled" | "disabled";
  createdAt: string;
  updatedAt: string;
}

export type SandboxProfilePayload = Omit<SandboxProfile, "profileId" | "createdAt" | "updatedAt">;

export interface SandboxExecution {
  executionId: number;
  executionCode: string;
  profileId: number;
  profileCode: string;
  image: string;
  command: string[];
  status: "pending" | "running" | "succeeded" | "failed" | "timed_out" | "cancelled";
  containerId: string | null;
  exitCode: number | null;
  timedOut: boolean;
  outputTruncated: boolean;
  stdoutText: string | null;
  stderrText: string | null;
  errorMessage: string | null;
  inputBytes: number;
  outputBytes: number;
  workspaceBytes: number;
  durationMs: number | null;
  requestedBy: number | null;
  requestedUsername: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  createdAt: string;
}

export interface SandboxArtifact {
  path: string;
  size: number;
  contentBase64: string;
}

export interface SandboxExecutionResult {
  execution: SandboxExecution;
  artifacts: SandboxArtifact[];
}

export interface ExecuteSandboxPayload {
  profileId: number;
  command: string[];
  inputFiles: Array<{ path: string; contentBase64: string }>;
  artifactPaths: string[];
}
