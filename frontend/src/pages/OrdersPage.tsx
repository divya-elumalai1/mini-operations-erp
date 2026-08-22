import { useState } from 'react'
import { useFieldArray, useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createOrder,
  extractApiError,
  fetchItems,
  fetchLocations,
  fetchOrders,
} from '../api/endpoints'
import type { OrderInput } from '../api/endpoints'
import { useAuth } from '../lib/AuthContext'
import { Alert, Card, EmptyState, ErrorState, Spinner, StatusPill } from '../components/ui'
import { Modal } from '../components/Modal'
import { formatMutationError } from './InventoryPage'

const orderSchema = z.object({
  customerName: z.string().min(1, 'Customer name is required').max(120),
  lines: z
    .array(
      z.object({
        itemId: z.coerce.number().int().positive('Select an item'),
        locationId: z.coerce.number().int().positive('Select a location'),
        quantity: z.coerce.number().positive('Quantity must be greater than 0'),
      }),
    )
    .min(1, 'Add at least one order line'),
})

type OrderForm = z.infer<typeof orderSchema>

export function OrdersPage() {
  const { hasAnyRole } = useAuth()
  const canOrder = hasAnyRole('SALES_USER', 'ADMIN')
  const [createOpen, setCreateOpen] = useState(false)
  const [lastCreated, setLastCreated] = useState<string | null>(null)

  const orders = useQuery({ queryKey: ['orders'], queryFn: fetchOrders })

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Customer Orders</h1>
          <p className="subtitle">
            Creating an order reserves stock atomically — if any line lacks availability the whole
            order is rejected.
          </p>
        </div>
        {canOrder && (
          <button type="button" className="btn" onClick={() => setCreateOpen(true)}>
            New customer order
          </button>
        )}
      </div>

      {lastCreated && (
        <Alert kind="success">
          Order <strong>{lastCreated}</strong> created and stock reserved successfully.
        </Alert>
      )}

      <Card>
        {orders.isLoading && <Spinner label="Loading orders…" />}
        {orders.isError && <ErrorState message={extractApiError(orders.error).message} />}
        {orders.data &&
          (orders.data.content.length === 0 ? (
            <EmptyState text="No customer orders yet." />
          ) : (
            <table className="data-table">
              <thead>
                <tr>
                  <th>Number</th>
                  <th>Customer</th>
                  <th>Lines</th>
                  <th>Status</th>
                  <th>Created by</th>
                  <th>Date</th>
                </tr>
              </thead>
              <tbody>
                {orders.data.content.map((order) => (
                  <tr key={order.id}>
                    <td>{order.orderNumber}</td>
                    <td>{order.customerName}</td>
                    <td>
                      {order.lines.map((line) => (
                        <div key={line.id}>
                          {line.quantity} × {line.itemSku} @ {line.locationCode}
                        </div>
                      ))}
                    </td>
                    <td><StatusPill value={order.status} /></td>
                    <td>{order.createdByUsername}</td>
                    <td>{new Date(order.createdAt).toLocaleDateString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ))}
      </Card>

      {canOrder && (
        <CreateOrderModal
          open={createOpen}
          onClose={() => setCreateOpen(false)}
          onCreated={(number) => setLastCreated(number)}
        />
      )}
    </>
  )
}

function CreateOrderModal({
  open,
  onClose,
  onCreated,
}: {
  open: boolean
  onClose: () => void
  onCreated: (orderNumber: string) => void
}) {
  const queryClient = useQueryClient()
  const items = useQuery({ queryKey: ['items'], queryFn: fetchItems })
  const locations = useQuery({ queryKey: ['locations'], queryFn: fetchLocations })

  const {
    register,
    handleSubmit,
    control,
    reset,
    formState: { errors },
  } = useForm<z.input<typeof orderSchema>, unknown, OrderForm>({
    resolver: zodResolver(orderSchema),
    defaultValues: { customerName: '', lines: [{ itemId: undefined, locationId: undefined, quantity: undefined }] },
  })

  const { fields, append, remove } = useFieldArray({ control, name: 'lines' })
  const linesValue = useWatch({ control, name: 'lines' }) ?? []
  const totalQuantity = linesValue.reduce((sum, line) => sum + (Number(line?.quantity) || 0), 0)

  const mutation = useMutation({
    mutationFn: (input: OrderInput) => createOrder(input),
    onSuccess: (order) => {
      void queryClient.invalidateQueries({ queryKey: ['orders'] })
      void queryClient.invalidateQueries({ queryKey: ['inventory'] })
      void queryClient.invalidateQueries({ queryKey: ['transactions'] })
      onCreated(order.orderNumber)
      reset()
      onClose()
    },
  })

  const submit = handleSubmit((values) => mutation.mutate(values))

  return (
    <Modal title="New customer order" open={open} onClose={onClose}>
      {mutation.isError && (
        <Alert kind="error">
          {formatMutationError(mutation.error)}
          {extractApiError(mutation.error).error === 'INSUFFICIENT_STOCK' && (
            <div>No reservation was made — nothing was partially saved.</div>
          )}
        </Alert>
      )}
      <form onSubmit={(e) => void submit(e)}>
        <div className="field">
          <label htmlFor="o-customer">Customer name</label>
          <input id="o-customer" className="input" {...register('customerName')} />
          {errors.customerName && <span className="field-error">{errors.customerName.message}</span>}
        </div>

        {fields.map((field, index) => (
          <fieldset key={field.id} style={{ border: '1px solid var(--border)', borderRadius: 8, marginBottom: 12, padding: 12 }}>
            <legend style={{ fontSize: 13, fontWeight: 600 }}>Line {index + 1}</legend>
            <div className="field">
              <label htmlFor={`o-item-${index}`}>Item</label>
              <select id={`o-item-${index}`} className="input" defaultValue="" {...register(`lines.${index}.itemId`)}>
                <option value="" disabled>Select…</option>
                {(items.data ?? []).filter((i) => i.active).map((item) => (
                  <option key={item.id} value={item.id}>{item.sku}</option>
                ))}
              </select>
              {errors.lines?.[index]?.itemId && (
                <span className="field-error">{errors.lines[index]?.itemId?.message}</span>
              )}
            </div>
            <div className="form-grid">
              <div className="field">
                <label htmlFor={`o-loc-${index}`}>Ship from</label>
                <select id={`o-loc-${index}`} className="input" defaultValue="" {...register(`lines.${index}.locationId`)}>
                  <option value="" disabled>Location…</option>
                  {(locations.data ?? []).filter((l) => l.active).map((loc) => (
                    <option key={loc.id} value={loc.id}>{loc.code}</option>
                  ))}
                </select>
                {errors.lines?.[index]?.locationId && (
                  <span className="field-error">{errors.lines[index]?.locationId?.message}</span>
                )}
              </div>
              <div className="field">
                <label htmlFor={`o-qty-${index}`}>Quantity</label>
                <input id={`o-qty-${index}`} className="input" type="number" step="0.001" min="0" {...register(`lines.${index}.quantity`)} />
                {errors.lines?.[index]?.quantity && (
                  <span className="field-error">{errors.lines[index]?.quantity?.message}</span>
                )}
              </div>
            </div>
            {fields.length > 1 && (
              <button type="button" className="btn btn-danger btn-sm" onClick={() => remove(index)}>
                Remove line
              </button>
            )}
          </fieldset>
        ))}

        <button
          type="button"
          className="btn btn-secondary btn-sm"
          onClick={() => append({ itemId: undefined, locationId: undefined, quantity: undefined })}
        >
          + Add line
        </button>
        {typeof errors.lines?.message === 'string' && (
          <span className="field-error" style={{ marginLeft: 10 }}>{errors.lines.message}</span>
        )}

        <p style={{ color: 'var(--muted)', margin: '12px 0 0' }}>
          Total units to reserve: <strong>{totalQuantity || 0}</strong>
        </p>

        <div className="form-row-actions">
          <button type="button" className="btn btn-secondary" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn" disabled={mutation.isPending}>
            {mutation.isPending ? 'Reserving stock…' : 'Create & reserve'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
