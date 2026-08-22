import type { ReactNode } from 'react'

export function Card({ title, children, actions }: { title?: string; children: ReactNode; actions?: ReactNode }) {
  return (
    <section className="card">
      {(title || actions) && (
        <header className="card-header">
          {title && <h2>{title}</h2>}
          {actions && <div className="card-actions">{actions}</div>}
        </header>
      )}
      {children}
    </section>
  )
}

export function Spinner({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="spinner-row" role="status">
      <span className="spinner" aria-hidden />
      <span>{label}</span>
    </div>
  )
}

export function ErrorState({ message }: { message: string | undefined }) {
  return (
    <div className="error-state" role="alert">
      <strong>Something went wrong.</strong>
      <p>{message ?? 'Unknown error'}</p>
    </div>
  )
}

export function EmptyState({ text }: { text: string }) {
  return <div className="empty-state">{text}</div>
}

export type AlertKind = 'success' | 'error' | 'info'

const ALERT_ICONS: Record<AlertKind, string> = { success: '✓', error: '⚠', info: 'ℹ' }

export function Alert({ kind, children }: { kind: AlertKind; children: ReactNode }) {
  return (
    <div className={`alert alert-${kind}`}>
      <span aria-hidden>{ALERT_ICONS[kind]}</span> {children}
    </div>
  )
}

export function StatusPill({ value }: { value: string }) {
  return <span className={`pill pill-${value.toLowerCase()}`}>{value.replace(/_/g, ' ')}</span>
}
