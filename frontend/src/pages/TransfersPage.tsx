import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import {
  createTransfer,
  dispatchTransfer,
  extractApiError,
  fetchItems,
  fetchLocations,
  fetchTransfers,
  receiveTransfer,
} from '../api/endpoints'
import type { Transfer } from '../types/api'
import { useAuth } from '../lib/AuthContext'
import { Card, EmptyState, ErrorState, Spinner, StatusPill } from '../components/ui'
import { Modal } from '../components/Modal'
import { formatMutationError } from './InventoryPage'

const transferSchema = z
  .object({
    sourceLocationId: z.coerce.number().int().positive('Select a source location'),
    destinationLocationId: z.coerce.number().int().positive('Select a destination location'),
    itemId: z.coerce.number().int().positive('Select an item'),
    quantity: z.coerce.number().positive('Quantity must be greater than 0'),
  })
  .refine((values) => values.sourceLocationId !== values.destinationLocationId, {
    message: 'Source and destination must differ',
    path: ['destinationLocationId'],
  })

type TransferForm = z.infer<typeof transferSchema>

const STATUS_FILTERS = ['', 'REQUESTED', 'DISPATCHED', 'RECEIVED'] as const

export function TransfersPage() {
  const { hasAnyRole } = useAuth()
  const canOperate = hasAnyRole('ADMIN', 'OPERATIONS_USER')
  const [statusFilter, setStatusFilter] = useState<string>('')
  const [createOpen, setCreateOpen] = useState(false)

  const transfers = useQuery({
    queryKey: ['transfers', statusFilter],
    queryFn: () => fetchTransfers(statusFilter || undefined),
  })

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Internal Transfers</h1>
          <p className="subtitle">
            Dispatch moves stock out of the source. Destination stock increases only on receipt — once.
          </p>
        </div>
        {canOperate && (
          <button type="button" className="btn" onClick={() => setCreateOpen(true)}>
            New transfer
          </button>
        )}
      </div>

      <Card>
        <div className="toolbar">
          <select className="input" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} aria-label="Filter by status">
            {STATUS_FILTERS.map((s) => (
              <option key={s} value={s}>{s === '' ? 'All statuses' : s}</option>
            ))}
          </select>
        </div>

        {transfers.isLoading && <Spinner label="Loading transfers…" />}
        {transfers.isError && <ErrorState message={extractApiError(transfers.error).message} />}
        {transfers.data &&
          (transfers.data.content.length === 0 ? (
            <EmptyState text="No transfers found." />
          ) : (
            <table className="data-table">
              <thead>
                <tr>
                  <th>Number</th>
                  <th>Item</th>
                  <th className="num">Qty</th>
                  <th>From → To</th>
                  <th>Status</th>
                  <th style={{ textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {transfers.data.content.map((transfer) => (
                  <TransferRow key={transfer.id} transfer={transfer} canOperate={canOperate} />
                ))}
              </tbody>
            </table>
          ))}
      </Card>

      {canOperate && <CreateTransferModal open={createOpen} onClose={() => setCreateOpen(false)} />}
    </>
  )
}

function TransferRow({ transfer, canOperate }: { transfer: Transfer; canOperate: boolean }) {
  const queryClient = useQueryClient()
  const [actionError, setActionError] = useState<string | null>(null)

  const dispatch = useMutation({
    mutationFn: () => dispatchTransfer(transfer.id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['transfers'] }),
    onError: (err) => setActionError(formatMutationError(err)),
  })
  const receive = useMutation({
    mutationFn: () => receiveTransfer(transfer.id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['transfers'] })
      void queryClient.invalidateQueries({ queryKey: ['inventory'] })
      void queryClient.invalidateQueries({ queryKey: ['transactions'] })
    },
    onError: (err) => setActionError(formatMutationError(err)),
  })

  const busy = dispatch.isPending || receive.isPending

  return (
    <>
      <tr>
        <td>{transfer.transferNumber}</td>
        <td>{transfer.itemSku}</td>
        <td className="num">{transfer.quantity}</td>
        <td>{transfer.sourceLocationCode} → {transfer.destinationLocationCode}</td>
        <td><StatusPill value={transfer.status} /></td>
        <td className="num">
          {canOperate && (
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              {transfer.status === 'REQUESTED' && (
                <button type="button" className="btn btn-sm" disabled={busy} onClick={() => { setActionError(null); dispatch.mutate() }}>
                  Dispatch
                </button>
              )}
              {transfer.status === 'DISPATCHED' && (
                <button type="button" className="btn btn-success btn-sm" disabled={busy} onClick={() => { setActionError(null); receive.mutate() }}>
                  Receive
                </button>
              )}
            </div>
          )}
          {actionError && <span className="field-error">{actionError}</span>}
        </td>
      </tr>
    </>
  )
}

function CreateTransferModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient()
  const items = useQuery({ queryKey: ['items'], queryFn: fetchItems })
  const locations = useQuery({ queryKey: ['locations'], queryFn: fetchLocations })

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
  } = useForm<z.input<typeof transferSchema>, unknown, TransferForm>({ resolver: zodResolver(transferSchema) })

  const mutation = useMutation({
    mutationFn: createTransfer,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['transfers'] })
      reset()
      onClose()
    },
  })

  const submit = handleSubmit((values) =>
    mutation.mutate(values, {
      onError: (err) => {
        const apiError = extractApiError(err)
        if (apiError.error === 'INSUFFICIENT_STOCK') {
          setError('quantity', { message: apiError.message ?? 'Insufficient stock at source' })
        }
      },
    }),
  )

  return (
    <Modal title="New internal transfer" open={open} onClose={onClose}>
      {mutation.isError && !errors.quantity && (
        <span className="field-error">{formatMutationError(mutation.error)}</span>
      )}
      <form onSubmit={(e) => void submit(e)}>
        <div className="field">
          <label htmlFor="t-item">Item</label>
          <select id="t-item" className="input" defaultValue="" {...register('itemId')}>
            <option value="" disabled>Select an item…</option>
            {(items.data ?? []).filter((i) => i.active).map((item) => (
              <option key={item.id} value={item.id}>{item.sku} — {item.name}</option>
            ))}
          </select>
          {errors.itemId && <span className="field-error">{errors.itemId.message}</span>}
        </div>
        <div className="form-grid">
          <div className="field">
            <label htmlFor="t-src">From</label>
            <select id="t-src" className="input" defaultValue="" {...register('sourceLocationId')}>
              <option value="" disabled>Source…</option>
              {(locations.data ?? []).filter((l) => l.active).map((loc) => (
                <option key={loc.id} value={loc.id}>{loc.code}</option>
              ))}
            </select>
            {errors.sourceLocationId && <span className="field-error">{errors.sourceLocationId.message}</span>}
          </div>
          <div className="field">
            <label htmlFor="t-dst">To</label>
            <select id="t-dst" className="input" defaultValue="" {...register('destinationLocationId')}>
              <option value="" disabled>Destination…</option>
              {(locations.data ?? []).filter((l) => l.active).map((loc) => (
                <option key={loc.id} value={loc.id}>{loc.code}</option>
              ))}
            </select>
            {errors.destinationLocationId && <span className="field-error">{errors.destinationLocationId.message}</span>}
          </div>
        </div>
        <div className="field">
          <label htmlFor="t-qty">Quantity</label>
          <input id="t-qty" className="input" type="number" step="0.001" min="0" {...register('quantity')} />
          {errors.quantity && <span className="field-error">{errors.quantity.message}</span>}
        </div>
        <div className="form-row-actions">
          <button type="button" className="btn btn-secondary" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn" disabled={mutation.isPending}>
            {mutation.isPending ? 'Creating…' : 'Create transfer'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
