import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import {
  createReceipt,
  extractApiError,
  fetchInventory,
  fetchItems,
  fetchLocations,
  fetchTransactions,
} from '../api/endpoints'
import { useAuth } from '../lib/AuthContext'
import { Alert, Card, EmptyState, ErrorState, Spinner } from '../components/ui'
import { Modal } from '../components/Modal'

const receiptSchema = z.object({
  itemId: z.coerce.number().int().positive('Select an item'),
  locationId: z.coerce.number().int().positive('Select a location'),
  quantity: z.coerce.number().positive('Quantity must be greater than 0'),
  batchCode: z.string().max(40, 'Max 40 characters').optional(),
  notes: z.string().max(255, 'Max 255 characters').optional(),
})

type ReceiptForm = z.infer<typeof receiptSchema>

export function InventoryPage() {
  const { hasAnyRole } = useAuth()
  const canManageStock = hasAnyRole('ADMIN', 'OPERATIONS_USER')
  const [itemFilter, setItemFilter] = useState('')
  const [locationFilter, setLocationFilter] = useState('')
  const [showLedger, setShowLedger] = useState(false)
  const [receiptOpen, setReceiptOpen] = useState(false)

  const items = useQuery({ queryKey: ['items'], queryFn: fetchItems })
  const locations = useQuery({ queryKey: ['locations'], queryFn: fetchLocations })
  const inventory = useQuery({
    queryKey: ['inventory', itemFilter, locationFilter],
    queryFn: () =>
      fetchInventory({
        itemId: itemFilter ? Number(itemFilter) : undefined,
        locationId: locationFilter ? Number(locationFilter) : undefined,
      }),
  })

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Inventory</h1>
          <p className="subtitle">
            Available = physical − reserved. Reservations are enforced server-side and race-safe.
          </p>
        </div>
        {canManageStock && (
          <button type="button" className="btn" onClick={() => setReceiptOpen(true)}>
            Goods receipt
          </button>
        )}
      </div>

      <Card>
        <div className="toolbar">
          <select className="input" value={itemFilter} onChange={(e) => setItemFilter(e.target.value)} aria-label="Filter by item">
            <option value="">All items</option>
            {(items.data ?? []).map((item) => (
              <option key={item.id} value={item.id}>{item.sku} — {item.name}</option>
            ))}
          </select>
          <select className="input" value={locationFilter} onChange={(e) => setLocationFilter(e.target.value)} aria-label="Filter by location">
            <option value="">All locations</option>
            {(locations.data ?? []).map((loc) => (
              <option key={loc.id} value={loc.id}>{loc.code}</option>
            ))}
          </select>
          <button type="button" className="btn btn-secondary" onClick={() => setShowLedger((v) => !v)}>
            {showLedger ? 'Hide audit ledger' : 'Show audit ledger'}
          </button>
        </div>

        {inventory.isLoading && <Spinner label="Loading stock…" />}
        {inventory.isError && <ErrorState message={extractApiError(inventory.error).message} />}
        {inventory.data &&
          (inventory.data.content.length === 0 ? (
            <EmptyState text="No stock recorded yet — start with a goods receipt." />
          ) : (
            <table className="data-table">
              <thead>
                <tr>
                  <th>SKU</th>
                  <th>Item</th>
                  <th>Location</th>
                  <th className="num">Physical</th>
                  <th className="num">Reserved</th>
                  <th className="num">Available</th>
                </tr>
              </thead>
              <tbody>
                {inventory.data.content.map((row) => (
                  <tr key={row.id}>
                    <td>{row.itemSku}</td>
                    <td>{row.itemName}</td>
                    <td>{row.locationCode}</td>
                    <td className="num">{row.physicalQty}</td>
                    <td className="num">{row.reservedQty}</td>
                    <td className="num"><strong>{row.availableQty}</strong></td>
                  </tr>
                ))}
              </tbody>
            </table>
          ))}
      </Card>

      {showLedger && <AuditLedger />}

      {canManageStock && (
        <ReceiptModal
          open={receiptOpen}
          onClose={() => setReceiptOpen(false)}
        />
      )}
    </>
  )
}

function AuditLedger() {
  const transactions = useQuery({ queryKey: ['transactions'], queryFn: () => fetchTransactions() })

  return (
    <Card title="Inventory transaction ledger (audit trail)">
      {transactions.isLoading && <Spinner />}
      {transactions.isError && <ErrorState message={extractApiError(transactions.error).message} />}
      {transactions.data &&
        (transactions.data.content.length === 0 ? (
          <EmptyState text="No stock movements yet." />
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Time</th>
                <th>Type</th>
                <th>Item</th>
                <th>Location</th>
                <th className="num">Δ Physical</th>
                <th className="num">Δ Reserved</th>
                <th>Reference</th>
                <th>By</th>
              </tr>
            </thead>
            <tbody>
              {transactions.data.content.map((tx) => (
                <tr key={tx.id}>
                  <td>{new Date(tx.createdAt).toLocaleString()}</td>
                  <td>{tx.txType.replace(/_/g, ' ')}</td>
                  <td>{tx.itemSku}</td>
                  <td>{tx.locationCode}</td>
                  <td className="num">{tx.quantityDeltaPhysical}</td>
                  <td className="num">{tx.quantityDeltaReserved}</td>
                  <td>{tx.referenceType}{tx.referenceId != null ? ` #${tx.referenceId}` : ''}</td>
                  <td>{tx.performedByUsername}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ))}
    </Card>
  )
}

function ReceiptModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient()
  const items = useQuery({ queryKey: ['items'], queryFn: fetchItems })
  const locations = useQuery({ queryKey: ['locations'], queryFn: fetchLocations })

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<z.input<typeof receiptSchema>, unknown, ReceiptForm>({ resolver: zodResolver(receiptSchema) })

  const mutation = useMutation({
    mutationFn: createReceipt,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['inventory'] })
      void queryClient.invalidateQueries({ queryKey: ['transactions'] })
      reset()
      onClose()
    },
  })

  const submit = handleSubmit((values) =>
    mutation.mutate({
      ...values,
      idempotencyKey: crypto.randomUUID().slice(0, 36),
    }),
  )

  return (
    <Modal title="Goods receipt" open={open} onClose={onClose}>
      {mutation.isError && (
        <Alert kind="error">
          {formatMutationError(mutation.error)}
        </Alert>
      )}
      <form onSubmit={(e) => void submit(e)}>
        <div className="field">
          <label htmlFor="r-item">Item</label>
          <select id="r-item" className="input" defaultValue="" {...register('itemId')}>
            <option value="" disabled>Select an item…</option>
            {(items.data ?? []).filter((i) => i.active).map((item) => (
              <option key={item.id} value={item.id}>{item.sku} — {item.name}</option>
            ))}
          </select>
          {errors.itemId && <span className="field-error">{errors.itemId.message}</span>}
        </div>
        <div className="field">
          <label htmlFor="r-loc">Location</label>
          <select id="r-loc" className="input" defaultValue="" {...register('locationId')}>
            <option value="" disabled>Select a location…</option>
            {(locations.data ?? []).filter((l) => l.active).map((loc) => (
              <option key={loc.id} value={loc.id}>{loc.code} — {loc.name}</option>
            ))}
          </select>
          {errors.locationId && <span className="field-error">{errors.locationId.message}</span>}
        </div>
        <div className="form-grid">
          <div className="field">
            <label htmlFor="r-qty">Quantity</label>
            <input id="r-qty" className="input" type="number" step="0.001" min="0" {...register('quantity')} />
            {errors.quantity && <span className="field-error">{errors.quantity.message}</span>}
          </div>
          <div className="field">
            <label htmlFor="r-batch">Batch code (optional)</label>
            <input id="r-batch" className="input" {...register('batchCode')} />
            {errors.batchCode && <span className="field-error">{errors.batchCode.message}</span>}
          </div>
        </div>
        <div className="field">
          <label htmlFor="r-notes">Notes (optional)</label>
          <input id="r-notes" className="input" {...register('notes')} />
          {errors.notes && <span className="field-error">{errors.notes.message}</span>}
        </div>
        <div className="form-row-actions">
          <button type="button" className="btn btn-secondary" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn" disabled={mutation.isPending}>
            {mutation.isPending ? 'Saving…' : 'Receive stock'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

/** Renders backend validation details when present, otherwise the plain message. */
export function formatMutationError(err: unknown): string {
  const apiError = extractApiError(err)
  if (apiError.details && apiError.details.length > 0) {
    return apiError.details.map((d) => `${d.field}: ${d.message}`).join(' · ')
  }
  return apiError.message ?? 'Request failed'
}
