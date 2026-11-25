import coreHttp from "@/api/coreHttp";
import type { BudgetAlert, BudgetPayload, BudgetPolicy, CostDashboard, PricePayload, ResourcePrice, UsageEvent } from "@/types/costGovernance";

export async function fetchCostDashboard():Promise<CostDashboard>{return (await coreHttp.get("/cost-governance/dashboard")).data.data}
export async function fetchUsage(params:Record<string,unknown>):Promise<UsageEvent[]>{return (await coreHttp.get("/cost-governance/usage",{params})).data.data}
export async function fetchPrices():Promise<ResourcePrice[]>{return (await coreHttp.get("/cost-governance/prices")).data.data}
export async function createPrice(payload:PricePayload):Promise<ResourcePrice>{return (await coreHttp.post("/cost-governance/prices",payload)).data.data}
export async function updatePrice(id:number,payload:PricePayload):Promise<ResourcePrice>{return (await coreHttp.put(`/cost-governance/prices/${id}`,payload)).data.data}
export async function fetchBudgets():Promise<BudgetPolicy[]>{return (await coreHttp.get("/cost-governance/budgets")).data.data}
export async function createBudget(payload:BudgetPayload):Promise<BudgetPolicy>{return (await coreHttp.post("/cost-governance/budgets",payload)).data.data}
export async function updateBudget(id:number,payload:BudgetPayload):Promise<BudgetPolicy>{return (await coreHttp.put(`/cost-governance/budgets/${id}`,payload)).data.data}
export async function fetchBudgetAlerts(status=""):Promise<BudgetAlert[]>{return (await coreHttp.get("/cost-governance/alerts",{params:{status}})).data.data}
export async function updateBudgetAlert(id:number,status:string):Promise<void>{await coreHttp.put(`/cost-governance/alerts/${id}/status`,{status})}
