import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './lib/AuthContext'
import { AppLayout } from './layouts/AppLayout'
import { LoginPage } from './pages/LoginPage'
import { InventoryPage } from './pages/InventoryPage'
import { WorkOrdersPage } from './pages/WorkOrdersPage'
import { TransfersPage } from './pages/TransfersPage'
import { OrdersPage } from './pages/OrdersPage'

function RequireAuth({ children }: { children: React.ReactElement }) {
  const { user } = useAuth()
  return user ? children : <Navigate to="/login" replace />
}

export default function App() {
  const { user } = useAuth()

  return (
    <Routes>
      <Route
        path="/login"
        element={user ? <Navigate to="/inventory" replace /> : <LoginPage />}
      />
      <Route element={<RequireAuth><AppLayout /></RequireAuth>}>
        <Route path="/inventory" element={<InventoryPage />} />
        <Route path="/work-orders" element={<WorkOrdersPage />} />
        <Route path="/transfers" element={<TransfersPage />} />
        <Route path="/orders" element={<OrdersPage />} />
      </Route>
      <Route path="*" element={<Navigate to={user ? '/inventory' : '/login'} replace />} />
    </Routes>
  )
}
