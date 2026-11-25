import coreHttp from "@/api/coreHttp";import type{ControlDashboard,RoutingRule,WorkerGroup}from"@/types/runControl";
export async function fetchControlDashboard():Promise<ControlDashboard>{return(await coreHttp.get("/agent-runs/control-plane")).data.data}
export async function fetchRoutingRules():Promise<RoutingRule[]>{return(await coreHttp.get("/agent-runs/control-plane/rules")).data.data}
export async function saveWorkerGroup(id:number|null,payload:any):Promise<void>{if(id)await coreHttp.put(`/agent-runs/control-plane/groups/${id}`,payload);else await coreHttp.post("/agent-runs/control-plane/groups",payload)}
export async function saveRoutingRule(id:number|null,payload:any):Promise<void>{if(id)await coreHttp.put(`/agent-runs/control-plane/rules/${id}`,payload);else await coreHttp.post("/agent-runs/control-plane/rules",payload)}
export async function setWorkerStatus(workerId:string,status:string):Promise<void>{await coreHttp.put(`/agent-runs/control-plane/nodes/${encodeURIComponent(workerId)}/status`,{status})}
