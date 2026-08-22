import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import {
  changeWorkOrderStatus,
  createWorkOrder,
  extractApiError,
  fetchItems,
  fetchLocations,
  fetchMaterialAvailability,
  fetchWorkOrders,
} from '../api/endpoints'
import type { MaterialAvailability, WorkOrder } from '../types/api'
import { useAuth } from '../lib/AuthContext'
import { Alert, Card, EmptyState, ErrorState, Spinner, StatusPill } from '../components/ui'
import { Modal } from '../components/Modal'
import { formatMutationError } from './InventoryPage'

const workOrderSchema = z.object({
  itemId: z.coerce.number().int().positive('Select an item'),
  locationId: z.coerce.number().int().positive('Select a location'),
  assignedUserId: z.coerce.number().int().positive('Assign an operations user'),
  requiredQuantity: z.coerce.number().positive('Quantity must be greater than 0'),
})

type WorkOrderForm = z.infer<typeof workOrderSchema>

const STATUS_FILTERS = ['', 'ASSIGNED', 'IN_PROGRESS', 'COMPLETED'] as const

export function WorkOrdersPage() {
  const { hasAnyRole } = useAuth()
  const isAdmin = hasAnyRole('ADMIN')
  const canProgress = hasAnyRole('ADMIN', 'OPERATIONS_USER')
  const [statusFilter, setStatusFilter] = useState<string>('')

  const workOrders = useQuery({
    queryKey: ['work-orders', statusFilter],
    queryFn: () => fetchWorkOrders(statusFilter || undefined),
  })
  const items = useQuery({ queryKey: ['items'], queryFn: fetchItems })
  const locations = useQuery({ queryKey: ['locations'], queryFn: fetchLocations })

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Work Orders</h1>
          <p className="subtitle">Shortage is calculated by the backend from live stock positions.</p>
        </div>
        {isAdmin && <CreateWorkOrderButton />}
      </div>

      <Card>
        <div className="toolbar">
          <select className="input" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} aria-label="Filter by status">
            {STATUS_FILTERS.map((s) => (
              <option key={s} value={s}>{s === '' ? 'All statuses' : s.replace(/_/g, ' ')}</option>
            ))}
          </select>
        </div>

        {workOrders.isLoading && <Spinner label="Loading work orders…" />}
        {workOrders.isError && <ErrorState message={extractApiError(workOrders.error).message} />}
        {workOrders.data &&
          (workOrders.data.content.length === 0 ? (
            <EmptyState text="No work orders found." />
          ) : (
            <table className="data-table">
              <thead>
                <tr>
                  <th>Number</th>
                  <th>Item</th>
                  <th>Location</th>
                  <th className="num">Required</th>
                  <th>Assigned to</th>
                  <th>Status</th>
                  <th style={{ textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {workOrders.data.content.map((wo) => (
                  <WorkOrderRow key={wo.id} wo={wo} canProgress={canProgress} />
                ))}
              </tbody>
            </table>
          ))}
        {(items.isError || locations.isError) && (
          <Alert kind="info">Master data failed to load — creation forms may be unavailable.</Alert>
        )}
      </Card>
    </>
  )
}

function WorkOrderRow({ wo, canProgress }: { wo: WorkOrder; canProgress: boolean }) {
  const [availability, setAvailability] = useState<MaterialAvailability | null>(null)
  const queryClient = useQueryClient()

  const statusMutation = useMutation({
    mutationFn: (target: WorkOrder['status']) => changeWorkOrderStatus(wo.id, target),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['work-orders'] }),
  })

  const availabilityQuery = useQuery({
    queryKey: ['material-availability', wo.id],
    queryFn: () => fetchMaterialAvailability(wo.id),
    enabled: false,
  })

  const loadAvailability = async () => {
    if (availability) {
      setAvailability(null)
      return
    }
    try {
      setAvailability(await availabilityQuery.refetch().then((r) => r.data ?? null))
    } catch {
      setAvailability(null)
    }
  }

  return (
    <>
      <tr>
        <td>{wo.woNumber}</td>
        <td>{wo.itemSku} — {wo.itemName}</td>
        <td>{wo.locationCode}</td>
        <td className="num">{wo.requiredQuantity}</td>
        <td>{wo.assignedUsername}</td>
        <td><StatusPill value={wo.status} /></td>
        <td className="num">
          <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
            <button type="button" className="btn btn-secondary btn-sm" onClick={() => void loadAvailability()}>
              {availability ? 'Hide shortage' : 'Shortage'}
            </button>
            {canProgress && wo.status === 'ASSIGNED' && (
              <button
                type="button"
                className="btn btn-sm"
                disabled={statusMutation.isPending}
                onClick={() => statusMutation.mutate('IN_PROGRESS')}
              >
                Start
              </button>
            )}
            {canProgress && wo.status === 'IN_PROGRESS' && (
              <button
                type="button"
                className="btn btn-success btn-sm"
                disabled={statusMutation.isPending}
                onClick={() => statusMutation.mutate('COMPLETED')}
              >
                Complete
              </button>
            )}
          </div>
          {statusMutation.isError && <span className="field-error">{formatMutationError(statusMutation.error)}</span>}
        </td>
      </tr>
      {availability && (
        <tr>
          <td colSpan={7}>
            <dl className="kv-list">
              <dt>Required</dt><dd>{availability.requiredQuantity}</dd>
              <dt>Available at {availability.locationCode}</dt><dd>{availability.availableQuantity}</dd>
              <dt>Shortage</dt>
              <dd style={{ color: availability.sufficient ? 'var(--success)' : 'var(--danger)' }}>
                {availability.shortageQuantity} {availability.sufficient ? '(sufficient)' : '(SHORT)'}
              </dd>
            </dl>
            {!availability.sufficient && (
              <p style={{ margin: '10px 0 0', color: 'var(--muted)' }}>
                Tip: raise an internal transfer to cover the shortage before starting production.
              </p>
            )}
          </td>
        </tr>
      )}
    </>
  )
}

function CreateWorkOrderButton() {
  const [open, setOpen] = useState(false)

  return (
    <>
      <button type="button" className="btn" onClick={() => setOpen(true)}>New work order</button>
      <CreateWorkOrderModal open={open} onClose={() => setOpen(false)} />
    </>
  )
}

function CreateWorkOrderModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient()
  const items = useQuery({ queryKey: ['items'], queryFn: fetchItems })
  const locations = useQuery({ queryKey: ['locations'], queryFn: fetchLocations })

  // Operations users are valid assignees; the backend enforces this rule authoritatively.
  const opsUsers = [
    { id: 2, username: 'ops', fullName: 'Om Prakash (OPERATIONS_USER)' },
  ]

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<z.input<typeof workOrderSchema>, unknown, WorkOrderForm>({ resolver: zodResolver(workOrderSchema) })

  const mutation = useMutation({
    mutationFn: createWorkOrder,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['work-orders'] })
      reset()
      onClose()
    },
  })

  const submit = handleSubmit((values) => mutation.mutate(values))

  return (
    <Modal title="Create work order" open={open} onClose={onClose}>
      {mutation.isError && <Alert kind="error">{formatMutationError(mutation.error)}</Alert>}
      <form onSubmit={(e) => void submit(e)}>
        <div className="field">
          <label htmlFor="w-item">Item (material required)</label>
          <select id="w-item" className="input" defaultValue="" {...register('itemId')}>
            <option value="" disabled>Select an item…</option>
            {(items.data ?? []).filter((i) => i.active).map((item) => (
              <option key={item.id} value={item.id}>{item.sku} — {item.name}</option>
            ))}
          </select>
          {errors.itemId && <span className="field-error">{errors.itemId.message}</span>}
        </div>
        <div className="field">
          <label htmlFor="w-loc">Location (availability checked here)</label>
          <select id="w-loc" className="input" defaultValue="" {...register('locationId')}>
            <option value="" disabled>Select a location…</option>
            {(locations.data ?? []).filter((l) => l.active).map((loc) => (
              <option key={loc.id} value={loc.id}>{loc.code} — {loc.name}</option>
            ))}
          </select>
          {errors.locationId && <span className="field-error">{errors.locationId.message}</span>}
        </div>
        <div className="form-grid">
          <div className="field">
            <label htmlFor="w-user">Assign to</label>
            <select id="w-user" className="input" defaultValue="" {...register('assignedUserId')}>
              <option value="" disabled>Select…</option>
              {opsUsers.map((user) => (
                <option key={user.id} value={user.id}>{user.fullName}</option>
              ))}
            </select>
            {errors.assignedUserId && <span className="field-error">{errors.assignedUserId.message}</span>}
          </div>
          <div className="field">
            <label htmlFor="w-qty">Required quantity</label>
            <input id="w-qty" className="input" type="number" step="0.001" min="0" {...register('requiredQuantity')} />
            {errors.requiredQuantity && <span className="field-error">{errors.requiredQuantity.message}</span>}
          </div>
        </div>
        <div className="form-row-actions">
          <button type="button" className="btn btn-secondary" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn" disabled={mutation.isPending}>
            {mutation.isPending ? 'Creating…' : 'Create'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
