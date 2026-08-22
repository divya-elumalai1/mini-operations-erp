/** Domain types mirroring backend DTOs. No `any` anywhere — the API contract is typed. */

export type Role = 'ADMIN' | 'OPERATIONS_USER' | 'SALES_USER'

export interface LoginResponse {
  token: string
  tokenType: string
  username: string
  fullName: string
  role: Role
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface FieldViolation {
  field: string
  message: string
}

export interface ApiError {
  timestamp?: string
  status?: number
  error?: string
  message?: string
  path?: string
  details?: FieldViolation[]
}

export interface InventoryRow {
  id: number
  itemId: number
  itemSku: string
  itemName: string
  unitOfMeasure: string
  locationId: number
  locationCode: string
  physicalQty: number
  reservedQty: number
  availableQty: number
  updatedAt: string
}

export type InventoryTxType =
  | 'GOODS_RECEIPT'
  | 'ISSUE'
  | 'RESERVE'
  | 'RELEASE'
  | 'TRANSFER_OUT'
  | 'TRANSFER_IN'
  | 'ADJUSTMENT'

export interface InventoryTransaction {
  id: number
  itemId: number
  itemSku: string
  locationId: number
  locationCode: string
  txType: InventoryTxType
  referenceType: string
  referenceId: number | null
  quantityDeltaPhysical: number
  quantityDeltaReserved: number
  notes: string | null
  performedByUsername: string
  createdAt: string
}

export interface Item {
  id: number
  sku: string
  name: string
  categoryId: number
  categoryName: string
  unitOfMeasure: string
  active: boolean
}

export interface Location {
  id: number
  code: string
  name: string
  active: boolean
}

export interface Category {
  id: number
  name: string
}

export type WorkOrderStatus = 'ASSIGNED' | 'IN_PROGRESS' | 'COMPLETED'

export interface WorkOrder {
  id: number
  woNumber: string
  itemId: number
  itemSku: string
  itemName: string
  locationId: number
  locationCode: string
  requiredQuantity: number
  assignedUserId: number
  assignedUsername: string
  status: WorkOrderStatus
  completedAt: string | null
  createdByUsername: string
  createdAt: string
}

export interface MaterialAvailability {
  workOrderId: number
  woNumber: string
  itemId: number
  itemSku: string
  locationId: number
  locationCode: string
  requiredQuantity: number
  availableQuantity: number
  shortageQuantity: number
  sufficient: boolean
}

export type TransferStatus = 'REQUESTED' | 'DISPATCHED' | 'RECEIVED'

export interface Transfer {
  id: number
  transferNumber: string
  itemId: number
  itemSku: string
  quantity: number
  sourceLocationId: number
  sourceLocationCode: string
  destinationLocationId: number
  destinationLocationCode: string
  status: TransferStatus
  dispatchedByUsername: string | null
  dispatchedAt: string | null
  receivedByUsername: string | null
  receivedAt: string | null
  createdByUsername: string
  createdAt: string
}

export interface OrderLine {
  id: number
  itemId: number
  itemSku: string
  locationId: number
  locationCode: string
  quantity: number
}

export interface Order {
  id: number
  orderNumber: string
  customerName: string
  status: 'CREATED' | 'CANCELLED'
  lines: OrderLine[]
  createdByUsername: string
  createdAt: string
}
