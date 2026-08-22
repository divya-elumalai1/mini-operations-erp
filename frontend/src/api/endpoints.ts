import type {
  Category,
  InventoryRow,
  InventoryTransaction,
  Item,
  Location,
  LoginResponse,
  MaterialAvailability,
  Order,
  PageResponse,
  Transfer,
  WorkOrder,
} from '../types/api'
import { api } from './client'

export { extractApiError } from './client'

// ---------- auth ----------
export async function login(username: string, password: string): Promise<LoginResponse> {
  const { data } = await api.post<LoginResponse>('/auth/login', { username, password })
  return data
}

// ---------- master data ----------
export async function fetchItems(): Promise<Item[]> {
  const { data } = await api.get<Item[]>('/items')
  return data
}

export async function fetchLocations(): Promise<Location[]> {
  const { data } = await api.get<Location[]>('/locations')
  return data
}

export async function fetchCategories(): Promise<Category[]> {
  const { data } = await api.get<Category[]>('/categories')
  return data
}

// ---------- inventory ----------
export interface InventoryQuery {
  itemId?: number
  locationId?: number
  page?: number
}

export async function fetchInventory(query: InventoryQuery): Promise<PageResponse<InventoryRow>> {
  const params = new URLSearchParams()
  if (query.itemId != null) params.set('itemId', String(query.itemId))
  if (query.locationId != null) params.set('locationId', String(query.locationId))
  params.set('page', String(query.page ?? 0))
  const { data } = await api.get<PageResponse<InventoryRow>>(`/inventory?${params}`)
  return data
}

export async function fetchTransactions(itemId?: number): Promise<PageResponse<InventoryTransaction>> {
  const suffix = itemId != null ? `?itemId=${itemId}` : ''
  const { data } = await api.get<PageResponse<InventoryTransaction>>(`/inventory/transactions${suffix}`)
  return data
}

export interface ReceiptInput {
  itemId: number
  locationId: number
  quantity: number
  batchCode?: string
  notes?: string
  idempotencyKey?: string
}

export async function createReceipt(input: ReceiptInput): Promise<InventoryRow> {
  const { data } = await api.post<InventoryRow>('/inventory/receipts', input)
  return data
}

// ---------- work orders ----------
export async function fetchWorkOrders(status?: string): Promise<PageResponse<WorkOrder>> {
  const suffix = status ? `?status=${status}` : ''
  const { data } = await api.get<PageResponse<WorkOrder>>(`/work-orders${suffix}`)
  return data
}

export interface WorkOrderInput {
  itemId: number
  locationId: number
  assignedUserId: number
  requiredQuantity: number
}

export async function createWorkOrder(input: WorkOrderInput): Promise<WorkOrder> {
  const { data } = await api.post<WorkOrder>('/work-orders', input)
  return data
}

export async function changeWorkOrderStatus(id: number, status: WorkOrder['status']): Promise<WorkOrder> {
  const { data } = await api.patch<WorkOrder>(`/work-orders/${id}/status`, { status })
  return data
}

export async function fetchMaterialAvailability(id: number): Promise<MaterialAvailability> {
  const { data } = await api.get<MaterialAvailability>(`/work-orders/${id}/material-availability`)
  return data
}

// ---------- transfers ----------
export async function fetchTransfers(status?: string): Promise<PageResponse<Transfer>> {
  const suffix = status ? `?status=${status}` : ''
  const { data } = await api.get<PageResponse<Transfer>>(`/transfers${suffix}`)
  return data
}

export interface TransferInput {
  sourceLocationId: number
  destinationLocationId: number
  itemId: number
  quantity: number
}

export async function createTransfer(input: TransferInput): Promise<Transfer> {
  const { data } = await api.post<Transfer>('/transfers', input)
  return data
}

export async function dispatchTransfer(id: number): Promise<Transfer> {
  const { data } = await api.post<Transfer>(`/transfers/${id}/dispatch`)
  return data
}

export async function receiveTransfer(id: number): Promise<Transfer> {
  const { data } = await api.post<Transfer>(`/transfers/${id}/receive`)
  return data
}

// ---------- orders ----------
export async function fetchOrders(): Promise<PageResponse<Order>> {
  const { data } = await api.get<PageResponse<Order>>('/orders')
  return data
}

export interface OrderLineInput {
  itemId: number
  locationId: number
  quantity: number
}

export interface OrderInput {
  customerName: string
  lines: OrderLineInput[]
}

export async function createOrder(input: OrderInput): Promise<Order> {
  const { data } = await api.post<Order>('/orders', input)
  return data
}
