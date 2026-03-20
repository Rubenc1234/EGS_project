import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import {
  Box,
  Button,
  CircularProgress,
  Container,
  Divider,
  Paper,
  Typography,
} from '@mui/material'
import LockOutlinedIcon from '@mui/icons-material/LockOutlined'
import { fetchLoginUrl, getToken } from '../api'

export default function LoginPage() {
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()

  useEffect(() => {
    if (getToken()) {
      const qs = searchParams.toString()
      navigate(`/pay${qs ? `?${qs}` : ''}`, { replace: true })
    }
  }, [navigate, searchParams])

  const handleLogin = async () => {
    setLoading(true)
    try {
      const qs = searchParams.toString()
      const callbackUrl = `${window.location.origin}/callback${qs ? `?${qs}` : ''}`
      const loginUrl = await fetchLoginUrl(callbackUrl)
      window.location.href = loginUrl
    } catch {
      setLoading(false)
    }
  }

  return (
    <Box
      sx={{
        minHeight: '100vh',
        background: 'linear-gradient(135deg, #1a237e 0%, #283593 50%, #3949ab 100%)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <Container maxWidth="xs">
        <Paper
          elevation={12}
          sx={{ borderRadius: 3, overflow: 'hidden' }}
        >
          {/* Header strip */}
          <Box
            sx={{
              background: 'linear-gradient(135deg, #1565c0, #1976d2)',
              py: 4,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              gap: 1.5,
            }}
          >
            <Box
              sx={{
                bgcolor: 'rgba(255,255,255,0.15)',
                borderRadius: '50%',
                width: 56,
                height: 56,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <LockOutlinedIcon sx={{ color: 'white', fontSize: 28 }} />
            </Box>
            <Typography variant="h5" fontWeight={700} color="white">
              Payments
            </Typography>
            <Typography variant="body2" sx={{ color: 'rgba(255,255,255,0.75)' }}>
              Secure wallet top-up
            </Typography>
          </Box>

          {/* Body */}
          <Box sx={{ p: 4, display: 'flex', flexDirection: 'column', gap: 3 }}>
            <Typography variant="body2" color="text.secondary" textAlign="center">
              Sign in with your account to complete the payment securely.
            </Typography>

            <Button
              variant="contained"
              size="large"
              fullWidth
              onClick={handleLogin}
              disabled={loading}
              sx={{ py: 1.5, borderRadius: 2, fontWeight: 600 }}
            >
              {loading ? <CircularProgress size={22} color="inherit" /> : 'Sign in'}
            </Button>

            <Divider />

            <Typography variant="caption" color="text.disabled" textAlign="center">
              Your payment is secured with 256-bit TLS encryption.
            </Typography>
          </Box>
        </Paper>
      </Container>
    </Box>
  )
}
