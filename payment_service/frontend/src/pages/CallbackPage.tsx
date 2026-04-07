import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Box, CircularProgress, Container, Typography } from '@mui/material'
import { exchangeCode, setToken } from '../api'

export default function CallbackPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const done = useRef(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (done.current) return
    done.current = true

    const code = searchParams.get('code')
    if (!code) {
      navigate('/', { replace: true })
      return
    }

    // Use a fixed callback URL that matches the authorization request
    const redirectUri = `${window.location.origin}/callback`

    exchangeCode(code, redirectUri)
      .then((token) => {
        setToken(token)
        
        // Retrieve stored payment parameters
        const walletId = localStorage.getItem('payment_wallet_id')
        const amount = localStorage.getItem('payment_amount')
        const redirectUrl = localStorage.getItem('payment_redirect_url')
        
        // Clear them after retrieval
        localStorage.removeItem('payment_wallet_id')
        localStorage.removeItem('payment_amount')
        localStorage.removeItem('payment_redirect_url')
        
        // Build query string for /pay
        const payParams = new URLSearchParams()
        if (walletId) payParams.set('wallet_id', walletId)
        if (amount) payParams.set('amount', amount)
        if (redirectUrl) payParams.set('redirect_url', redirectUrl)
        
        const qs = payParams.toString()
        navigate(`/pay${qs ? `?${qs}` : ''}`, { replace: true })
      })
      .catch((err) => {
        const detail = err?.response?.data?.detail ?? err?.response?.data?.error ?? err?.message ?? 'Unknown error'
        setError(`Token exchange failed: ${detail}`)
      })
  }, [navigate, searchParams])

  if (error) {
    return (
      <Container maxWidth="sm">
        <Box sx={{ mt: 12, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2 }}>
          <Typography color="error" fontWeight={600}>Login failed</Typography>
          <Typography variant="body2" color="text.secondary" textAlign="center">{error}</Typography>
          <Typography
            variant="body2"
            sx={{ cursor: 'pointer', textDecoration: 'underline' }}
            onClick={() => navigate('/', { replace: true })}
          >
            Try again
          </Typography>
        </Box>
      </Container>
    )
  }

  return (
    <Container maxWidth="sm">
      <Box sx={{ mt: 12, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2 }}>
        <CircularProgress />
        <Typography>Completing sign in…</Typography>
      </Box>
    </Container>
  )
}
