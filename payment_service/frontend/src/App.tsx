import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import AppLayout from './components/AppLayout'
import LoginPage from './pages/LoginPage'
import CallbackPage from './pages/CallbackPage'
import PaymentPage from './pages/PaymentPage'
import SuccessPage from './pages/SuccessPage'
import PaymentsPage from './pages/PaymentsPage'
import PaymentDetailPage from './pages/PaymentDetailPage'
import ProfilePage from './pages/ProfilePage'
import StatsPage from './pages/StatsPage'
import HomePage from './pages/HomePage'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<LoginPage />} />
        <Route path="/callback" element={<CallbackPage />} />
        <Route path="/pay" element={<PaymentPage />} />
        <Route path="/success" element={<SuccessPage />} />
        <Route element={<AppLayout />}>
          <Route path="/home" element={<HomePage />} />
          <Route path="/payments" element={<PaymentsPage />} />
          <Route path="/payments/:id" element={<PaymentDetailPage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/stats" element={<StatsPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
