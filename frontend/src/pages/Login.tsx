import React, { useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'

export default function Login() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  
  useEffect(() => {
    const token = localStorage.getItem('egs_token')
    const force = searchParams.get('force') === 'true'

    if (token && !force) {
      console.log('User already has a token, redirecting to dashboard')
      navigate('/dashboard')
      return
    }

    if (force) {
      localStorage.removeItem('egs_token')
    }

    // Redirect the browser to transactions_service which will forward to Keycloak.
    const redirect_uri = window.location.origin + '/callback'
    const txBase = import.meta.env.VITE_TRANSACTIONS_BASE_URL || 'http://transactions.pt'
    const url = `${txBase}/v1/login?redirect_uri=${encodeURIComponent(redirect_uri)}`
    console.log('Redirecting browser to transactions_service login endpoint:', url)
    window.location.href = url
  }, [navigate, searchParams])

  return (
    <div style={{ textAlign: 'center', marginTop: '50px' }}>
      <p>Redirecting to Keycloak...</p>
    </div>
  )
}
