import coreHttp from "@/api/coreHttp";
import type {
  ExecuteSandboxPayload,
  PageResponse,
  SandboxExecution,
  SandboxExecutionResult,
  SandboxProfile,
  SandboxProfilePayload
} from "@/types/sandbox";

export async function fetchSandboxProfiles(params: Record<string, unknown>): Promise<PageResponse<SandboxProfile>> {
  const response = await coreHttp.get("/sandboxes/profiles", { params });
  return response.data.data;
}

export async function createSandboxProfile(payload: SandboxProfilePayload): Promise<SandboxProfile> {
  const response = await coreHttp.post("/sandboxes/profiles", payload);
  return response.data.data;
}

export async function updateSandboxProfile(id: number, payload: SandboxProfilePayload): Promise<SandboxProfile> {
  const response = await coreHttp.put(`/sandboxes/profiles/${id}`, payload);
  return response.data.data;
}

export async function fetchSandboxExecutions(params: Record<string, unknown>): Promise<PageResponse<SandboxExecution>> {
  const response = await coreHttp.get("/sandboxes/executions", { params });
  return response.data.data;
}

export async function fetchSandboxExecution(id: number): Promise<SandboxExecution> {
  const response = await coreHttp.get(`/sandboxes/executions/${id}`);
  return response.data.data;
}

export async function executeSandbox(payload: ExecuteSandboxPayload): Promise<SandboxExecutionResult> {
  const response = await coreHttp.post("/sandboxes/executions", payload, { timeout: 3_660_000 });
  return response.data.data;
}

export async function cancelSandboxExecution(id: number): Promise<SandboxExecution> {
  const response = await coreHttp.post(`/sandboxes/executions/${id}/cancel`);
  return response.data.data;
}
