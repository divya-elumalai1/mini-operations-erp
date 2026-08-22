import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../lib/AuthContext'

const NAV_ITEMS = [
  { to: '/inventory', label: 'Inventory', roles: ['ADMIN', 'OPERATIONS_USER', 'SALES_USER'] },
  { to: '/work-orders', label: 'Work Orders', roles: ['ADMIN', 'OPERATIONS_USER', 'SALES_USER'] },
  { to: '/transfers', label: 'Internal Transfers', roles: ['ADMIN', 'OPERATIONS_USER', 'SALES_USER'] },
  { to: '/orders', label: 'Customer Orders', roles: ['ADMIN', 'SALES_USER', 'OPERATIONS_USER'] },
] as const

export function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  if (!user) return null

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <span className="brand">Mini Operations ERP</span>
        <nav className="nav-links" aria-label="Main navigation">
          {NAV_ITEMS.map((item) =>
            item.roles.includes(user.role) ? (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
              >
                {item.label}
              </NavLink>
            ) : null,
          )}
        </nav>
        <div className="topbar-user">
          <span className="role-chip">{user.role.replace(/_/g, ' ')}</span>
          <span>{user.username}</span>
          <button type="button" className="btn btn-secondary btn-sm" onClick={handleLogout}>
            Log out
          </button>
        </div>
      </header>
      <main className="page">
        <Outlet />
      </main>
    </div>
  )
}
