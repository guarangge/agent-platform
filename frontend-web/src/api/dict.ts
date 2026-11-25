import http from "@/api/http";

export interface DictTypeQueryParams {
  pageNum: number;
  pageSize: number;
  typeCode?: string;
  typeName?: string;
  status?: string;
}

export interface DictItemQueryParams {
  pageNum: number;
  pageSize: number;
  itemCode?: string;
  itemName?: string;
  status?: string;
}

export interface DictTypeRecord {
  dictTypeId: number;
  typeCode: string;
  typeName: string;
  status: string;
  builtIn: boolean;
  description: string | null;
  createdAt: string;
}

export interface DictTypeDetail {
  dictTypeId: number;
  typeCode: string;
  typeName: string;
  status: string;
  builtIn: boolean;
  description: string | null;
  remark: string | null;
  createdAt: string;
  updatedAt: string;
  createdBy: number | null;
  updatedBy: number | null;
}

export interface DictItemRecord {
  dictItemId: number;
  dictTypeId: number;
  typeCode: string;
  itemCode: string;
  itemName: string;
  sortOrder: number;
  status: string;
  isDefault: boolean;
  description: string | null;
  createdAt: string;
}

export interface DictItemDetail {
  dictItemId: number;
  dictTypeId: number;
  typeCode: string;
  itemCode: string;
  itemName: string;
  sortOrder: number;
  status: string;
  isDefault: boolean;
  description: string | null;
  remark: string | null;
  createdAt: string;
  updatedAt: string;
  createdBy: number | null;
  updatedBy: number | null;
}

export interface DictOptionItem {
  itemCode: string;
  itemName: string;
  description: string | null;
}

export interface CreateDictTypePayload {
  typeCode: string;
  typeName: string;
  status: string;
  builtIn: boolean;
  description: string;
  remark: string;
}

export interface UpdateDictTypePayload {
  typeCode: string;
  typeName: string;
  status: string;
  builtIn: boolean;
  description: string;
  remark: string;
}

export interface CreateDictItemPayload {
  itemCode: string;
  itemName: string;
  sortOrder: number;
  status: string;
  isDefault: boolean;
  description: string;
  remark: string;
}

export interface UpdateDictItemPayload {
  itemCode: string;
  itemName: string;
  sortOrder: number;
  status: string;
  isDefault: boolean;
  description: string;
  remark: string;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  pageNum: number;
  pageSize: number;
}

interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export async function fetchDictTypePage(params: DictTypeQueryParams): Promise<PageResult<DictTypeRecord>> {
  const response = await http.get<ApiResponse<PageResult<DictTypeRecord>>>("/dict-types", { params });
  return response.data.data;
}

export async function fetchDictTypeDetail(dictTypeId: number): Promise<DictTypeDetail> {
  const response = await http.get<ApiResponse<DictTypeDetail>>(`/dict-types/${dictTypeId}`);
  return response.data.data;
}

export async function createDictType(payload: CreateDictTypePayload): Promise<DictTypeDetail> {
  const response = await http.post<ApiResponse<DictTypeDetail>>("/dict-types", payload);
  return response.data.data;
}

export async function updateDictType(dictTypeId: number, payload: UpdateDictTypePayload): Promise<DictTypeDetail> {
  const response = await http.put<ApiResponse<DictTypeDetail>>(`/dict-types/${dictTypeId}`, payload);
  return response.data.data;
}

export async function enableDictType(dictTypeId: number): Promise<void> {
  await http.put<ApiResponse<null>>(`/dict-types/${dictTypeId}/enable`);
}

export async function disableDictType(dictTypeId: number): Promise<void> {
  await http.put<ApiResponse<null>>(`/dict-types/${dictTypeId}/disable`);
}

export async function fetchDictItemPage(dictTypeId: number, params: DictItemQueryParams): Promise<PageResult<DictItemRecord>> {
  const response = await http.get<ApiResponse<PageResult<DictItemRecord>>>(`/dict-types/${dictTypeId}/items`, { params });
  return response.data.data;
}

export async function fetchDictItemDetail(dictItemId: number): Promise<DictItemDetail> {
  const response = await http.get<ApiResponse<DictItemDetail>>(`/dict-items/${dictItemId}`);
  return response.data.data;
}

export async function createDictItem(dictTypeId: number, payload: CreateDictItemPayload): Promise<DictItemDetail> {
  const response = await http.post<ApiResponse<DictItemDetail>>(`/dict-types/${dictTypeId}/items`, payload);
  return response.data.data;
}

export async function updateDictItem(dictItemId: number, payload: UpdateDictItemPayload): Promise<DictItemDetail> {
  const response = await http.put<ApiResponse<DictItemDetail>>(`/dict-items/${dictItemId}`, payload);
  return response.data.data;
}

export async function enableDictItem(dictItemId: number): Promise<void> {
  await http.put<ApiResponse<null>>(`/dict-items/${dictItemId}/enable`);
}

export async function disableDictItem(dictItemId: number): Promise<void> {
  await http.put<ApiResponse<null>>(`/dict-items/${dictItemId}/disable`);
}

export async function fetchDictOptions(typeCodes: string[]): Promise<Record<string, DictOptionItem[]>> {
  const params = new URLSearchParams();
  typeCodes.forEach((typeCode) => {
    params.append("typeCodes", typeCode);
  });
  const response = await http.get<ApiResponse<Record<string, DictOptionItem[]>>>("/dicts/options", {
    params
  });
  return response.data.data;
}
