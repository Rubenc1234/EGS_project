import React from 'react'
import { Routes, Route } from 'react-router-dom'
import Home from './pages/Home'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import Callback from './pages/Callback'
import Header from './components/Header'
import Container from '@mui/material/Container'

export default function App() {
  return (
    <div>
      <Header />
      <Container maxWidth="lg" sx={{ mt: 3 }}>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/login" element={<Login />} />
          <Route path="/callback" element={<Callback />} />
          <Route path="/dashboard" element={<Dashboard />} />
        </Routes>
      </Container>
    </div>
  )
}
