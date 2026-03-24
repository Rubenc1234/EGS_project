import React from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import Home from './pages/Home'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import Callback from './pages/Callback'
import Header from './components/Header'
import Container from '@mui/material/Container'

export const isTokenExpired = (token: string) => {
  try {
    const base64Url = token.split('.')[1]
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
    const jsonPayload = decodeURIComponent(atob(base64).split('').map(function(c) {
        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)
    }).join(''))

    const payload = JSON.parse(jsonPayload)
    const now = Math.floor(Date.now() / 1000)
    
    if (payload.exp && payload.exp < now) {
      return true
    }
    return false
  } catch (e) {
    return true
  }
}

const ProtectedRoute = ({ children }: { children: React.ReactNode }) => {
  const token = localStorage.getItem('egs_token')
  
  if (!token || isTokenExpired(token)) {
    localStorage.removeItem('egs_token')
    return <Navigate to="/login" replace />
  }
  
  return <>{children}</>
}

export default function App() {
  return (
    <div>
      <Header />
      <Container maxWidth="lg" sx={{ mt: 3 }}>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/login" element={<Login />} />
          <Route path="/callback" element={<Callback />} />
          <Route path="/dashboard" element={
            <ProtectedRoute>
              <Dashboard />
            </ProtectedRoute>
          } />
        </Routes>
      </Container>
    </div>
  )
}
