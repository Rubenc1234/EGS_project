import React, { useEffect } from 'react'

export default function Login() {
  useEffect(() => {
    // Limpa token existente para forçar novo login
    localStorage.removeItem('egs_token')

    // Redirect the browser to transactions_service which will forward to Keycloak.
    // This avoids CORS since the browser will follow the 302 from transactions_service.
    const redirect_uri = window.location.origin + '/callback'
    const txBase = 'http://localhost:8081'
    const url = `${txBase}/v1/login?redirect_uri=${encodeURIComponent(redirect_uri)}`
    console.log('Redirecting browser to transactions_service login endpoint:', url)
    window.location.href = url
  }, [])

  return (
    <div style={{ textAlign: 'center', marginTop: '50px' }}>
      <p>Redirecting to Keycloak...</p>
    </div>
  )
}

